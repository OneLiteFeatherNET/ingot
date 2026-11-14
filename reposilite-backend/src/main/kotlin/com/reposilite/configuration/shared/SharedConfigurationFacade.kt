/*
 * Copyright (c) 2023 dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reposilite.configuration.shared

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.github.victools.jsonschema.generator.SchemaGenerator
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.configuration.shared.api.UpdateValidatedSettings
import com.reposilite.journalist.Journalist
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.SharedConfigurationChangedEvent
import com.reposilite.shared.EntityTag
import com.reposilite.shared.EntityTagSigner
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequest
import com.reposilite.shared.toErrorResult
import com.reposilite.status.FailureFacade
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeCodec
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeException
import net.onelitefeather.ingot.settings.encryption.SettingsKeySources
import net.onelitefeather.ingot.settings.encryption.SealedSetting
import net.onelitefeather.ingot.settings.encryption.SettingsSecretCipher
import io.javalin.http.HttpStatus
import io.javalin.http.HttpStatus.PRECONDITION_FAILED
import io.javalin.openapi.JsonSchemaLoader
import panda.std.Result
import panda.std.Result.supplyThrowing
import panda.std.asError
import panda.std.asSuccess
import panda.std.ok
import panda.std.reactive.MutableReference
import java.util.concurrent.atomic.AtomicReference
import java.util.ConcurrentModificationException
import java.util.function.Supplier
import kotlin.reflect.KClass

/**
 * The stored state of a settings domain and the entity tag that identifies exactly that state.
 */
data class SharedSettingsSnapshot<S : SharedSettings>(
    val settings: S,
    val etag: String,
)

/**
 * A write refused because the shared settings file owns the domain, see [SharedConfigurationFacade.isExternallyManaged].
 */
class SharedSettingsManagedException(message: String) : IllegalStateException(message)

private val canonicalWriter = DEFAULT_OBJECT_MAPPER.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

