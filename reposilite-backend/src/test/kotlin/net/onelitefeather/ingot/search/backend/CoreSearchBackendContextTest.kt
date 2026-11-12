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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.backend

import com.reposilite.audit.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** The secrets a search backend context hands out and the redaction of what the backend shows (search backend registration, D4). */
internal class CoreSearchBackendContextTest {

    @TempDir
    lateinit var directory: Path

    private val environment = mutableMapOf<String, String>()
    private val lines = mutableListOf<String>()

    private fun context() = CoreSearchBackendContext(
        clock = MutableClock(Instant.parse("2026-10-16T10:00:00Z")),
        instanceId = "instance",
        backendId = "fake",
        environment = environment::get,
    ) { level, message -> lines.add("$level $message") }

    @Test
    fun `a secret handed out by secret is replaced in a message of the backend`() {
        environment["SEARCH_TOKEN"] = "s3cr3t-token"
        val context = context()
        context.secret("SEARCH_TOKEN")

        val redacted = context.redact("login refused for s3cr3t-token")

        assertThat(redacted).`as`("message with the secret").isEqualTo("login refused for ***")
    }

    @Test
    fun `every occurrence of a handed out secret is replaced`() {
        environment["SEARCH_TOKEN"] = "s3cr3t-token"
        val context = context()
        context.secret("SEARCH_TOKEN")

        val redacted = context.redact("s3cr3t-token then s3cr3t-token again")

        assertThat(redacted).`as`("message with two occurrences").isEqualTo("*** then *** again")
    }

    @Test
    fun `a secret that was never read from the context is not replaced`() {
        environment["SEARCH_TOKEN"] = "s3cr3t-token"
        val context = context()

        val redacted = context.redact("the value s3cr3t-token is only in the environment")

        assertThat(redacted).`as`("message with a secret that was never handed out")
            .isEqualTo("the value s3cr3t-token is only in the environment")
    }

    @Test
    fun `a missing secret causes no replacement`() {
        val context = context()
        val secret = context.secret("MISSING_TOKEN")

        val redacted = context.redact("a message with * and *** in it")

        assertThat(secret).`as`("missing secret").isNull()
        assertThat(redacted).`as`("message after a missing secret").isEqualTo("a message with * and *** in it")
    }

    @Test
    fun `an empty secret read from a file causes no replacement`() {
        val blank = Files.writeString(directory.resolve("blank.txt"), "  \n")
        environment["BLANK_TOKEN_FILE"] = blank.toString()
        val context = context()
        val secret = context.secret("BLANK_TOKEN")

        val redacted = context.redact("a message with * and *** in it")

        assertThat(secret).`as`("secret read from a blank file").isEmpty()
        assertThat(redacted).`as`("message after an empty secret").isEqualTo("a message with * and *** in it")
    }

    @Test
    fun `user information of a url is still removed`() {
        val context = context()

        val redacted = context.redact("jdbc:postgresql://reader:pw@db.example:5432/search")

        assertThat(redacted).`as`("redacted url").isEqualTo("jdbc:postgresql://***@db.example:5432/search")
    }

    @Test
    fun `a log line of the backend carries the redacted secret`() {
        environment["SEARCH_TOKEN"] = "s3cr3t-token"
        val context = context()
        context.secret("SEARCH_TOKEN")

        context.logger().warn("the index rejected s3cr3t-token")

        assertThat(lines).`as`("log lines of the backend").containsExactly("warn SEARCH | fake | the index rejected ***")
    }

}
