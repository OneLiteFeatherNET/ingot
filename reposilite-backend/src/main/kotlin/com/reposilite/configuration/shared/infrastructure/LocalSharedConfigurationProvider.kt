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

package com.reposilite.configuration.shared.infrastructure

import com.reposilite.configuration.shared.SharedConfigurationProvider
import com.reposilite.journalist.Journalist
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

const val SHARED_CONFIGURATION_FILE = "configuration.shared.json"

/**
 * @param reloadEnabled when on, the file is watched: a change is detected by the SHA-256 of its content, not by its modification
 * time, and the file is read-only for the API, see [com.reposilite.configuration.shared.SharedConfigurationReload]. When off, the
 * file is read once at start and written on every save, as it always was.
 */
class LocalSharedConfigurationProvider(
    val journalist: Journalist,
    val workingDirectory: Path,
    val configurationFile: Path,
    val reloadEnabled: Boolean = false,
) : SharedConfigurationProvider {

    /**
     * The digest of the content handed to the loader last. It is recorded in [fetchConfiguration], so a document that changes
     * between the check and the load is recorded with the content that was really applied.
     */
    @Volatile
    private var loadedDigest: String? = null

    override fun updateConfiguration(content: String) {
        workingDirectory.resolve(configurationFile).writeText(content)
    }

    override fun fetchConfiguration(): String {
        val content = readConfiguration()
        if (reloadEnabled) {
            loadedDigest = digest(content)
        }

        return content
    }

    override fun isUpdateRequired(): Boolean =
        reloadEnabled && digest(readConfiguration()) != loadedDigest

    override fun isMutable(): Boolean =
        false

    override fun isExternallyManaged(): Boolean =
        reloadEnabled

    private fun readConfiguration(): String =
        workingDirectory.resolve(configurationFile)
            .takeIf { it.exists() }
            ?.readText()
            ?: ""

    private fun digest(content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }

    override fun name(): String =
        "local file-system: $configurationFile"

}
