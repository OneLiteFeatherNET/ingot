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
 * The setting carries a value that is neither `true` nor `false`, or `false` on a target that must run the jobs. The
 * message names the source and the value.
 */
class BackgroundJobsSettingException(message: String) : IllegalArgumentException(message)

/**
 * Resolves whether this server runs the singleton background jobs. The function is pure: every source is passed in as a
 * value, so it reads neither the environment nor the JVM properties.
 *
 * Precedence: the command line option, the system property, the environment variable, and then the default `true`. A
 * blank value counts as not set. The setting has no `REPOSILITE_` spelling.
 *
 * `false` is only valid on target `server`, where the worker takes over the jobs. On `all` and `worker` it is refused,
 * and any value other than `true` or `false` is refused on every target.
 */
object BackgroundJobsSetting {

    const val OPTION = "--background-jobs"
    const val PROPERTY = "ingot.background-jobs"
    const val ENVIRONMENT_VARIABLE = "INGOT_BACKGROUND_JOBS"

    /**
     * Returns `true` when the background jobs run on this instance. Throws [BackgroundJobsSettingException] when the set
     * value is invalid or when `false` is combined with a target other than `server`.
     */
    fun resolve(
        target: DeployTarget,
        commandLine: String?,
        properties: Map<String, String>,
        environment: Map<String, String>,
    ): Boolean {
        val setSource = sequenceOf(
            OPTION to commandLine,
            PROPERTY to properties[PROPERTY],
            ENVIRONMENT_VARIABLE to environment[ENVIRONMENT_VARIABLE],
        )
            .mapNotNull { (source, value) -> value?.takeUnless { it.isBlank() }?.let { source to it } }
            .firstOrNull()
            ?: return true

        val (source, value) = setSource
        val enabled = when (value) {
            "true" -> true
            "false" -> false
            else -> throw BackgroundJobsSettingException(
                "Invalid background jobs value '$value' from $source. Valid values: true, false."
            )
        }

        if (!enabled && target != DeployTarget.SERVER) {
            throw BackgroundJobsSettingException("$source=false is only valid on target server.")
        }

        return enabled
    }

}
