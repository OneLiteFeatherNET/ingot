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

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.time.Duration

private val mapper = ObjectMapper()

private const val WELCOME = """{"type":"welcome","protocol":1,"boot":"b-1","seq":7,"replay":"resumed"}"""

/** Records each wait instead of waiting, so no test depends on the clock. */
private class RecordedPauses : (Duration) -> Unit {
    val waits = mutableListOf<Duration>()
    override fun invoke(wait: Duration) {
        waits.add(wait)
    }
}

internal class ReconnectTest {

    @Test
    fun `the backoff starts at 1 second, doubles and stops at 30 seconds`() {
        val backoff = Backoff { 0.5 }

        val steps = List(8) { backoff.next().toMillis() }

        assertThat(steps).describedAs("steps without jitter").containsExactly(1000, 2000, 4000, 8000, 16000, 30000, 30000, 30000)
    }

    @Test
    fun `the jitter moves a step by at most a fifth and never above 30 seconds`() {
        assertThat(Backoff { 0.0 }.next().toMillis()).describedAs("lowest first step").isEqualTo(800)
        assertThat(Backoff { 1.0 }.next().toMillis()).describedAs("highest first step").isEqualTo(1200)
        val high = Backoff { 1.0 }
        repeat(5) { high.next() }
        assertThat(high.next().toMillis()).describedAs("a capped step with jitter").isEqualTo(30000)
    }

    @Test
    fun `a reset starts the backoff at 1 second again`() {
        val backoff = Backoff { 0.5 }
        repeat(3) { backoff.next() }

        backoff.reset()

        assertThat(backoff.next().toMillis()).describedAs("first step after a reset").isEqualTo(1000)
    }

    @Test
    fun `the reconnect waits one step before each attempt and retries while the server cannot be reached`() {
        StubSocket { text, peer -> if (mapper.readTree(text).path("type").asText() == "hello") peer.send(WELCOME) }.use { stub ->
            val pauses = RecordedPauses()
            var attempts = 0
            val reconnector = Reconnector(Backoff { 0.5 }, pauses, PrintStream(ByteArrayOutputStream(), true, StandardCharsets.UTF_8)) { resume ->
                attempts++
                if (attempts < 3) throw ServerUnreachableException(IOException("connection refused"))
                ConsoleSocket.open(stub.url, "ci:ci-secret", "ingot-cli/test", HttpClient.newHttpClient(), Duration.ofSeconds(5), {}, resume)
            }

            reconnector.reconnect(ConsoleResume("b-1", 9)).use { socket ->
                assertThat(socket.welcome.replay).describedAs("replay").isEqualTo("resumed")
            }

            assertThat(pauses.waits.map { it.toMillis() }).describedAs("waits").containsExactly(1000L, 2000L, 4000L)
            val hello = mapper.readTree(stub.frames.first())
            assertThat(hello.path("resume").path("boot").asText()).describedAs("resumed boot").isEqualTo("b-1")
            assertThat(hello.path("resume").path("seq").asLong()).describedAs("resumed seq").isEqualTo(9L)
        }
    }

    @Test
    fun `the reconnect gives up on a refusal and passes it on`() {
        val pauses = RecordedPauses()
        val reconnector = Reconnector(Backoff { 0.5 }, pauses, PrintStream(ByteArrayOutputStream(), true, StandardCharsets.UTF_8)) {
            throw ConsoleSocketRefusedException("unauthorized", "invalid credentials")
        }

        val refused = catchThrowableOfType(ConsoleSocketRefusedException::class.java) { reconnector.reconnect(null) }

        assertThat(refused.code).describedAs("refusal code").isEqualTo("unauthorized")
        assertThat(pauses.waits).describedAs("one wait before the only attempt").hasSize(1)
    }

}
