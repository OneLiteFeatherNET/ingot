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
import java.io.PrintStream
import java.lang.IllegalArgumentException
import java.nio.file.Path
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
            loadCustomPropertiesViaReflections(journalist, parameters.workingDirectory, provider.localConfiguration)
        }.localConfiguration

    /**
     * Load custom properties from environment variables and system properties.
     */
    private fun loadCustomPropertiesViaReflections(journalist: Journalist?, workingDirectory: Path, localConfiguration: LocalConfiguration) =
        applyCustomProperties(
            journalist,
            localConfiguration,
            getEnvironmentVariables(workingDirectory = workingDirectory, journalist = journalist) +
                getProperties(workingDirectory = workingDirectory, journalist = journalist)
        )

    internal fun applyCustomProperties(
        journalist: Journalist?,
        localConfiguration: LocalConfiguration,
        properties: Map<String, String>,
        errorOutput: PrintStream = System.err,
    ) {
        properties.forEach { (key, value) ->
            val property = localConfiguration::class.declaredMemberProperties.find { it.name.equals(key, ignoreCase = true) } ?: run {
                journalist?.logger?.warn("Unknown local configuration property: $key")
                return@forEach
            }

            @Suppress("UNCHECKED_CAST")
            val reference = property.getter.call(localConfiguration) as Reference<Any>

            when (reference.type.kotlin) {
                Boolean::class -> {
                    if (!value.equals("true", ignoreCase = true) && !value.equals("false", ignoreCase = true)) {
                        journalist?.logger?.warn("Local configuration | $key expects true or false, any other value is read as false")
                    }
                    ReferenceUtils.setValue(reference, value.toBoolean())
                }
                Int::class -> ReferenceUtils.setValue(reference, convertOrReport(key, "int", journalist, errorOutput) { value.toInt() })
                Long::class -> ReferenceUtils.setValue(reference, convertOrReport(key, "long", journalist, errorOutput) { value.toLong() })
                String::class -> ReferenceUtils.setValue(reference, value)
                else -> throw IllegalArgumentException("Unsupported local configuration property type: $key (expected: ${reference.type})")
            }

            journalist?.logger?.info("Local configuration has been updated by external property: $key=${value.take(1)}***${value.takeLast(1)}")
        }
    }

    /**
     * Runs a conversion and reports a value that does not convert, then rethrows the exception the conversion threw. The value
     * itself is left out of the message. Without a journalist, which exists only on the second pass, the report goes to [errorOutput].
     */
    private fun <T> convertOrReport(
        key: String,
        expectedType: String,
        journalist: Journalist?,
        errorOutput: PrintStream,
        convert: () -> T,
    ): T =
        try {
            convert()
        } catch (exception: NumberFormatException) {
            val message = "Local configuration | $key cannot be read as $expectedType, the value is not a valid $expectedType"
            if (journalist != null) {
                journalist.logger.error(message)
            } else {
                errorOutput.println("ERROR $message")
            }
            throw exception
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
     * INGOT_LOCAL_SSLENABLED=false. A variable with the _FILE suffix, such as INGOT_LOCAL_KEYPASSWORD_FILE, gives the value
     * of its setting from the file it names, see [LocalSettingFiles].
     */
    internal fun getEnvironmentVariables(
        environment: Map<String, String> = System.getenv(),
        workingDirectory: Path = Path.of(""),
        journalist: Journalist? = null,
        knownSettings: Set<String> = localSettingNames(),
    ): Map<String, String> =
        LocalSettingFiles.resolve(
            entries = environment,
            prefixes = ENVIRONMENT_VARIABLE_PREFIXES,
            fold = { it.uppercase() },
            fileSuffix = "_FILE",
            knownSettings = knownSettings,
            workingDirectory = workingDirectory,
            journalist = journalist,
        )

    /**
     * Get all system properties that start with ingot.local. or the legacy reposilite.local., example:
     * ingot.local.sslEnabled=false. A property with the .file suffix, such as ingot.local.keyPassword.file, gives the value
     * of its setting from the file it names, see [LocalSettingFiles].
     */
    internal fun getProperties(
        properties: Map<String, String> = systemProperties(),
        workingDirectory: Path = Path.of(""),
        journalist: Journalist? = null,
        knownSettings: Set<String> = localSettingNames(),
    ): Map<String, String> =
        LocalSettingFiles.resolve(
            entries = properties,
            prefixes = SYSTEM_PROPERTY_PREFIXES,
            fold = { it.lowercase() },
            fileSuffix = ".file",
            knownSettings = knownSettings,
            workingDirectory = workingDirectory,
            journalist = journalist,
        )

    private fun systemProperties(): Map<String, String> =
        System.getProperties()
            .propertyNames()
            .asSequence()
            .map { it.toString() }
            .mapNotNull { name -> System.getProperty(name)?.let { name to it } }
            .toMap()

    /**
     * The names of all local settings in lower case, the keys that a file variable may refer to.
     */
    private fun localSettingNames(): Set<String> =
        LocalConfiguration::class.declaredMemberProperties.map { it.name.lowercase() }.toSet()

}
