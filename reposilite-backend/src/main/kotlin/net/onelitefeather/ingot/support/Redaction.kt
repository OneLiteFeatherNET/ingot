/*
 * Copyright (c) 2026 OneLiteFeather
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

package net.onelitefeather.ingot.support

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode
import com.reposilite.configuration.shared.SettingsSecrets
import net.onelitefeather.ingot.cluster.ClusterBusUrl

private val WHITESPACE = Regex("\\s+")

/** Counts the values that the bundle withholds, masks or scrubs. The manifest reports the total. */
class RedactionTally {

    var count: Int = 0
        private set

    fun add(amount: Int = 1) {
        count += amount
    }
}

/** One shared settings domain as the support bundle reads it: its name, its settings type when known, and its resolved document. */
data class SharedDomainDocument(val name: String, val type: Class<*>?, val document: JsonNode)

/**
 * The redaction contract, version 1. Every value the bundle writes passes through one of these rules, and a value that no rule
 * describes is not written. The allowlists are the single place where a new key is admitted.
 */
object Redaction {

    const val RULE_VERSION = 1

    /** Stands in for a value that the contract does not let the bundle write. */
    const val WITHHELD = "[withheld]"

    /** Stands in for a secret found in a log line. */
    const val REDACTED = "[redacted]"

    /** The placeholder of the settings masking, the same one the settings API uses. */
    const val HIDDEN_SECRET = "********"

    /** The placeholder of the user part of a bus URL, the one the cluster status uses. */
    const val REDACTED_USER = ClusterBusUrl.REDACTED

    /** The local key that is reduced by [DatabaseDescription], never copied as written. */
    const val LOCAL_DATABASE = "database"

    /** Local keys whose values the bundle writes as they are. A key outside both sets is withheld, which is the safe default. */
    val LOCAL_ALLOWLIST: Set<String> = setOf(
        "hostname", "port", "sslEnabled", "sslPort", "enforceSsl", "webThreadPool", "ioThreadPool", "databaseThreadPool",
        "compressionStrategy", "idleTimeout", "bypassExternalCache", "cachedLogSize", "defaultFrontend", "basePath",
        "hideSettingsSecrets", "searchEnabled", "searchEngine", "schemaLockTimeout", "debugEnabled", "pluginUi",
    )

    /** Local keys that hold a secret or a path to one. They are always withheld. */
    val LOCAL_WITHHELD: Set<String> = setOf("keyPassword", "keyPath")

    /** Environment variables whose values the bundle writes. The legacy spellings are listed next to the Ingot ones. */
    val ENVIRONMENT_ALLOWLIST: Set<String> = setOf(
        "PUID", "PGID", "REPOSILITE_FORCE_CHOWN",
        "INGOT_READ_ONLY", "REPOSILITE_READ_ONLY",
        "INGOT_METRICS_ENABLED", "REPOSILITE_METRICS_ENABLED",
        "INGOT_SUPPORT_BUNDLE_ENABLED", "REPOSILITE_SUPPORT_BUNDLE_ENABLED",
    )

    /** Environment variables that hold JVM arguments. Their values pass through the JVM argument rule, token by token. */
    val ENVIRONMENT_OPTIONS: Set<String> = setOf("JAVA_OPTS", "REPOSILITE_OPTS")

    private val KEPT_PROPERTY_PREFIXES = listOf("ingot.", "reposilite.")

    private val KEPT_JVM_FLAGS = listOf("-Xmx", "-Xms", "-Xss", "-XX:", "--add-opens", "--add-exports", "--add-modules", "--enable-preview")

    /** Key names that announce a secret. Matched case-insensitively, the same way the log scrubber matches them. */
    val SECRET_NAME = Regex("(?i)(secret_key|access_key|apikey|password|passwd|secret|token|credential)")

    /** The local values, redacted. The database setting is reduced to its kind and endpoint. */
    fun local(values: Map<String, String?>, tally: RedactionTally): Map<String, String> =
        values.toSortedMap().mapValues { (key, value) ->
            when {
                key == LOCAL_DATABASE -> DatabaseDescription.describe(value).let { description ->
                    if (description.redacted) tally.add()
                    description.text()
                }
                key in LOCAL_WITHHELD || key !in LOCAL_ALLOWLIST -> withheld(tally)
                else -> value.orEmpty()
            }
        }

    /** The environment, redacted. Only allowlisted names keep their values, and the options variables are split into arguments. */
    fun environment(values: Map<String, String>, tally: RedactionTally): Map<String, String> =
        values.toSortedMap().mapValues { (name, value) ->
            when {
                name in ENVIRONMENT_ALLOWLIST -> value
                name in ENVIRONMENT_OPTIONS -> value.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ") { jvmArgument(it, tally) }
                else -> withheld(tally)
            }
        }

