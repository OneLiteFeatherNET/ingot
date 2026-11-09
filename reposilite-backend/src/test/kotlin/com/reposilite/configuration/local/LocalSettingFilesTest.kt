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

package com.reposilite.configuration.local

import com.reposilite.journalist.Channel
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.backend.InMemoryLogger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path

/**
 * A `_FILE` variable or a `.file` property supplies the value of a local setting. Every test writes its files into its own
 * temporary directory and reads the environment and the properties from maps, so the process environment is never touched.
 */
internal class LocalSettingFilesTest {

    @TempDir
    lateinit var tempDir: Path

    private fun secretFile(name: String, content: String): Path =
        tempDir.resolve(name).also { Files.writeString(it, content, UTF_8) }

    private fun secretFileBytes(name: String, content: ByteArray): Path =
        tempDir.resolve(name).also { Files.write(it, content) }

    private fun missingFile(name: String): Path = tempDir.resolve(name)

    /**
     * Resolves the environment and the properties the way the factory does, then applies them to a default configuration.
     */
    private fun load(
        environment: Map<String, String> = emptyMap(),
        properties: Map<String, String> = emptyMap(),
        journalist: Journalist = InMemoryLogger(),
    ): LocalConfiguration {
        val configuration = LocalConfiguration()
        val resolved = LocalConfigurationFactory.getEnvironmentVariables(
            environment = environment,
            workingDirectory = tempDir,
            journalist = journalist,
        ) + LocalConfigurationFactory.getProperties(
            properties = properties,
            workingDirectory = tempDir,
            journalist = journalist,
        )
        LocalConfigurationFactory.applyCustomProperties(
            journalist = journalist,
            localConfiguration = configuration,
            properties = resolved,
            errorOutput = PrintStream(ByteArrayOutputStream(), true, UTF_8),
        )
        return configuration
    }

    private fun messagesOf(journalist: Journalist, channel: Channel): List<String> =
        (journalist as InMemoryLogger).messages
            .filter { it.key == channel }
            .map { it.value }

    @Test
    fun `a file variable supplies the value of its setting`() {
        // given: a secret file holding the key password followed by a line break
        val secret = secretFile("key-password", "s3cret\n")

        // when: the file variable names the file
        val configuration = load(environment = mapOf("INGOT_LOCAL_KEYPASSWORD_FILE" to secret.toString()))

        // then: the setting has the content of the file
        assertThat(configuration.keyPassword.get()).describedAs("keyPassword read from file").isEqualTo("s3cret")
    }

    @Test
    fun `the ingot file spelling beats the legacy reposilite file spelling`() {
        // given: two files, one per spelling
        val ingotFile = secretFile("ingot-port", "8181")
        val legacyFile = secretFile("legacy-port", "8080")

        // when: both spellings name a file
        val configuration = load(
            environment = mapOf(
                "INGOT_LOCAL_PORT_FILE" to ingotFile.toString(),
                "REPOSILITE_LOCAL_PORT_FILE" to legacyFile.toString(),
            )
        )

        // then: the ingot spelling wins
        assertThat(configuration.port.get()).describedAs("port with both file spellings").isEqualTo(8181)
    }

    @Test
    fun `the legacy reposilite file spelling is read when no ingot spelling is set`() {
        // given: a file named by the legacy spelling only
        val legacyFile = secretFile("legacy-port", "8181")

        // when: only the legacy file variable is set
        val configuration = load(environment = mapOf("REPOSILITE_LOCAL_PORT_FILE" to legacyFile.toString()))

        // then: the file is read
        assertThat(configuration.port.get()).describedAs("port from legacy file spelling").isEqualTo(8181)
    }

    @Test
    fun `a trailing line feed is removed from the file value`() {
        // given: a file whose content ends with a line feed
        val secret = secretFile("key-password", "s3cret\n")

        // when: the file is read
        val configuration = load(environment = mapOf("INGOT_LOCAL_KEYPASSWORD_FILE" to secret.toString()))

        // then: the line feed is not part of the value
        assertThat(configuration.keyPassword.get()).describedAs("keyPassword after LF").isEqualTo("s3cret")
    }

