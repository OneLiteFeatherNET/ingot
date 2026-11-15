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
package net.onelitefeather.ingot.cli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

object TokenResolver {

    /**
     * The token as `name:secret`, from `INGOT_TOKEN`, then the token file, then the credentials helper. The helper is a
     * program run without arguments and without a shell. Messages never carry the value of the token.
     */
    fun resolve(
        config: CliConfig,
        env: Map<String, String>,
        warn: (String) -> Unit,
        runHelper: (String) -> String = ::runCredentialHelper,
    ): String {
        val token = env["INGOT_TOKEN"]?.takeIf { it.isNotBlank() }
            ?: config.tokenFile?.let { readTokenFile(it, warn) }
            ?: config.credentialHelper?.let { runHelper(it) }
            ?: throw ConfigException("No token: set INGOT_TOKEN, INGOT_TOKEN_FILE or a credentials helper")
        return token.trim().also { checkShape(it) }
    }

    private fun readTokenFile(file: Path, warn: (String) -> Unit): String {
        val permissions = Files.getPosixFilePermissions(file)
        if (permissions.any { it in OPEN_TO_OTHERS }) {
            warn("The token file $file is readable by other users. Restrict it to its owner, for example with chmod 600.")
        }
        return Files.readAllLines(file).firstOrNull() ?: throw ConfigException("The token file $file is empty")
    }

    private fun runCredentialHelper(helper: String): String {
        val process = ProcessBuilder(helper).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val line = process.inputReader().readLine()
        if (process.waitFor() != 0 || line.isNullOrBlank()) {
            throw ConfigException("The credentials helper $helper did not print a token")
        }
        return line
    }

    private fun checkShape(token: String) {
        if (!token.contains(':') || token.substringBefore(':').isEmpty()) {
            throw ConfigException("The token must have the form name:secret")
        }
    }

    private val OPEN_TO_OTHERS = setOf(
        PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE,
    )

}