    /** The system properties, redacted. Only the Ingot and Reposilite prefixes keep their values, and not when the name is a secret one. */
    fun systemProperties(values: Map<String, String>, tally: RedactionTally): Map<String, String> =
        values.toSortedMap().mapValues { (key, value) ->
            if (isKeptProperty(key)) value else withheld(tally)
        }

    /** The JVM arguments, redacted argument by argument. */
    fun jvmArguments(arguments: List<String>, tally: RedactionTally): List<String> =
        arguments.map { jvmArgument(it, tally) }

    /**
     * The shared settings domains, each masked with the settings masking, whether or not the settings hide secrets. A reference
     * is masked too. A domain without a known type is withheld completely.
     */
    fun shared(domains: List<SharedDomainDocument>, tally: RedactionTally): Map<String, JsonNode> =
        domains.associate { domain ->
            val type = domain.type
            val masked: JsonNode = if (type == null) {
                TextNode(withheld(tally))
            } else {
                val counted = SettingsSecrets.transformSensitive(type, domain.document) { _, value ->
                    if (value.isNotEmpty()) tally.add()
                    value
                }
                SettingsSecrets.mask(type, counted, keepReferences = false)
            }
            domain.name to masked
        }

    /** The bus URL without its credentials, or null when no bus is configured. */
    fun busUrl(url: ClusterBusUrl?, tally: RedactionTally): String? =
        url?.let {
            if (it.username != null || it.password != null) tally.add()
            it.redacted()
        }

    /**
     * Every secret value that the bundle must never contain: the sensitive values of the shared domains, the secret parts of the
     * database setting, the TLS key password and the bus credentials. The guard searches for exactly these.
     */
    fun knownSecrets(domains: List<SharedDomainDocument>, localValues: Map<String, String?>, bus: ClusterBusUrl?): Set<String> {
        val values = linkedSetOf<String>()
        domains.forEach { domain ->
            val type = domain.type ?: return@forEach
            SettingsSecrets.transformSensitive(type, domain.document) { _, value ->
                values.add(value)
                value
            }
        }
        localValues["keyPassword"]?.let(values::add)
        localValues[LOCAL_DATABASE]?.let { values.addAll(DatabaseDescription.describe(it).secrets) }
        bus?.username?.let(values::add)
        bus?.password?.let(values::add)
        return values.filter { it.isNotEmpty() }.toSet()
    }

    /**
     * The values of the environment and the system properties that the bundle withholds. They are scrubbed from the log lines, but
     * not searched for by the guard, because an ordinary word can equal the value of an unrelated variable.
     */
    fun withheldValues(environment: Map<String, String>, systemProperties: Map<String, String>, jvmArguments: List<String>): Set<String> =
        (environment.filterKeys { it !in ENVIRONMENT_ALLOWLIST && it !in ENVIRONMENT_OPTIONS }.values +
            systemProperties.filterKeys { !isKeptProperty(it) }.values +
            jvmArguments.flatMap { withheldJvmValues(it) })
            .filter { it.length >= LogScrubber.MIN_LENGTH }
            .toSet()

    /** The value part of one JVM argument that the argument rule withholds. Empty when the argument is kept whole. */
    private fun withheldJvmValues(argument: String): List<String> {
        if (KEPT_JVM_FLAGS.any { argument.startsWith(it) }) return emptyList()
        if (!argument.startsWith("-")) return listOf(argument)
        if (argument.startsWith("-D")) {
            val body = argument.removePrefix("-D")
            if (!body.contains('=') || isKeptProperty(body.substringBefore('='))) return emptyList()
            return listOf(body.substringAfter('='))
        }
        val separator = argument.indexOfFirst { it == '=' || it == ':' }
        return if (separator < 0) emptyList() else listOf(argument.substring(separator + 1))
    }

    private fun isKeptProperty(key: String): Boolean =
        KEPT_PROPERTY_PREFIXES.any { key.startsWith(it) } && !SECRET_NAME.containsMatchIn(key)

    private fun jvmArgument(argument: String, tally: RedactionTally): String {
        if (KEPT_JVM_FLAGS.any { argument.startsWith(it) }) return argument
        if (!argument.startsWith("-")) return withheld(tally)

        if (argument.startsWith("-D")) {
            val body = argument.removePrefix("-D")
            if (!body.contains('=')) return argument
            val key = body.substringBefore('=')
            return if (isKeptProperty(key)) argument else "-D$key=${withheld(tally)}"
        }

        val separator = argument.indexOfFirst { it == '=' || it == ':' }
        return if (separator < 0) argument else argument.substring(0, separator + 1) + withheld(tally)
    }

    private fun withheld(tally: RedactionTally): String {
        tally.add()
        return WITHHELD
    }
}
