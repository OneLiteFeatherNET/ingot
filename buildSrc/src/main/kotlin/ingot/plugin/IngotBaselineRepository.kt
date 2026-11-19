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

package ingot.plugin

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

// The repository the Ingot plugin API report resolves its baseline release from (design D2 of extend-plugin-abi-gate). Releases are
// published there by release-please.yml, and the repository asks for credentials even to read. The credentials use the names of the
// publish repository in the root build: the secrets ONELITEFEATHER_MAVEN_USERNAME and ONELITEFEATHER_MAVEN_PASSWORD as environment
// variables, or the Gradle properties mavenUser and mavenPassword. Without them, in a fork or a local run, the report is skipped.
object IngotBaselineRepository {

    const val NAME = "IngotBaselineReleases"
    const val URL = "https://repo.onelitefeather.dev/onelitefeather-releases"
    const val GROUP = "net.onelitefeather.ingot"

    const val USERNAME_ENVIRONMENT = "ONELITEFEATHER_MAVEN_USERNAME"
    const val PASSWORD_ENVIRONMENT = "ONELITEFEATHER_MAVEN_PASSWORD"
    const val USERNAME_PROPERTY = "mavenUser"
    const val PASSWORD_PROPERTY = "mavenPassword"

    data class Credentials(val username: String, val password: String) {
        override fun toString(): String = "Credentials(username=$username, password=***)"
    }

    // The environment comes first, as in the publish repository. A blank value counts as missing, because gradle.properties declares
    // mavenUser with an empty value. Half a credential is none, so the report skips rather than failing on a 401.
    fun credentials(environment: (String) -> String?, property: (String) -> String?): Credentials? {
        fun read(environmentName: String, propertyName: String): String? =
            environment(environmentName)?.takeIf { it.isNotBlank() } ?: property(propertyName)?.takeIf { it.isNotBlank() }

        val username = read(USERNAME_ENVIRONMENT, USERNAME_PROPERTY) ?: return null
        val password = read(PASSWORD_ENVIRONMENT, PASSWORD_PROPERTY) ?: return null
        return Credentials(username, password)
    }

    fun skipReason(credentials: Credentials?): String? =
        if (credentials != null) null
        else "Skipping the Ingot plugin API report: no credentials for $URL, where the baseline release is resolved from. " +
            "Set the environment variables $USERNAME_ENVIRONMENT and $PASSWORD_ENVIRONMENT, " +
            "or the Gradle properties $USERNAME_PROPERTY and $PASSWORD_PROPERTY, to run it."

    // Serves only the Ingot group and only releases, so no other coordinate of the build is ever looked up there.
    fun register(repositories: RepositoryHandler, login: Credentials): MavenArtifactRepository =
        repositories.maven {
            name = NAME
            setUrl(URL)
            credentials {
                username = login.username
                password = login.password
            }
            mavenContent {
                releasesOnly()
                includeGroup(GROUP)
            }
        }
}
