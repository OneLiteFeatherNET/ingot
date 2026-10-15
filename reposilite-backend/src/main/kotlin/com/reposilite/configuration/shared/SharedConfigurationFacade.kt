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

import com.fasterxml.jackson.databind.SerializationFeature
import com.github.victools.jsonschema.generator.SchemaGenerator
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.journalist.Journalist
import com.reposilite.plugin.api.Facade
import com.reposilite.shared.EntityTag
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequest
import com.reposilite.shared.toErrorResult
import com.reposilite.status.FailureFacade
import io.javalin.http.HttpStatus.PRECONDITION_FAILED
import io.javalin.openapi.JsonSchemaLoader
import panda.std.Result
import panda.std.Result.supplyThrowing
import panda.std.asError
import panda.std.asSuccess
import panda.std.ok
import panda.std.reactive.MutableReference
import java.util.function.Supplier
import kotlin.reflect.KClass

/**
 * The stored state of a settings domain and the entity tag that identifies exactly that state.
 */
data class SharedSettingsSnapshot<S : SharedSettings>(
    val settings: S,
    val etag: String,
)

private val canonicalWriter = DEFAULT_OBJECT_MAPPER.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

private fun SharedSettings.entityTag(): String =
    EntityTag.of(canonicalWriter.writeValueAsBytes(this))

private fun <S : SharedSettings> S.snapshot(): SharedSettingsSnapshot<S> =
    SharedSettingsSnapshot(this, entityTag())

class SharedConfigurationFacade(
    private val journalist: Journalist,
    private val schemaGenerator: Lazy<SchemaGenerator>,
    private val failureFacade: FailureFacade,
    private val sharedSettingsProvider: SharedSettingsProvider,
    private val sharedConfigurationProvider: SharedConfigurationProvider
) : Facade {

    private val configHandlers = mutableMapOf<String, SharedSettingsReference<*>>()

    /**
     * Serializes reads and writes of the settings of this instance, so a precondition is checked against the state it writes.
     */
    private val stateLock = Any()

    init {
        val jsonSchemaLoader = JsonSchemaLoader()
        val knownSchemes = jsonSchemaLoader.loadGeneratedSchemes().associateBy { it.name }

        sharedSettingsProvider.domains.forEach { (type, settings) ->
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
                    getter = { settings.get() },
                    setter = { settings.update(it) }
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
        val updateResult = supplyThrowing { DEFAULT_OBJECT_MAPPER.readTree(content) }
            .map { node -> getDomainNames().asSequence().filter { node.has(it) }.associateWith { node.get(it) } }
            .orElseGet { emptyMap() }
            .map { (name, obj) ->
                val ref = getSettingsReference<SharedSettings>(name)!!
                val settings = DEFAULT_OBJECT_MAPPER.readValue(obj.toString(), ref.type)
                ref to ref.update(settings)
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

    fun <S : SharedSettings> updateSharedSettings(name: String, body: S): Result<S, out Exception>? {
        synchronized(stateLock) {
            refreshFromProvider()
            return getSettingsReference<S>(name)
                ?.update(body)
                ?.peek { sharedConfigurationProvider.updateConfiguration(renderConfiguration()) }
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
     * Replaces a domain with the settings returned by [transform], which receives the latest stored state. The precondition
     * and the write run under one lock. When [ifMatch] is absent the write is unconditional, otherwise it is applied only
     * while the entity tag still describes the stored state.
     *
     * @return null when the domain does not exist, a 412 error when [ifMatch] no longer matches the stored state
     */
    fun <S : SharedSettings> updateDomain(
        name: String,
        ifMatch: String?,
        transform: (current: S) -> Result<S, ErrorResponse>
    ): Result<SharedSettingsSnapshot<S>, ErrorResponse>? {
        synchronized(stateLock) {
            val reference = getSettingsReference<S>(name) ?: return null
            refreshFromProvider()
            val current = reference.get()
            if (!EntityTag.matches(ifMatch, current.entityTag())) {
                return PRECONDITION_FAILED.toErrorResult("Settings domain '$name' has been changed since it was read, reload it and try again")
            }

            return transform(current).flatMap { settings ->
                reference.update(settings)
                    .mapErr { badRequest(it.toString()) }
                    .peek { sharedConfigurationProvider.updateConfiguration(renderConfiguration()) }
                    .map { it.snapshot() }
            }
        }
    }

    /**
     * A mutable provider, the remote database, can be written by another instance. Its stored document is read before a
     * precondition is evaluated or a document is rendered, so neither works from a copy that may be behind.
     */
    private fun refreshFromProvider() {
        if (!sharedConfigurationProvider.isMutable()) {
            return
        }

        val stored = runCatching { DEFAULT_OBJECT_MAPPER.readTree(sharedConfigurationProvider.fetchConfiguration()) }.getOrNull()
            ?: return

        getDomainNames().forEach { name ->
            val node = stored.get(name) ?: return@forEach
            val reference = getSettingsReference<SharedSettings>(name) ?: return@forEach
            runCatching { DEFAULT_OBJECT_MAPPER.treeToValue(node, reference.type) }
                .onSuccess { reference.update(it) }
                .onFailure { journalist.logger.warn("Shared configuration | Cannot refresh '$name' from ${sharedConfigurationProvider.name()}: ${it.message}") }
        }
    }

    private fun domainTypes(): Map<String, Class<*>> =
        getDomainNames()
            .mapNotNull { name -> getSettingsReference<SharedSettings>(name)?.let { name to it.type } }
            .toMap()

    private fun renderConfiguration(): String =
        getDomainNames()
            .associateWith { getSettingsReference<SharedSettings>(it)!!.get() }
            .let { DEFAULT_OBJECT_MAPPER.writeValueAsString(it) }

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

    fun isMutable(): Boolean =
        sharedConfigurationProvider.isMutable()

    fun getProviderName(): String =
        sharedConfigurationProvider.name()

}
