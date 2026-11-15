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
package net.onelitefeather.ingot.cli

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.nio.file.Path
import java.nio.file.Paths

class ConfigException(message: String) : RuntimeException(message)

/** The settings after resolution. A null value means that no source gives the setting. */
data class CliConfig(
    val url: String?,
    val context: String?,
    val tokenFile: Path?,
    val credentialHelper: String?,
    val caCert: Path?,
    val output: String,
    val timeoutSeconds: Long,
    val insecure: Boolean,
)

/**
 * The config file: a current context and the contexts by name. It holds no secret. A token value, or insecure TLS, is
 * refused at parse time, so that a file cannot quietly carry either.
 */
class ConfigFile(val current: String?, val contexts: Map<String, Map<String, String>>) {

    companion object {

        private val YAML = ObjectMapper(YAMLFactory())

        fun parse(text: String): ConfigFile {
            val root = YAML.readTree(text.ifBlank { "{}" })
            if (root.has("token") || root.has("insecure")) {
                throw ConfigException("The config file must not hold a token or insecure TLS. Use tokenFile for the token.")
            }
            val contexts = mutableMapOf<String, Map<String, String>>()
            root.path("contexts").forEach { context ->
                val name = context.path("name").asText()
                if (context.has("token") || context.has("insecure")) {
                    throw ConfigException("Context '$name' must not hold a token or insecure TLS. Use tokenFile for the token.")
                }
                contexts[name] = listOf("url", "tokenFile", "credentialHelper", "caCert").mapNotNull { key ->
                    context.path(key).takeIf { !it.isMissingNode && !it.isNull }?.asText()?.let { key to it }
                }.toMap()
            }
            return ConfigFile(current = root.path("current").takeIf { !it.isMissingNode && !it.isNull }?.asText(), contexts = contexts)
        }

        /** The default location: `INGOT_CONFIG`, then `$XDG_CONFIG_HOME/ingot/config.yaml`, then `~/.config/ingot/config.yaml`. */
        fun defaultPath(env: Map<String, String>, home: String = System.getProperty("user.home")): Path =
            env["INGOT_CONFIG"]?.let { Paths.get(it) }
                ?: env["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() }?.let { Paths.get(it, "ingot", "config.yaml") }
                ?: Paths.get(home, ".config", "ingot", "config.yaml")

    }

}

object ConfigResolver {

    /**
     * Resolves each setting from the flag, then the `INGOT_` variable, then the selected context, then the default. The
     * flags are keyed by option name without dashes. The removed `token` flag is refused with a pointer to the variables.
     */
    fun resolve(flags: Map<String, String>, env: Map<String, String>, file: ConfigFile): CliConfig {
        if (flags.containsKey("token")) {
            throw ConfigException("The --token option is not accepted, the token would reach the shell history. Use INGOT_TOKEN, INGOT_TOKEN_FILE or a credentials helper.")
        }
        val contextName = flags["context"] ?: env["INGOT_CONTEXT"] ?: file.current
        val context = contextName?.let { name -> file.contexts[name] ?: throw ConfigException("No context named '$name' in the config file") }.orEmpty()

        fun pick(flag: String, variable: String, key: String): String? =
            flags[flag] ?: env[variable]?.takeIf { it.isNotBlank() } ?: context[key]

        return CliConfig(
            url = pick("url", "INGOT_URL", "url")?.trimEnd('/'),
            context = contextName,
            tokenFile = pick("token-file", "INGOT_TOKEN_FILE", "tokenFile")?.let { Paths.get(it) },
            credentialHelper = pick("credential-helper", "INGOT_CREDENTIAL_HELPER", "credentialHelper"),
            caCert = pick("ca-cert", "INGOT_CA_CERT", "caCert")?.let { Paths.get(it) },
            output = pick("output", "INGOT_OUTPUT", "output") ?: "text",
            timeoutSeconds = pick("timeout", "INGOT_TIMEOUT", "timeout")?.toLongOrNull() ?: 30L,
            insecure = flags.containsKey("insecure") || env["INGOT_INSECURE"] == "true",
        )
    }

}
