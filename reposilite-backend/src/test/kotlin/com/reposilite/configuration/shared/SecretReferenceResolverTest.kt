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

package com.reposilite.configuration.shared

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The reference grammar and the allowlists of settings secret references. The environment is injected as a map and the files
 * live in a temporary directory, so no real variable is read and no fixed path is touched.
 */
internal class SecretReferenceResolverTest {

    @TempDir
    lateinit var directory: Path

    private val secrets: Path by lazy { directory.resolve("secrets") }

    private fun resolver(
        environment: Map<String, String> = emptyMap(),
        variables: Set<String> = emptySet(),
        directories: List<Path> = emptyList(),
    ) = SecretReferenceResolver(environment = environment, variables = variables, directories = directories)

    private fun secretFile(name: String, bytes: ByteArray): Path {
        Files.createDirectories(secrets)
        return Files.write(secrets.resolve(name), bytes)
    }

    private fun secretFile(name: String, content: String): Path =
        secretFile(name, content.toByteArray(Charsets.UTF_8))

    @Test
    fun `resolves an environment reference to the value of an allowed variable`() {
        // given: a variable that is allowed and set
        val resolver = resolver(environment = mapOf("S3_SECRET" to "s3cret"), variables = setOf("S3_SECRET"))

        // when: the reference is resolved
        val resolved = resolver.resolve("\${env:S3_SECRET}")

        // then: the value of the variable is the result
        assertThat(resolved).describedAs("resolved environment reference").isEqualTo("s3cret")
    }

    @Test
    fun `resolves a file reference to the content of a file in an allowed directory`() {
        // given: a secret file inside an allowed directory
        val file = secretFile("s3", "s3cret")
        val resolver = resolver(directories = listOf(secrets))

        // when: the reference is resolved
        val resolved = resolver.resolve("\${file:$file}")

        // then: the content of the file is the result
        assertThat(resolved).describedAs("resolved file reference").isEqualTo("s3cret")
    }

    @Test
    fun `resolves several references in one value`() {
        // given: an endpoint that embeds a variable and a file
        val file = secretFile("pass", "p")
        val resolver = resolver(
            environment = mapOf("S3_USER" to "u"),
            variables = setOf("S3_USER"),
            directories = listOf(secrets),
        )

        // when: the endpoint is resolved
        val resolved = resolver.resolve("https://\${env:S3_USER}:\${file:$file}@s3.example.test")

        // then: both references are replaced and the rest is kept
        assertThat(resolved).describedAs("endpoint with two references").isEqualTo("https://u:p@s3.example.test")
    }

    @Test
    fun `writes a doubled dollar brace as a literal reference without reading the variable`() {
        // given: a variable that is allowed and set, so a read would be visible in the result
        val resolver = resolver(environment = mapOf("S3_SECRET" to "s3cret"), variables = setOf("S3_SECRET"))

        // when: the escaped reference is resolved
        val resolved = resolver.resolve("\$\${env:S3_SECRET}")

        // then: the text stays a literal reference and the variable is not read
        assertThat(resolved).describedAs("escaped reference").isEqualTo("\${env:S3_SECRET}")
    }

    @Test
    fun `keeps other dollar brace text unchanged`() {
        // when: a value uses a dollar brace that is not a reference
        val resolved = resolver().resolve("\${HOME}")

        // then: the value stays as it is
        assertThat(resolved).describedAs("dollar brace text that is not a reference").isEqualTo("\${HOME}")
    }

    @Test
    fun `refuses an unclosed reference and does not echo the value`() {
        // given: a value whose reference is not closed
        val resolver = resolver(environment = mapOf("TOKEN_PART_123" to "x"), variables = setOf("TOKEN_PART_123"))

        // when, then: the value is refused and the message holds none of it
        assertThatThrownBy { resolver.resolve("prefix-\${env:TOKEN_PART_123") }
            .isInstanceOf(SecretReferenceException::class.java)
            .satisfies({ error -> assertThat(error.message).describedAs("message of an unclosed reference").doesNotContain("TOKEN_PART_123", "prefix") })
    }

    @Test
    fun `refuses an environment reference without a variable name`() {
        assertThatThrownBy { resolver().resolve("\${env:}") }
            .describedAs("empty variable name")
            .isInstanceOf(SecretReferenceException::class.java)
    }

    @Test
    fun `refuses an environment reference whose name contains whitespace and does not echo it`() {
        assertThatThrownBy { resolver(variables = setOf("A B")).resolve("\${env:A B}") }
            .isInstanceOf(SecretReferenceException::class.java)
            .satisfies({ error -> assertThat(error.message).describedAs("message of an invalid name").doesNotContain("A B") })
    }

    @Test
    fun `refuses a file reference without a path`() {
        assertThatThrownBy { resolver().resolve("\${file:}") }
            .describedAs("empty file path")
            .isInstanceOf(SecretReferenceException::class.java)
    }

    @Test
    fun `refuses a file reference whose path is not absolute`() {
        assertThatThrownBy { resolver(directories = listOf(secrets)).resolve("\${file:secrets/s3}") }
            .describedAs("relative file path")
            .isInstanceOf(SecretReferenceException::class.java)
    }

