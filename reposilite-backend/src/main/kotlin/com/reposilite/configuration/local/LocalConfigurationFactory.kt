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

package com.reposilite.configuration.local

import com.reposilite.ReposiliteParameters
import com.reposilite.configuration.local.infrastructure.LocalConfigurationProvider
import com.reposilite.journalist.Journalist
import panda.std.reactive.Reference
import panda.std.reactive.ReferenceUtils
import java.lang.IllegalArgumentException
import kotlin.reflect.full.declaredMemberProperties

internal object LocalConfigurationFactory {

    fun createLocalConfiguration(journalist: Journalist?, parameters: ReposiliteParameters): LocalConfiguration =
        LocalConfigurationProvider(
            journalist = journalist,
            workingDirectory = parameters.workingDirectory,
            configurationFile = parameters.localConfigurationPath,
            mode = parameters.localConfigurationMode,
            localConfiguration = LocalConfiguration()
        ).also { provider ->
            provider.initialize()
            loadCustomPropertiesViaReflections(journalist, provider.localConfiguration)
        }.localConfiguration

    /**
     * Load custom properties from environment variables and system properties.
     */
    private fun loadCustomPropertiesViaReflections(journalist: Journalist?, localConfiguration: LocalConfiguration) =
        applyCustomProperties(journalist, localConfiguration, getEnvironmentVariables() + getProperties())

    internal fun applyCustomProperties(
        journalist: Journalist?,
        localConfiguration: LocalConfiguration,
        properties: Map<String, String>
    ) {
        properties.forEach { (key, value) ->
            val property = localConfiguration::class.declaredMemberProperties.find { it.name.equals(key, ignoreCase = true) } ?: run {
                journalist?.logger?.warn("Unknown local configuration property: $key")
                return@forEach
            }

            @Suppress("UNCHECKED_CAST")
            val reference = property.getter.call(localConfiguration) as Reference<Any>

            when (reference.type.kotlin) {
                Boolean::class -> ReferenceUtils.setValue(reference, value.toBoolean())
                Int::class -> ReferenceUtils.setValue(reference, value.toInt())
                Long::class -> ReferenceUtils.setValue(reference, value.toLong())
                String::class -> ReferenceUtils.setValue(reference, value)
                else -> throw IllegalArgumentException("Unsupported local configuration property type: $key (expected: ${reference.type})")
            }

            journalist?.logger?.info("Local configuration has been updated by external property: $key=${value.take(1)}***${value.takeLast(1)}")
        }
    }

    /**
     * Prefixes recognized for external overrides, most specific first. The legacy REPOSILITE
     * prefix stays supported so an existing deployment keeps working after the switch to Ingot;
     * when both prefixes carry the same property, the INGOT one wins.
     */
    private val ENVIRONMENT_VARIABLE_PREFIXES = listOf("INGOT_LOCAL_", "REPOSILITE_LOCAL_")
    private val SYSTEM_PROPERTY_PREFIXES = listOf("ingot.local.", "reposilite.local.")

    /**
     * Get all environment variables that start with INGOT_LOCAL_ or the legacy REPOSILITE_LOCAL_, example:
     * INGOT_LOCAL_SSLENABLED=false
     */
    internal fun getEnvironmentVariables(environment: Map<String, String> = System.getenv()): Map<String, String> =
        stripPrefixes(environment.mapKeys { (key) -> key.uppercase() }, ENVIRONMENT_VARIABLE_PREFIXES)

    /**
     * Get all system properties that start with ingot.local. or the legacy reposilite.local., example:
     * ingot.local.sslEnabled=false
     */
    private fun getProperties(): Map<String, String> =
        System.getProperties()
            .propertyNames()
            .asSequence()
            .map { it.toString() }
            .associate { it.lowercase() to System.getProperty(it) }
            .let { stripPrefixes(it, SYSTEM_PROPERTY_PREFIXES) }

    /**
     * Strips the matching prefix off every key of [source] that carries one. [prefixes] is ordered
     * most preferred first and applied back to front, so a value found under an earlier prefix
     * overwrites the one a later prefix contributed for the same property.
     */
    private fun stripPrefixes(source: Map<String, String>, prefixes: List<String>): Map<String, String> =
        prefixes.foldRight(mutableMapOf<String, String>()) { prefix, accumulator ->
            source
                .filterKeys { it.startsWith(prefix) }
                .forEach { (key, value) -> accumulator[key.substringAfter(prefix)] = value }
            accumulator
        }

}
