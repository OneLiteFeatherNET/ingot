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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

internal class TokenResolverTest {

    @TempDir
    lateinit var dir: Path

    private fun config(tokenFile: Path? = null, helper: String? = null) =
        CliConfig(
            url = "https://ingot.example",
            context = null,
            tokenFile = tokenFile,
            credentialHelper = helper,
            caCert = null,
            output = "text",
            timeoutSeconds = 30,
            insecure = false,
        )

    @Test
    fun `should read the token from INGOT_TOKEN before a token file`() {
        // given: a token in the environment and another in a file
        val file = dir.resolve("token").also { Files.writeString(it, "file:file-secret\n") }

        // when: the token is resolved
        val token = TokenResolver.resolve(config(tokenFile = file), env = mapOf("INGOT_TOKEN" to "env:env-secret"), warn = {})

        // then: the environment wins
        assertThat(token).isEqualTo("env:env-secret")
    }

    @Test
    fun `should read the first line of the token file, trimmed`() {
        // given: a token file with a trailing newline and a second line
        val file = dir.resolve("token").also { Files.writeString(it, "  ci:ci-secret  \nignored\n") }

        // when: the token is resolved
        val token = TokenResolver.resolve(config(tokenFile = file), env = emptyMap(), warn = {})

        // then: only the first line is used
        assertThat(token).isEqualTo("ci:ci-secret")
    }

    @Test
    fun `should warn when the token file is readable by others`() {
        // given: a token file readable by the group and others
        val file = dir.resolve("token").also {
            Files.writeString(it, "ci:ci-secret\n")
            Files.setPosixFilePermissions(it, PosixFilePermissions.fromString("rw-r--r--"))
        }
        val warnings = mutableListOf<String>()

        // when: the token is resolved
        TokenResolver.resolve(config(tokenFile = file), env = emptyMap(), warn = { warnings.add(it) })

        // then: a warning names the file and not the secret
        assertThat(warnings).hasSize(1)
        assertThat(warnings.single()).contains("token").doesNotContain("ci-secret")
    }

    @Test
    fun `should not warn for an owner-only token file`() {
        // given: a token file that only the owner can read
        val file = dir.resolve("token").also {
            Files.writeString(it, "ci:ci-secret\n")
            Files.setPosixFilePermissions(it, PosixFilePermissions.fromString("rw-------"))
        }
        val warnings = mutableListOf<String>()

        // when: the token is resolved
        TokenResolver.resolve(config(tokenFile = file), env = emptyMap(), warn = { warnings.add(it) })

        // then: no warning is given
        assertThat(warnings).isEmpty()
    }

    @Test
    fun `should refuse to start without any token source and name the variables`() {
        // given: no token source
        // when: the token is resolved
        // then: the error names the sources
        assertThatThrownBy { TokenResolver.resolve(config(), env = emptyMap(), warn = {}) }
            .isInstanceOf(ConfigException::class.java)
            .hasMessageContaining("INGOT_TOKEN")
    }

    @Test
    fun `should run a credentials helper without a shell and use its first line`() {
        // given: a helper program that prints a token
        val helper = dir.resolve("helper.sh").also {
            Files.writeString(it, "#!/bin/sh\necho helper:helper-secret\n")
            Files.setPosixFilePermissions(it, PosixFilePermissions.fromString("rwx------"))
        }

        // when: the token is resolved through the helper
        val token = TokenResolver.resolve(config(helper = helper.toString()), env = emptyMap(), warn = {})

        // then: the helper's first line is the token
        assertThat(token).isEqualTo("helper:helper-secret")
    }

    @Test
    fun `should refuse a token without a name separator and not echo it`() {
        // given: a token file with a value that lacks the name separator
        val file = dir.resolve("token").also { Files.writeString(it, "no-separator-secret\n") }

        // when / then: the error does not contain the value
        assertThatThrownBy { TokenResolver.resolve(config(tokenFile = file), env = emptyMap(), warn = {}) }
            .isInstanceOf(ConfigException::class.java)
            .hasMessageNotContaining("no-separator-secret")
    }

}
