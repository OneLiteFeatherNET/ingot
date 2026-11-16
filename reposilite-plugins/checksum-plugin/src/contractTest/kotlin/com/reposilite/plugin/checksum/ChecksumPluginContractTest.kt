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

package com.reposilite.plugin.checksum

import com.reposilite.plugin.contract.PluginContractServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/**
 * The contract of the checksum plugin: a fresh server that loads the shipped jar of this module answers the checksum of a
 * deployed artifact. The jar path comes from the `contractTest` task, which builds the shadow jar first.
 */
internal class ChecksumPluginContractTest {

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `should answer the sha256 checksum of a deployed artifact`() {
        // given: a server with the shipped checksum plugin, and an artifact deployed without a checksum file
        val plugin = checkNotNull(System.getProperty(PLUGIN_JAR_PROPERTY)) { "$PLUGIN_JAR_PROPERTY is not set, run the contractTest task" }
        val content = "contract-artifact"

        PluginContractServer(workingDirectory, listOf(Path.of(plugin))).start().use { server ->
            assertThat(server.deploy("releases/com/reposilite/contract/contract-1.0.jar", content).statusCode())
                .describedAs("status of the deployment of the artifact")
                .isEqualTo(200)

            // when: the sha256 checksum of the artifact is requested
            val response = server.get("releases/com/reposilite/contract/contract-1.0.jar.sha256")

            // then: the plugin has generated the digest of the artifact
            assertThat(response.statusCode())
                .describedAs("status of the sha256 request, expected 200 from the checksum plugin")
                .isEqualTo(200)
            assertThat(response.body().trim())
                .describedAs("sha256 served for contract-1.0.jar")
                .isEqualTo(sha256Hex(content))
        }
    }

    private fun sha256Hex(content: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.toByteArray()))

    private companion object {
        const val PLUGIN_JAR_PROPERTY = "ingot.contract.plugin"
    }

}