    @Test
    fun `refuses a variable that is not on the allowlist and names it without its value`() {
        // given: a variable that is set but not allowed
        val resolver = resolver(environment = mapOf("DB_PASSWORD" to "db-value-xyz"), variables = setOf("S3_SECRET"))

        // when, then: the reference is refused, named, and the value is not in the message
        assertThatThrownBy { resolver.resolve("\${env:DB_PASSWORD}") }
            .isInstanceOf(SecretReferenceException::class.java)
            .satisfies({ error ->
                assertThat(error.message).describedAs("message of a variable outside the allowlist")
                    .contains("DB_PASSWORD")
                    .doesNotContain("db-value-xyz")
            })
    }

    @Test
    fun `refuses an allowed variable that is not set and names it`() {
        assertThatThrownBy { resolver(variables = setOf("UNSET_VARIABLE")).resolve("\${env:UNSET_VARIABLE}") }
            .isInstanceOf(SecretReferenceException::class.java)
            .satisfies({ error -> assertThat(error.message).describedAs("message of an unset variable").contains("UNSET_VARIABLE") })
    }

    @Test
    fun `refuses a file outside the allowed directories and does not read it`() {
        // given: a secret file that is not inside the allowed directory
        val other = directory.resolve("other").also { Files.createDirectories(it) }.resolve("s3")
        Files.writeString(other, "outside-secret")
        val resolver = resolver(directories = listOf(secrets))

        // when, then: the file is refused and its content is not in the message
        assertThatThrownBy { resolver.resolve("\${file:$other}") }
            .isInstanceOf(SecretReferenceException::class.java)
            .satisfies({ error -> assertThat(error.message).describedAs("message of a file outside the allowlist").doesNotContain("outside-secret") })
    }

    @Test
    fun `refuses a path that leaves the allowed directory through dot dot`() {
        // given: a file next to the allowed directory, reached through it with dot dot
        Files.createDirectories(secrets)
        Files.writeString(directory.resolve("outside"), "outside-secret")
        val escaping = secrets.resolve("..").resolve("outside")
        val resolver = resolver(directories = listOf(secrets))

        // when, then: the canonical path is outside, so the reference is refused
        assertThatThrownBy { resolver.resolve("\${file:$escaping}") }
            .isInstanceOf(SecretReferenceException::class.java)
            .satisfies({ error -> assertThat(error.message).describedAs("message of an escaping path").doesNotContain("outside-secret") })
    }

    @Test
    fun `refuses a symbolic link inside the allowed directory that points outside it`() {
        // given: a link inside the allowed directory whose target is outside
        val target = directory.resolve("target").also { Files.writeString(it, "outside-secret") }
        Files.createDirectories(secrets)
        val link = Files.createSymbolicLink(secrets.resolve("link"), target)
        val resolver = resolver(directories = listOf(secrets))

        // when, then: the canonical target is outside the allowed directory, so the link is refused
        assertThatThrownBy { resolver.resolve("\${file:$link}") }
            .describedAs("symbolic link leaving the allowed directory")
            .isInstanceOf(SecretReferenceException::class.java)
    }

    @Test
    fun `refuses every reference when both allowlists are empty`() {
        // given: a secret file and a variable, and no allowlist at all
        val file = secretFile("s3", "s3cret")
        val resolver = resolver(environment = mapOf("S3_SECRET" to "s3cret"))

        // when, then: both kinds are refused
        assertThatThrownBy { resolver.resolve("\${env:S3_SECRET}") }
            .describedAs("environment reference with an empty allowlist")
            .isInstanceOf(SecretReferenceException::class.java)
        assertThatThrownBy { resolver.resolve("\${file:$file}") }
            .describedAs("file reference with an empty allowlist")
            .isInstanceOf(SecretReferenceException::class.java)
    }

    @Test
    fun `removes one trailing line feed from a file`() {
        val file = secretFile("lf", "s3cret\n")

        assertThat(resolver(directories = listOf(secrets)).resolve("\${file:$file}"))
            .describedAs("content with a trailing line feed")
            .isEqualTo("s3cret")
    }

    @Test
    fun `removes a trailing carriage return and line feed from a file`() {
        val file = secretFile("crlf", "s3cret\r\n")

        assertThat(resolver(directories = listOf(secrets)).resolve("\${file:$file}"))
            .describedAs("content with a trailing CRLF")
            .isEqualTo("s3cret")
    }

    @Test
    fun `resolves an empty file to the empty string`() {
        val file = secretFile("empty", "\n")

        assertThat(resolver(directories = listOf(secrets)).resolve("\${file:$file}"))
            .describedAs("file that holds only a line break")
            .isEmpty()
    }

    @Test
    fun `refuses a file that is not valid UTF-8 without its bytes`() {
        // given: a file whose readable part is followed by a byte that is not UTF-8
        val file = secretFile("binary", "secret-".toByteArray(Charsets.UTF_8) + byteArrayOf(0xFF.toByte()))

        // when, then: the file is refused and the readable bytes are not in the message
        assertThatThrownBy { resolver(directories = listOf(secrets)).resolve("\${file:$file}") }
            .isInstanceOf(SecretReferenceException::class.java)
            .satisfies({ error -> assertThat(error.message).describedAs("message of a file that is not UTF-8").doesNotContain("secret-") })
    }
}