    @Test
    fun `a trailing carriage return and line feed are removed from the file value`() {
        // given: a file written on Windows, ending with a carriage return and a line feed
        val secret = secretFile("key-password", "s3cret\r\n")

        // when: the file is read
        val configuration = load(environment = mapOf("INGOT_LOCAL_KEYPASSWORD_FILE" to secret.toString()))

        // then: both characters are removed
        assertThat(configuration.keyPassword.get()).describedAs("keyPassword after CRLF").isEqualTo("s3cret")
    }

    @Test
    fun `inner and leading spaces of the file value are kept`() {
        // given: a file with spaces inside and in front of the value
        val secret = secretFile("key-password", " a b \n")

        // when: the file is read
        val configuration = load(environment = mapOf("INGOT_LOCAL_KEYPASSWORD_FILE" to secret.toString()))

        // then: only the line feed is removed
        assertThat(configuration.keyPassword.get()).describedAs("keyPassword with spaces").isEqualTo(" a b ")
    }

    @Test
    fun `an empty file gives the empty string instead of the default`() {
        // given: an empty file for a setting whose default is not empty
        val empty = secretFile("hostname", "")

        // when: the file is read
        val configuration = load(environment = mapOf("INGOT_LOCAL_HOSTNAME_FILE" to empty.toString()))

        // then: the setting is the empty string
        assertThat(configuration.hostname.get()).describedAs("hostname from empty file").isEmpty()
    }

    @Test
    fun `the plain variable wins over the file variable of the same source and one warning names both`() {
        // given: a plain port, and a file variable naming a file that would give another port
        val journalist = InMemoryLogger()
        val otherPort = secretFile("other-port", "8080")

        // when: both variables are set in the environment
        val configuration = load(
            environment = mapOf(
                "INGOT_LOCAL_PORT" to "8181",
                "INGOT_LOCAL_PORT_FILE" to otherPort.toString(),
            ),
            journalist = journalist,
        )

        // then: the plain value is used
        assertThat(configuration.port.get()).describedAs("port with plain and file variable").isEqualTo(8181)

        // and: exactly one warning names both variables
        val warnings = messagesOf(journalist, Channel.WARN)
        assertThat(warnings).describedAs("warnings for plain and file variable").hasSize(1)
        assertThat(warnings.single())
            .describedAs("warning text")
            .contains("INGOT_LOCAL_PORT_FILE")
            .contains("INGOT_LOCAL_PORT")
    }

    @Test
    fun `a missing file is not read when the plain variable of the same source is set`() {
        // given: a plain port and a file variable naming a file that does not exist
        val journalist = InMemoryLogger()
        val missing = missingFile("does-not-exist")

        // when: the environment is loaded
        val configuration = load(
            environment = mapOf(
                "INGOT_LOCAL_PORT" to "8181",
                "INGOT_LOCAL_PORT_FILE" to missing.toString(),
            ),
            journalist = journalist,
        )

        // then: the start goes on with the plain value and no error is logged
        assertThat(configuration.port.get()).describedAs("port with an unread missing file").isEqualTo(8181)
        assertThat(messagesOf(journalist, Channel.ERROR)).describedAs("error lines for an unread missing file").isEmpty()
    }

    @Test
    fun `an environment file variable loses to a system property of the same setting`() {
        // given: a file that would give port 8181, and a system property that gives port 8282
        val file = secretFile("port", "8181")

        // when: the file variable and the property are both set
        val configuration = load(
            environment = mapOf("INGOT_LOCAL_PORT_FILE" to file.toString()),
            properties = mapOf("ingot.local.port" to "8282"),
        )

        // then: the system property wins
        assertThat(configuration.port.get()).describedAs("port with file variable and property").isEqualTo(8282)
    }

    @Test
    fun `the ingot system property file form is read`() {
        // given: a secret file for the key password
        val secret = secretFile("key-password", "s3cret")

        // when: the property names the file
        val configuration = load(properties = mapOf("ingot.local.keyPassword.file" to secret.toString()))

        // then: the setting has the content of the file
        assertThat(configuration.keyPassword.get()).describedAs("keyPassword from ingot property file").isEqualTo("s3cret")
    }

    @Test
    fun `the legacy reposilite system property file form is read`() {
        // given: a file holding the port
        val file = secretFile("port", "8181")

        // when: the legacy property names the file
        val configuration = load(properties = mapOf("reposilite.local.port.file" to file.toString()))

        // then: the setting has the content of the file
        assertThat(configuration.port.get()).describedAs("port from reposilite property file").isEqualTo(8181)
    }

