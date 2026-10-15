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
 * An operator who rolls back to upstream keeps using upstream for a while, and upstream keeps writing to the legacy tables
 * the identity migration has already read. Two things then happen on the next switch to Ingot:
 *
 * - upstream creates a token that the identity migration has never seen, and
 * - a token that Ingot created in the meantime holds the identifier upstream hands out next, because the identity tables
 *   and the legacy tables count their identifiers separately.
 *
 * Both tokens must still authenticate. Before the ledger, the first one was silently unusable and the second one was a
 * clash.
 */
class LateLegacyTokenTest {

    @Test
    @DisplayName("tokens created on the source after a rollback keep working after the next switch to Ingot")
    fun `tokens created on the source after a rollback keep working after the next switch`(@TempDir directory: Path) {
        DataDirectory(directory, ImageUnderTest.server).use { data ->
            createDatasetOnUpstream(data)

            val ingotToken = createTokenInIngot(data)

            createTokenOnUpstream(data)

            assertThat(ingotTokenAfterRollbackIsKnownToUpstream(data))
                .describedAs("the token made in Ingot is not visible to upstream, which is the documented downgrade caveat")
                .isFalse()

            switchAgainAndCheck(data, ingotToken)
        }
    }

    private fun createDatasetOnUpstream(data: DataDirectory) {
        serverContainer(
            image = ImageUnderTest.upstream,
            data = data,
            environment = mapOf("REPOSILITE_OPTS" to "--token $BOOTSTRAP_NAME:$BOOTSTRAP_SECRET"),
        ).use { upstream ->
            upstream.start()
            RealisticDataset.create(upstream.baseUrl(), BOOTSTRAP_NAME to BOOTSTRAP_SECRET)
        }
    }

    /** Ingot creates a token after the first switch. Its identifier is the next free one of the identity tables. */
    private fun createTokenInIngot(data: DataDirectory): Pair<String, String> {
        val credentials = INGOT_TOKEN to INGOT_SECRET
        serverContainer(ImageUnderTest.server, data).use { ingot ->
            ingot.start()
            val url = ingot.baseUrl()
            val manager = "ops-admin" to RealisticDataset.tokens.getValue("ops-admin")
            val response = Http.putJson(
                url = "$url/api/tokens/$INGOT_TOKEN",
                json = routeJson(INGOT_SECRET),
                credentials = manager,
            )
            assertThat(response.isSuccessful).describedAs("create a token in Ingot: %s", response.body).isTrue()
        }
        return credentials
    }

    /** Rolls back, and upstream creates a token of its own. The identity migration has not seen it. */
    private fun createTokenOnUpstream(data: DataDirectory) {
        serverContainer(
            image = ImageUnderTest.upstream,
            data = data,
            environment = mapOf("REPOSILITE_OPTS" to "--token $BOOTSTRAP_NAME:$BOOTSTRAP_SECRET"),
        ).use { upstream ->
            upstream.start()
            val response = Http.putJson(
                url = "${upstream.baseUrl()}/api/tokens/$LATE_TOKEN",
                json = routeJson(LATE_SECRET),
                credentials = BOOTSTRAP_NAME to BOOTSTRAP_SECRET,
            )
            assertThat(response.isSuccessful).describedAs("create a token on upstream: %s", response.body).isTrue()
        }
    }

    /**
     * Whether upstream, started on the directory once more, already lists the Ingot token. It must not: the token only
     * exists in the identity tables. Checked with the source's own listing, which is what an operator would see.
     */
    private fun ingotTokenAfterRollbackIsKnownToUpstream(data: DataDirectory): Boolean =
        serverContainer(
            image = ImageUnderTest.upstream,
            data = data,
            environment = mapOf("REPOSILITE_OPTS" to "--token $BOOTSTRAP_NAME:$BOOTSTRAP_SECRET"),
        ).use { upstream ->
            upstream.start()
            Http.get("${upstream.baseUrl()}/api/tokens", BOOTSTRAP_NAME to BOOTSTRAP_SECRET).body.contains("\"name\":\"$INGOT_TOKEN\"")
        }

    /** Second switch: both the late token and the Ingot token must still authenticate, each as its own machine principal. */
    private fun switchAgainAndCheck(data: DataDirectory, ingotToken: Pair<String, String>) {
        serverContainer(ImageUnderTest.server, data).use { ingot ->
            ingot.start()
            val url = ingot.baseUrl()

            val late = Http.put(
                url = "$url/releases/com/example/late/1.0.0/late-1.0.0.jar",
                body = "uploaded with the late token",
                credentials = LATE_TOKEN to LATE_SECRET,
            )
            assertThat(late.isSuccessful)
                .describedAs("the token created on the source after a rollback must authenticate after the next switch: %s", late.status)
                .isTrue()

            val ingotUpload = Http.put(
                url = "$url/releases/com/example/late/1.0.0/ingot-1.0.0.jar",
                body = "uploaded with the Ingot token",
                credentials = ingotToken,
            )
            assertThat(ingotUpload.isSuccessful)
                .describedAs("the token created in Ingot must still authenticate: %s", ingotUpload.status)
                .isTrue()

            val principals = Http.get("$url/api/identity/principals", "ops-admin" to RealisticDataset.tokens.getValue("ops-admin")).body
            listOf(LATE_TOKEN, INGOT_TOKEN).forEach { name ->
                assertThat(principals)
                    .describedAs("token %s is a machine principal", name)
                    .contains("\"kind\":\"MACHINE\",\"name\":\"$name\"")
            }
        }
    }

    private fun routeJson(secret: String): String =
        """{"type":"PERSISTENT","secretType":"RAW","secret":"$secret","permissions":[],"routes":[{"path":"/releases","permissions":["w"]}]}"""

    private companion object {
        const val BOOTSTRAP_NAME = "bootstrap"
        const val BOOTSTRAP_SECRET = "bootstrap-secret-for-the-late-token"
        const val INGOT_TOKEN = "ingot-token"
        const val INGOT_SECRET = "ingot-token-secret-for-the-late-token"
        const val LATE_TOKEN = "late-ci"
        const val LATE_SECRET = "late-ci-secret-for-the-late-token"
    }
}
