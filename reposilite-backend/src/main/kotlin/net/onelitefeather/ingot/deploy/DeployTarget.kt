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

package net.onelitefeather.ingot.deploy

/**
 * What this instance serves. `all` is the default and serves the dashboard together with the API and the Maven
 * endpoints. `server` serves the API and the Maven endpoints only.
 */
enum class DeployTarget(val value: String) {
    ALL("all"),
    SERVER("server");

    companion object {
        val VALID_VALUES: String = entries.joinToString(", ") { it.value }

        fun parse(value: String): DeployTarget? = entries.firstOrNull { it.value == value }
    }
}

/**
 * The effective [target] and the messages to log once at start. [warnings] is empty unless a target source is set and
 * the legacy `defaultFrontend` switch disagrees with it.
 */
data class DeployTargetResolution(
    val target: DeployTarget,
    val warnings: List<String>,
)

/**
 * Resolves the deploy target from its sources. The function is pure: every source is passed in as a value, so it reads
 * neither the environment nor the JVM properties.
 *
 * Precedence: the command line option, the system property, the environment variable, and then the legacy
 * `defaultFrontend` switch. The legacy switch means `server` when it is `false` and `all` otherwise, and it is only
 * consulted when no target source is set.
 */
object DeployTargetResolver {

    const val OPTION = "--target"
    const val PROPERTY = "ingot.target"
    const val ENVIRONMENT_VARIABLE = "INGOT_TARGET"

    /**
     * Throws [IllegalArgumentException] when a set source carries a value other than `all` or `server`. The message names
     * the source and lists the valid values. There is no fallback to another target.
     */
    fun resolve(
        commandLine: String?,
        properties: Map<String, String>,
        environment: Map<String, String>,
        legacyDefaultFrontend: Boolean,
    ): DeployTargetResolution {
        val setSource = sequenceOf(
            OPTION to commandLine,
            PROPERTY to properties[PROPERTY],
            ENVIRONMENT_VARIABLE to environment[ENVIRONMENT_VARIABLE],
        )
            .mapNotNull { (source, value) -> value?.takeUnless { it.isBlank() }?.let { source to it } }
            .firstOrNull()
            ?: return DeployTargetResolution(
                target = if (legacyDefaultFrontend) DeployTarget.ALL else DeployTarget.SERVER,
                warnings = emptyList(),
            )

        val (source, value) = setSource
        val target = DeployTarget.parse(value)
            ?: throw IllegalArgumentException("Unknown deploy target '$value' from $source. Valid values: ${DeployTarget.VALID_VALUES}.")

        // The legacy switch states a target only when it is off. Its default (on) is not a statement about the target,
        // so a plain start with a target set does not warn.
        val legacyServerOnly = !legacyDefaultFrontend
        val warnings = if (target == DeployTarget.ALL && legacyServerOnly) {
            listOf(
                "Deploy target 'all' from $source overrides the legacy setting defaultFrontend=false, which means " +
                    "'server'. The target wins. Remove the legacy setting or set the target to 'server'."
            )
        } else {
            emptyList()
        }

        return DeployTargetResolution(target = target, warnings = warnings)
    }

}