    @Test
    fun `a file variable of an unknown key is warned about and its file is not read`() {
        // given: a file variable for a key that is not a local setting, naming a file that does not exist
        val journalist = InMemoryLogger()
        val missing = missingFile("does-not-exist")

        // when: the environment is loaded
        assertThatCode {
            load(
                environment = mapOf("INGOT_LOCAL_NOSUCHKEY_FILE" to missing.toString()),
                journalist = journalist,
            )
        }.describedAs("loading must not read the file of an unknown key").doesNotThrowAnyException()

        // then: one warning names the variable
        val warnings = messagesOf(journalist, Channel.WARN)
        assertThat(warnings).describedAs("warnings for unknown file variable").hasSize(1)
        assertThat(warnings.single()).describedAs("warning text").contains("NOSUCHKEY_FILE")
    }

    @Test
    fun `a missing file stops the start with a message naming the variable and the path`() {
        // given: a file variable naming a file that does not exist
        val missing = missingFile("key-password")

        // when: the environment is loaded
        assertThatThrownBy {
            load(environment = mapOf("INGOT_LOCAL_KEYPASSWORD_FILE" to missing.toString()))
        }
            // then: the exception names the variable and the path
            .describedAs("missing file must stop the start")
            .isInstanceOf(LocalConfigurationFileException::class.java)
            .hasMessageContaining("INGOT_LOCAL_KEYPASSWORD_FILE")
            .hasMessageContaining(missing.toString())
    }

    @Test
    fun `a directory named by a file variable stops the start with a message naming the legacy variable`() {
        // when: the legacy file variable names a directory
        assertThatThrownBy {
            load(environment = mapOf("REPOSILITE_LOCAL_KEYPASSWORD_FILE" to tempDir.toString()))
        }
            // then: the exception names the legacy variable and the path
            .describedAs("a directory is not a readable file")
            .isInstanceOf(LocalConfigurationFileException::class.java)
            .hasMessageContaining("REPOSILITE_LOCAL_KEYPASSWORD_FILE")
            .hasMessageContaining(tempDir.toString())
    }

    @Test
    fun `a file that is not valid utf-8 stops the start without printing its content`() {
        // given: a file with readable text followed by a byte that is not valid UTF-8
        val invalid = secretFileBytes("key-password", "s3cret".toByteArray(UTF_8) + byteArrayOf(0xFF.toByte()))

        // when: the environment is loaded
        assertThatThrownBy {
            load(environment = mapOf("INGOT_LOCAL_KEYPASSWORD_FILE" to invalid.toString()))
        }
            // then: the exception names the variable and the path, and never the content
            .describedAs("invalid UTF-8 must stop the start")
            .isInstanceOf(LocalConfigurationFileException::class.java)
            .hasMessageContaining("INGOT_LOCAL_KEYPASSWORD_FILE")
            .hasMessageContaining(invalid.toString())
            .hasMessageNotContaining("s3cret")
    }

    @Test
    fun `the start log names the variable of a file value and never its content`() {
        // given: a secret file for the key password
        val journalist = InMemoryLogger()
        val secret = secretFile("key-password", "s3cret")

        // when: the file is read
        load(
            environment = mapOf("INGOT_LOCAL_KEYPASSWORD_FILE" to secret.toString()),
            journalist = journalist,
        )

        // then: no log line carries the full content of the file
        val lines = (journalist as InMemoryLogger).messages.map { it.value }
        assertThat(lines).describedAs("log lines of the start").noneMatch { it.contains("s3cret") }
        assertThat(lines).describedAs("log lines of the start").anyMatch { it.contains("KEYPASSWORD") }
    }

    @Test
    fun `a file value is read once at the start and a later change does not reach the configuration`() {
        // given: a secret file read at the start
        val secret = secretFile("key-password", "s3cret")
        val configuration = load(environment = mapOf("INGOT_LOCAL_KEYPASSWORD_FILE" to secret.toString()))

        // when: the file changes after the start
        Files.writeString(secret, "rotated")

        // then: the configuration keeps the value read at the start
        assertThat(configuration.keyPassword.get()).describedAs("keyPassword after the file changed").isEqualTo("s3cret")
    }

}
