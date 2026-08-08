/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.image

import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The upgrade, rehearsed against the real upstream image.
 *
 * [ServerImageContractTest] proves the contract holds on an empty directory. This proves
 * the thing operators actually care about: an existing Reposilite instance, with a
 * database, a token and published artifacts, keeps working when it is pointed at Ingot.
 *
 * It ends by handing the directory back to upstream, because "no risk" also means the
 * rollback works.
 *
 * This is the test that found the regression it was written for. A Shadow 9 upgrade
 * stopped the fat jar from registering tinylog's writers, so the image built cleanly,
 * started cleanly, served artifacts, and wrote nothing at all to /var/log/reposilite.
 */
class UpstreamMigrationTest {

    @Test
    @DisplayName("an existing Reposilite instance keeps working, and can go back")
    fun `existing instance survives the switch and the rollback`(@TempDir directory: Path) {
        DataDirectory(directory, ImageUnderTest.server).use { data ->
            val secret = createStateWithUpstream(data)

            takeOverWithIngot(data, secret)

            handBackToUpstream(data, secret)
        }
    }

    /**
     * Runs the upstream image and leaves behind what a real instance has: an initialised
     * database, a persistent access token, and a published artifact.
     *
     * The bootstrap token comes from `--token`, which creates a temporary one that is never
     * written to the database. It exists only to authenticate the call that creates the
     * persistent token, and that persistent token is the point: it has to survive the
     * switch, which a temporary one could not prove.
     */
    private fun createStateWithUpstream(data: DataDirectory): String {
        serverContainer(
            image = ImageUnderTest.upstream,
            data = data,
            environment = mapOf("REPOSILITE_OPTS" to "--token $BOOTSTRAP_NAME:$BOOTSTRAP_SECRET"),
        ).use { upstream ->
            upstream.start()
            val url = upstream.baseUrl()

            val created = Http.putJson(
                url = "$url/api/tokens/$TOKEN_NAME",
                json = """
                    {
                      "type": "PERSISTENT",
                      "secretType": "RAW",
                      "secret": "$TOKEN_SECRET",
                      "permissions": ["m"],
                      "routes": [{ "path": "/", "permissions": ["r", "w"] }]
                    }
                """.trimIndent(),
                credentials = BOOTSTRAP_NAME to BOOTSTRAP_SECRET,
            )
            assertThat(created.isSuccessful)
                .describedAs("upstream refused to create the token: %s", created.body)
                .isTrue()

            val published = Http.put(
                url = "$url/$ARTIFACT_V1",
                body = ARTIFACT_V1_CONTENT,
                credentials = TOKEN_NAME to TOKEN_SECRET,
            )
            assertThat(published.isSuccessful)
                .describedAs("upstream refused the upload: %s", published.body)
                .isTrue()

            assertThat(Http.get("$url/$ARTIFACT_V1").body).isEqualTo(ARTIFACT_V1_CONTENT)
        }

        return data.read("configuration.cdn")
    }

    /** Points our image at that directory and checks what survived. */
    private fun takeOverWithIngot(data: DataDirectory, configurationBefore: String) {
        serverContainer(ImageUnderTest.server, data).use { ingot ->
            ingot.start()
            val url = ingot.baseUrl()

            assertThat(Http.get("$url/$ARTIFACT_V1").body)
                .describedAs("the artifact published under upstream did not survive the switch")
                .isEqualTo(ARTIFACT_V1_CONTENT)

            val republished = Http.put(
                url = "$url/$ARTIFACT_V2",
                body = ARTIFACT_V2_CONTENT,
                credentials = TOKEN_NAME to TOKEN_SECRET,
            )
            assertThat(republished.isSuccessful)
                .describedAs("the token from the upstream database was rejected")
                .isTrue()

            // Both products rewrite this file on startup, so the header comments end up
            // branded differently and that is expected. A changed setting is not: it would
            // silently reconfigure an instance that only meant to change its image.
            assertThat(settingsOf(data.read("configuration.cdn")))
                .describedAs("our image rewrote configured values, not just its own comments")
                .isEqualTo(settingsOf(configurationBefore))

            // /var/log/reposilite is part of the contract: log shippers and volume mounts
            // point at it, and an image that stops writing there takes them with it.
            val logDirectory = ingot.execInContainer("ls", "/var/log/reposilite").stdout.trim()
            assertThat(logDirectory.lines().filter { it.isNotBlank() })
                .describedAs("upstream writes latest.log and a dated file")
                .hasSizeGreaterThanOrEqualTo(2)
            assertThat(ingot.logs)
                .describedAs("the logging backend reported a missing writer")
                .doesNotContain("LOGGER ERROR")
        }
    }

    /** Gives the directory back, because an upgrade nobody can undo is not risk free. */
    private fun handBackToUpstream(data: DataDirectory, configuration: String) {
        serverContainer(ImageUnderTest.upstream, data).use { upstream ->
            upstream.start()
            val url = upstream.baseUrl()

            assertThat(Http.get("$url/$ARTIFACT_V2").body)
                .describedAs("upstream cannot read what our image wrote")
                .isEqualTo(ARTIFACT_V2_CONTENT)

            val details = Http.get(
                url = "$url/api/maven/details/releases/com/example/demo",
                credentials = TOKEN_NAME to TOKEN_SECRET,
            )
            assertThat(details.isSuccessful)
                .describedAs("upstream rejected the token after the round trip")
                .isTrue()
        }
    }

    /** The configuration without its comments, which are branded and expected to differ. */
    private fun settingsOf(configuration: String): List<String> =
        configuration.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    private companion object {
        const val BOOTSTRAP_NAME = "bootstrap"
        const val BOOTSTRAP_SECRET = "bootstrap-secret-for-the-rehearsal"
        const val TOKEN_NAME = "ci-user"
        const val TOKEN_SECRET = "token-secret-that-must-survive"

        const val ARTIFACT_V1 = "releases/com/example/demo/1.0.0/demo-1.0.0.jar"
        const val ARTIFACT_V1_CONTENT = "published while running upstream"
        const val ARTIFACT_V2 = "releases/com/example/demo/2.0.0/demo-2.0.0.jar"
        const val ARTIFACT_V2_CONTENT = "published after the switch to ingot"
    }
}