class SharedConfigurationFacade(
    private val journalist: Journalist,
    private val schemaGenerator: Lazy<SchemaGenerator>,
    private val failureFacade: FailureFacade,
    private val sharedSettingsProvider: SharedSettingsProvider,
    private val sharedConfigurationProvider: SharedConfigurationProvider,
    private val extensions: Extensions = Extensions(journalist),
    private val entityTags: EntityTagSigner = EntityTagSigner.ephemeral(),
    private val secretReferences: SecretReferenceResolver? = null,
    /**
     * The keys of this instance. Secret values are stored as envelopes while a key is active, and an envelope is opened when a
     * document is loaded. Null when no encryption is configured at all.
     */
    private val secretCipher: SettingsSecretCipher? = null,
) : Facade {

    private companion object {
        /**
         * Writes that lose a race against another instance are retried against the stored state this many times at most.
         */
        const val MAX_WRITE_ATTEMPTS = 10
    }

    private val configHandlers = mutableMapOf<String, SharedSettingsReference<*>>()

    /**
     * Serializes reads and writes of the settings of this instance, so a precondition is checked against the state it writes.
     */
    private val stateLock = Any()

    private fun SharedSettings.entityTag(): String =
        entityTags.of(canonicalWriter.writeValueAsBytes(this))

    private fun <S : SharedSettings> S.snapshot(): SharedSettingsSnapshot<S> =
        SharedSettingsSnapshot(this, entityTag())

    init {
        val jsonSchemaLoader = JsonSchemaLoader()
        val knownSchemes = jsonSchemaLoader.loadGeneratedSchemes().associateBy { it.name }

        sharedSettingsProvider.domains.forEach { (type, settings) ->
            // Holds the document as it was written when references are on. Consumers read [settings], which then holds the
            // resolved view. Without references the document and the consumer view are the same object, as they always were.
            val written = AtomicReference<SharedSettings?>(null)
            registerSettingsWatcher(
                DefaultSharedSettingsReference(
                    type = type,
                    schema = knownSchemes[type.name]
                        ?.let { Supplier { it.getContent() } }
                        ?: run {
                            journalist.logger.warn("[SharedConfigurationFacade] Cannot find scheme for $type, scheme has to be generated at runtime")
                            val scheme = schemaGenerator.value.generateSchema(type).also { cleanupScheme(it) }.toPrettyString()
                            Supplier { scheme.byteInputStream() }
                        },
                    getter = { written.get() ?: settings.get() },
                    setter = { value ->
                        // Resolved before anything is stored, so a failing reference leaves the previous document and view in place.
                        val effective = resolveEffective(type, value)
                        settings.update(effective)
                        written.set(value.takeIf { secretReferences != null })
                    }
                )
            )
        }
    }

    private fun <T : SharedSettings> registerSettingsWatcher(handler: SharedSettingsReference<T>): SharedSettingsReference<T> =
        handler.also {
            require(it.name !in configHandlers) { "There are already settings with that name! Please report to the plugin author." }
            configHandlers[it.name] = it
        }

    open class SharedSettingsUpdateException(
        val errors: Collection<Pair<SharedSettingsReference<*>, Exception>>
    ) : IllegalStateException("Cannot load shared configuration from file (${errors.size} errors):\n${errors.joinToString(System.lineSeparator())}")

    fun fetchConfiguration(): String =
        sharedConfigurationProvider.fetchConfiguration()

    internal fun loadSharedSettingsFromString(content: String): Result<Unit, SharedSettingsUpdateException> {
        val document = supplyThrowing { DEFAULT_OBJECT_MAPPER.readTree(content) }
        if (document.isErr) {
            reportUnreadableDocument(document.error)
        }

        val updateResult = document
            .map { node -> getDomainNames().asSequence().filter { node.has(it) }.associateWith { node.get(it) } }
            .orElseGet { emptyMap() }
            .map { (name, obj) ->
                val ref = getSettingsReference<SharedSettings>(name)!!
                ref to applyDomain(name, ref, obj)
            }

        updateResult
            .asSequence()
            .filter { (_, result) -> result.isOk }
            .joinToString(separator = ", ") { (ref) -> "'${ref.name}'" }
            .let { journalist.logger.info("Domains $it have been loaded from ${sharedConfigurationProvider.name()}") }

        val failures = updateResult
            .filter { (_, result) -> result.isErr }

        failures.forEach { (ref, result) ->
            journalist.logger.error("Shared configuration | Cannot update '${ref.name}' due to ${result.error}")
            journalist.logger.debug("Shared configuration | Source:")
            journalist.logger.debug(SettingsSecrets.redactDocument(content, domainTypes()))
            failureFacade.throwException("Shared configuration", result.error)
        }

        return failures
            .map { (ref, result) -> ref to result.error }
            .takeIf { it.isNotEmpty() }
            ?.let { SharedSettingsUpdateException(errors = it).asError() }
            ?: ok()
    }

    /**
     * Opens the envelopes of one stored domain and applies it. A value that cannot be opened fails only this domain, so the
     * previous settings of it stay in place.
     */
    private fun applyDomain(name: String, ref: SharedSettingsReference<SharedSettings>, node: JsonNode): Result<SharedSettings, out Exception> {
        val settings = try {
            DEFAULT_OBJECT_MAPPER.readValue(openSecrets(name, ref.type, node).toString(), ref.type)
        } catch (exception: SettingsEnvelopeException) {
            return exception.asError()
        }
        return ref.update(settings)
    }

    /**
     * Opens every envelope of a stored domain, so consumers and the API work on plain values. The message of a failure names the
     * domain and the property, never the value.
     */
    private fun openSecrets(name: String, type: Class<*>, node: JsonNode): JsonNode =
        SettingsSecrets.transformSensitive(type, node) { path, value ->
            if (!SettingsEnvelopeCodec.isEnvelope(value)) {
                return@transformSensitive value
            }
            val cipher = secretCipher
                ?: throw SettingsEnvelopeException("Settings domain '$name' cannot be read: '$path' is encrypted, but no settings key is configured")
            try {
                cipher.open(name, path, value)
            } catch (exception: SettingsEnvelopeException) {
                throw SettingsEnvelopeException("Settings domain '$name' cannot be read: ${exception.message}")
            }
        }

    /**
     * Seals the plain secret values of a rendered domain. Empty values and references stay as written.
     */
    private fun sealSecrets(name: String, type: Class<*>, node: JsonNode): JsonNode {
        val cipher = secretCipher?.takeIf { it.sealing } ?: return node
        return SettingsSecrets.transformSensitive(type, node) { path, value ->
            if (isPlainSecret(value)) cipher.seal(name, path, value) else value
        }
    }

    /**
     * True for a value that is stored in clear: not empty, not a reference and not an envelope.
     */
    private fun isPlainSecret(value: String): Boolean =
        value.isNotEmpty() &&
            !SettingsEnvelopeCodec.isEnvelope(value) &&
            !(secretReferences != null && SecretReferenceResolver.isReferenceOnly(value))

    /**
     * Logs where the parser stopped. The parser message is left out: it can quote the token it rejected, and the document holds
     * credentials. Every domain then falls back to its defaults, as it always did.
     */
    private fun reportUnreadableDocument(error: Exception) {
        val location = (error as? JsonProcessingException)?.location
        val position = location?.let { "line ${it.lineNr}, column ${it.columnNr}" } ?: "an unknown position"
        journalist.logger.error("Shared configuration | Cannot read ${sharedConfigurationProvider.name()} at $position (${error::class.simpleName}), domains fall back to defaults")
    }

    /**
     * Stores the new settings of a domain and records which fields changed. The audit event names the
     * fields only: the values of settings can be secrets, so they never leave this method.
     */
    fun <S : SharedSettings> updateSharedSettings(name: String, body: S, actor: AuditActor = AuditActor.SYSTEM_ACTOR): Result<S, out Exception>? {
        synchronized(stateLock) {
            repeat(MAX_WRITE_ATTEMPTS) {
                val reference = getSettingsReference<S>(name) ?: return null
                if (isExternallyManaged()) {
                    return SharedSettingsManagedException(managedMessage(name)).asError()
                }

                val revision = refreshFromProvider()
                val before = DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(reference.get())
                if (withoutRequiredKey(name, body)) {
                    return IllegalStateException(keyRequiredMessage(name)).asError()
                }

                val result = reference.update(body)
                if (result.isErr) {
                    return result
                }

                if (storeRendered(revision)) {
                    recordSettingsChange(name, before, body, actor)
                    extensions.emitEvent(SharedConfigurationChangedEvent(name))
                    return result
                }
            }

            return ConcurrentModificationException("Settings domain '$name' kept changing while it was stored, try again").asError()
        }
    }

    /**
     * Latest stored state of a domain together with its entity tag.
     *
     * @return null when the domain does not exist
     */
    fun <S : SharedSettings> getDomainSnapshot(name: String): SharedSettingsSnapshot<S>? {
        synchronized(stateLock) {
            val reference = getSettingsReference<S>(name) ?: return null
            refreshFromProvider()
            return reference.get().snapshot()
        }
    }

    /**
     * Replaces a domain with the settings returned by [transform], which receives the latest stored state and may run more
     * than once. When [ifMatch] is absent the write is unconditional, otherwise it is applied only while the entity tag still
     * describes the stored state. The change is recorded for [actor] with the field names only.
     *
     * The precondition is checked against the stored document, and the write is a compare-and-set on the revision of that
     * document. A write that lost a race against another instance is therefore repeated against what that instance stored:
     * a conditional write fails with 412 there, an unconditional one is applied on top of it.
     *
     * @return null when the domain does not exist, a 412 error when [ifMatch] no longer matches the stored state
     */
    fun <S : SharedSettings> updateDomain(
        name: String,
        ifMatch: String?,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
        transform: (current: S) -> Result<S, ErrorResponse>
    ): Result<SharedSettingsSnapshot<S>, ErrorResponse>? {
        synchronized(stateLock) {
            repeat(MAX_WRITE_ATTEMPTS) {
                val reference = getSettingsReference<S>(name) ?: return null
                if (isExternallyManaged()) {
                    return HttpStatus.CONFLICT.toErrorResult(managedMessage(name))
                }

                val revision = refreshFromProvider()
                val current = reference.get()
                if (!EntityTag.matches(ifMatch, current.entityTag())) {
                    return PRECONDITION_FAILED.toErrorResult("Settings domain '$name' has been changed since it was read, reload it and try again")
                }

                val transformed = transform(current)
                if (transformed.isErr) {
                    return transformed.map { it.snapshot() }
                }

                // Rules of the write only, such as the formats this server has installed. A document read at start skips them.
                val writeViolations = (transformed.get() as? UpdateValidatedSettings)?.updateViolations().orEmpty()
                if (writeViolations.isNotEmpty()) {
                    return badRequest(SettingsViolationException(writeViolations).message).asError()
                }

                if (withoutRequiredKey(name, transformed.get())) {
                    return HttpStatus.CONFLICT.toErrorResult(keyRequiredMessage(name))
                }

                // The message of the exception, never its toString: that would put the class name in front of the rule.
                val applied = reference.update(transformed.get()).mapErr { badRequest(it.message ?: "Settings domain '$name' cannot be stored") }
                if (applied.isErr) {
                    return applied.map { it.snapshot() }
                }

                if (storeRendered(revision)) {
                    recordSettingsChange(name, DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(current), applied.get(), actor)
                    extensions.emitEvent(SharedConfigurationChangedEvent(name))
                    return applied.map { it.snapshot() }
                }
            }

            return HttpStatus.CONFLICT.toErrorResult("Settings domain '$name' kept changing while it was stored, reload it and try again")
        }
    }

    /** The codecs of the configured settings keys, the active one first. Empty while no key is configured. */
    fun settingsCodecs(): List<SettingsEnvelopeCodec> =
        secretCipher?.codecs().orEmpty()

    /**
     * True when settings secret references are switched on, so a stored reference is resolved when the document is loaded.
     */
    fun secretReferencesEnabled(): Boolean =
        secretReferences != null

    /**
     * The settings a consumer reads: [settings] with every reference of its [Sensitive] values replaced by the value it names. The
     * document keeps the references as written, this view is only handed to the consumers of the domain.
     *
     * @throws SecretReferenceException when a reference cannot be resolved, the message names the domain, the property and the reference
     */
    private fun resolveEffective(type: SharedSettingsClass, settings: SharedSettings): SharedSettings {
        val resolver = secretReferences ?: return settings
        val name = settingsName(type)
        val resolved = SettingsSecrets.transformSensitive(type, DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(settings)) { path, value ->
            try {
                resolver.resolve(value)
            } catch (exception: SecretReferenceException) {
                throw SecretReferenceException("Settings domain '$name', property '$path': ${exception.message}")
            }
        }
        return DEFAULT_OBJECT_MAPPER.treeToValue(resolved, type)
    }

    /**
     * The message of a write refused because the shared settings file owns the domain. It names the file, so an operator knows
     * where the change belongs.
     */
    private fun managedMessage(name: String): String =
        "Settings domain '$name' is managed by ${sharedConfigurationProvider.name()} and cannot be changed through the API, change the file instead"

    /**
     * A mutable provider, the remote database, can be written by another instance. Its stored document is read before a
     * precondition is evaluated or a document is rendered, so neither works from a copy that may be behind.
     *
     * @return the revision of the stored document, null when the provider has no shared revisions
     */
    private fun refreshFromProvider(): Long? {
        if (!sharedConfigurationProvider.isMutable()) {
            return null
        }

        val stored = sharedConfigurationProvider.fetchVersionedConfiguration()
        val document = runCatching { DEFAULT_OBJECT_MAPPER.readTree(stored.content) }.getOrNull()
        if (document != null) {
            getDomainNames().forEach { name ->
                val node = document.get(name) ?: return@forEach
                val reference = getSettingsReference<SharedSettings>(name) ?: return@forEach
                // An unchanged document is not applied again, so its references are not read again on every request.
                runCatching { DEFAULT_OBJECT_MAPPER.treeToValue(openSecrets(name, reference.type, node), reference.type) }
                    .onSuccess { stored -> if (!sameDocument(stored, reference.get())) reference.update(stored) }
                    .onFailure { journalist.logger.warn("Shared configuration | Cannot refresh '$name' from ${sharedConfigurationProvider.name()}: ${it.message}") }
            }
        }

        return stored.revision
    }

    private fun sameDocument(left: SharedSettings, right: SharedSettings): Boolean =
        DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(left) == DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(right)

    /**
     * Stores the rendered configuration if the stored document still has [expectedRevision], see [refreshFromProvider].
     *
     * @return false when another instance has stored a newer document in the meantime
     */
    private fun storeRendered(expectedRevision: Long?): Boolean =
        sharedConfigurationProvider.compareAndUpdateConfiguration(renderConfiguration(), expectedRevision)

    private fun recordSettingsChange(name: String, before: JsonNode, body: SharedSettings, actor: AuditActor) {
        val fields = changedSettingPaths(before, DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(body))
        if (fields.isEmpty()) {
            return
        }

        extensions.emitEvent(AuditEvent(action = AuditAction.SETTINGS_CHANGED, actor = actor, details = mapOf("domain" to name, "fields" to fields)))
        if (name == MAVEN_DOMAIN) {
            repositoryAuditEvents(before, DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(body), actor).forEach { extensions.emitEvent(it) }
        }
    }

    private fun domainTypes(): Map<String, Class<*>> =
        getDomainNames()
            .mapNotNull { name -> getSettingsReference<SharedSettings>(name)?.let { name to it.type } }
            .toMap()

    /**
     * The stored document. With [sealed] set, the plain secret values are written as envelopes while a key is active.
     */
    private fun renderConfiguration(sealed: Boolean = true): String {
        val root = DEFAULT_OBJECT_MAPPER.createObjectNode()
        getDomainNames().forEach { name ->
            val reference = getSettingsReference<SharedSettings>(name)!!
            val node = DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(reference.get())
            root.set<JsonNode>(name, if (sealed) sealSecrets(name, reference.type, node) else node)
        }
        return DEFAULT_OBJECT_MAPPER.writeValueAsString(root)
    }

    /**
     * The saved state of every domain as plain JSON: secrets in clear, references as written. It is the content of a password
     * protected export, and it is never written to the stored document.
     */
    fun plainDocument(): String =
        synchronized(stateLock) {
            refreshFromProvider()
            renderConfiguration(sealed = false)
        }

    /**
     * The number of stored secret values that are still plain text. Empty values, references and envelopes are not counted.
     */
    /**
     * The envelopes of the stored document, with the domain, the property path and the key identifier each one names. Used at start,
     * before the document is opened, so a missing or foreign key is reported with its place.
     */
    fun storedSealedSettings(): List<SealedSetting> {
        val root = runCatching { DEFAULT_OBJECT_MAPPER.readTree(sharedConfigurationProvider.fetchConfiguration()) }.getOrNull()
            ?: return emptyList()
        val found = mutableListOf<SealedSetting>()
        getDomainNames().forEach { name ->
            val reference = getSettingsReference<SharedSettings>(name) ?: return@forEach
            val node = root.get(name) ?: return@forEach
            SettingsSecrets.transformSensitive(reference.type, node) { path, value ->
                if (SettingsEnvelopeCodec.isEnvelope(value)) {
                    found.add(SealedSetting(domain = name, path = path, keyId = value.split(':').getOrElse(2) { "" }))
                }
                value
            }
        }
        return found
    }

    fun plaintextSecretCount(): Int =
        countSecretValues(sharedConfigurationProvider.fetchConfiguration()) { isPlainSecret(it) }

    /**
     * Stores the stored document again with the active key, for example at the first start with a key. Only a database can be
     * written by the instance, so the rewrite is refused for a managed file.
     *
     * @return the number of secret values that were sealed, or null when nothing was stored
     */
    fun sealStoredSecrets(reason: String, actor: AuditActor = AuditActor.SYSTEM_ACTOR): Int? =
        rewriteDocument(sealed = true, reason = reason, actor = actor)

    /**
     * Stores the stored document with plain secret values again, which is the downgrade path. Callers must check for the active key
     * first, see [sealStoredSecrets].
     *
     * @return the number of secret values that were written in clear, or null when nothing was stored
     */
    fun unsealStoredSecrets(reason: String, actor: AuditActor = AuditActor.SYSTEM_ACTOR): Int? =
        rewriteDocument(sealed = false, reason = reason, actor = actor)

    private fun rewriteDocument(sealed: Boolean, reason: String, actor: AuditActor): Int? {
        synchronized(stateLock) {
            if (isExternallyManaged()) {
                return null
            }

            repeat(MAX_WRITE_ATTEMPTS) {
                val revision = refreshFromProvider()
                val rendered = renderConfiguration(sealed)
                val count = countSecretValues(rendered) { if (sealed) SettingsEnvelopeCodec.isEnvelope(it) else isPlainSecret(it) }
                if (sharedConfigurationProvider.compareAndUpdateConfiguration(rendered, revision)) {
                    val details = mapOf(
                        "reason" to reason,
                        "values" to count,
                        "activeKeyId" to secretCipher?.activeKeyId,
                        "previousKeyId" to secretCipher?.previousKeyId,
                    )
                    extensions.emitEvent(AuditEvent(action = AuditAction.SETTINGS_SECRETS_REENCRYPTED, actor = actor, details = details))
                    return count
                }
            }

            return null
        }
    }

    private fun keyRequiredMessage(name: String): String =
        "Settings domain '$name' holds a secret, and the settings encryption is required while no settings key is active. " +
            "Set ${SettingsKeySources.KEY} or " +
            "${SettingsKeySources.KEY_FILE} first."

    /**
     * True when a write would store a plain secret while the mode requires a key and no key is active.
     */
    private fun withoutRequiredKey(name: String, value: SharedSettings): Boolean {
        if (secretCipher?.requiresKey != true) {
            return false
        }

        val type = getSettingsReference<SharedSettings>(name)?.type ?: return false
        var plain = false
        SettingsSecrets.transformSensitive(type, DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(value)) { _, stored ->
            plain = plain || isPlainSecret(stored)
            stored
        }
        return plain
    }

    /**
     * Counts the secret values of a stored document that match [predicate]. Domains without a known type are not counted.
     */
    private fun countSecretValues(document: String, predicate: (String) -> Boolean): Int {
        val root = runCatching { DEFAULT_OBJECT_MAPPER.readTree(document) }.getOrNull() ?: return 0
        var count = 0
        getDomainNames().forEach { name ->
            val reference = getSettingsReference<SharedSettings>(name) ?: return@forEach
            val node = root.get(name) ?: return@forEach
            SettingsSecrets.transformSensitive(reference.type, node) { _, value ->
                if (value.isNotEmpty() && predicate(value)) {
                    count++
                }
                value
            }
        }
        return count
    }

    inline fun <reified T : SharedSettings> getDomainSettings(): MutableReference<T> =
        getDomainSettings(T::class)

    fun <S : SharedSettings> getDomainSettings(settingsClass: KClass<S>): MutableReference<S> =
        getDomainSettings(settingsClass.java)

    @Suppress("UNCHECKED_CAST")
    fun <S : SharedSettings> getDomainSettings(settingsClass: Class<S>): MutableReference<S> =
        sharedSettingsProvider.domains[settingsClass] as MutableReference<S>

    @Suppress("UNCHECKED_CAST")
    fun <S : SharedSettings> getSettingsReference(name: String): SharedSettingsReference<S>? =
        configHandlers[name] as? SharedSettingsReference<S>

    fun getDomainNames(): Collection<String> =
        configHandlers.keys

    fun isUpdateRequired(): Boolean =
        sharedConfigurationProvider.isUpdateRequired()

    /**
     * Loads the stored document when the provider reports a newer revision. The scheduled poll and the cluster bus both call it.
     *
     * @return true when a newer document was loaded
     */
    internal fun reloadIfChanged(): Boolean =
        synchronized(stateLock) {
            if (!isUpdateRequired()) {
                return@synchronized false
            }

            journalist.logger.info("Propagation | Shared configuration has been changed in ${getProviderName()}, updating current instance...")
            loadSharedSettingsFromString(fetchConfiguration())
            journalist.logger.info("Propagation | Sources have been updated successfully")
            true
        }

    fun isMutable(): Boolean =
        sharedConfigurationProvider.isMutable()

    fun isExternallyManaged(): Boolean =
        sharedConfigurationProvider.isExternallyManaged()

    fun getProviderName(): String =
        sharedConfigurationProvider.name()

}
