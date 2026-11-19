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

import ingot.plugin.IngotBaselineRepository.Credentials
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class IngotBaselineRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun lookup(values: Map<String, String>): (String) -> String? = { values[it] }

    private val none = lookup(emptyMap())

    @Test
    fun credentialsComeFromTheEnvironmentVariablesOfTheReleaseWorkflow() {
        val credentials = IngotBaselineRepository.credentials(
            environment = lookup(mapOf("ONELITEFEATHER_MAVEN_USERNAME" to "ci", "ONELITEFEATHER_MAVEN_PASSWORD" to "secret")),
            property = none,
        )

        assertThat(credentials)
            .describedAs("the two secrets that release-please.yml forwards are read under the same names")
            .isEqualTo(Credentials("ci", "secret"))
    }

    @Test
    fun credentialsFallBackToTheGradlePropertiesOfThePublishRepository() {
        val credentials = IngotBaselineRepository.credentials(
            environment = none,
            property = lookup(mapOf("mavenUser" to "local", "mavenPassword" to "secret")),
        )

        assertThat(credentials)
            .describedAs("mavenUser and mavenPassword are the properties the publish repository of the root build reads")
            .isEqualTo(Credentials("local", "secret"))
    }

    @Test
    fun environmentVariableWinsOverGradleProperty() {
        val credentials = IngotBaselineRepository.credentials(
            environment = lookup(mapOf("ONELITEFEATHER_MAVEN_USERNAME" to "ci", "ONELITEFEATHER_MAVEN_PASSWORD" to "env")),
            property = lookup(mapOf("mavenUser" to "local", "mavenPassword" to "property")),
        )

        assertThat(credentials)
            .describedAs("the environment comes first, as in the publish repository of the root build")
            .isEqualTo(Credentials("ci", "env"))
    }

    @Test
    fun blankValuesCountAsMissing() {
        // gradle.properties of the repository declares mavenUser with an empty value.
        val credentials = IngotBaselineRepository.credentials(
            environment = lookup(mapOf("ONELITEFEATHER_MAVEN_USERNAME" to " ")),
            property = lookup(mapOf("mavenUser" to "", "mavenPassword" to "")),
        )

        assertThat(credentials)
            .describedAs("an empty or blank value is no credential, so a checkout without secrets resolves nothing")
            .isNull()
    }

    @Test
    fun usernameWithoutPasswordIsNoCredential() {
        val credentials = IngotBaselineRepository.credentials(
            environment = lookup(mapOf("ONELITEFEATHER_MAVEN_USERNAME" to "ci")),
            property = none,
        )

        assertThat(credentials)
            .describedAs("half a credential is treated as none, so the report skips instead of failing with 401")
            .isNull()
    }

    @Test
    fun missingCredentialsGiveSkipReasonNamingWhatToSet() {
        val reason = IngotBaselineRepository.skipReason(null)

        assertThat(reason)
            .describedAs("the skip message tells a fork or a local run which names to set")
            .isNotNull()
            .contains("https://repo.onelitefeather.dev/onelitefeather-releases")
            .contains("ONELITEFEATHER_MAVEN_USERNAME")
            .contains("ONELITEFEATHER_MAVEN_PASSWORD")
            .contains("mavenUser")
            .contains("mavenPassword")
    }

    @Test
    fun presentCredentialsGiveNoSkipReason() {
        assertThat(IngotBaselineRepository.skipReason(Credentials("ci", "secret")))
            .describedAs("with credentials the report runs")
            .isNull()
    }

    @Test
    fun credentialsDoNotPrintThePassword() {
        assertThat(Credentials("ci", "secret").toString())
            .describedAs("a logged credential must not leak the password")
            .doesNotContain("secret")
    }

    @Test
    fun registeredRepositoryPointsAtTheReleasesRepositoryWithTheCredentials() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()

        IngotBaselineRepository.register(project.repositories, Credentials("ci", "secret"))

        val repository = project.repositories.findByName(IngotBaselineRepository.NAME) as MavenArtifactRepository?
        assertThat(repository).describedAs("the baseline repository is registered under its name").isNotNull()
        assertThat(repository!!.url.toString())
            .describedAs("the releases repository that release-please.yml publishes to")
            .isEqualTo("https://repo.onelitefeather.dev/onelitefeather-releases")
        assertThat(repository.credentials.username).describedAs("username").isEqualTo("ci")
        assertThat(repository.credentials.password).describedAs("password").isEqualTo("secret")
    }
}
