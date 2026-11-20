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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

private val mapper = ObjectMapper()

private fun parse(frame: String): JsonNode = mapper.readTree(frame)

private const val WELCOME = """{"type":"welcome","protocol":1,"boot":"b-1","seq":7,"replay":null}"""

/** A server that welcomes every hello and answers each other frame with what [answer] returns for it. */
private fun server(answer: (JsonNode, StubSocket.Peer) -> Unit = { _, _ -> }) = StubSocket { frame, peer ->
    val parsed = parse(frame)
    if (parsed.path("type").asText() == "hello") peer.send(WELCOME) else answer(parsed, peer)
}

private fun result(id: String, exitCode: Int = 0, confirmed: Boolean = false, text: String = "ok") =
    """{"type":"result","id":"$id","status":"${if (exitCode == 0) "SUCCEEDED" else "FAILED"}","exitCode":$exitCode,""" +
        """"output":[{"stream":"${if (exitCode == 0) "stdout" else "stderr"}","text":"$text"}],"data":null,"node":"ingot-0","confirmed":$confirmed}"""

private fun open(url: String, onLog: (ConsoleLogLine) -> Unit = {}) =
    ConsoleSocket.open(url, "ci:ci-secret", "ingot-cli/test", HttpClient.newHttpClient(), Duration.ofSeconds(10), onLog)

internal class ConsoleSocketTest {

    @Test
    fun `the hello names protocol 1, the client and the token, on the console socket path`() {
        server().use { stub ->
            open(stub.url + "/ingot").use { socket ->
                assertThat(socket.welcome.boot).describedAs("boot of the welcome").isEqualTo("b-1")
                assertThat(socket.welcome.seq).describedAs("seq of the welcome").isEqualTo(7L)
            }
            val hello = parse(stub.frames.first())
            assertThat(stub.paths).describedAs("upgrade path keeps the context path").containsExactly("/ingot/api/console/sock")
            assertThat(hello.path("type").asText()).isEqualTo("hello")
            assertThat(hello.path("protocols").map { it.asInt() }).describedAs("protocols").containsExactly(1)
            assertThat(hello.path("client").asText()).describedAs("client").isEqualTo("ingot-cli/test")
            assertThat(hello.path("token").asText()).describedAs("token").isEqualTo("ci:ci-secret")
        }
    }

    @Test
    fun `an execute returns the result with the same id`() {
        server { frame, peer -> peer.send(result(frame.path("id").asText(), text = "status ok")) }.use { stub ->
            open(stub.url).use { socket ->
                val result = socket.execute("status") { _, _ -> error("status needs no confirmation") }

                assertThat(result.exitCode).describedAs("exit code").isEqualTo(0)
                assertThat(result.node).describedAs("node").isEqualTo("ingot-0")
                assertThat(result.output.map { it.text }).describedAs("output").containsExactly("status ok")
            }
            val execute = parse(stub.frames[1])
            assertThat(execute.path("type").asText()).isEqualTo("execute")
            assertThat(execute.path("command").asText()).isEqualTo("status")
            assertThat(execute.path("id").asText()).describedAs("execute id").isNotBlank()
        }
    }

    @Test
    fun `a confirm frame asks the user and a declined answer sends accept false`() {
        val asked = CopyOnWriteArrayList<String>()
        server { frame, peer ->
            when (frame.path("type").asText()) {
                "execute" -> peer.send("""{"type":"confirm","id":"${frame.path("id").asText()}","command":"stop","reason":"stops the server"}""")
                "confirm" -> peer.send(result(frame.path("id").asText(), exitCode = 3, text = "did not run"))
            }
        }.use { stub ->
            open(stub.url).use { socket ->
                val result = socket.execute("stop") { command, reason -> asked.add("$command|$reason"); false }

                assertThat(result.exitCode).describedAs("a declined command answers exit code 3").isEqualTo(3)
            }
            assertThat(asked).describedAs("the user was asked once").containsExactly("stop|stops the server")
            val confirm = parse(stub.frames[2])
            assertThat(confirm.path("type").asText()).isEqualTo("confirm")
            assertThat(confirm.path("accept").asBoolean(true)).describedAs("accept").isFalse()
            assertThat(confirm.path("id").asText()).describedAs("the confirm answers the execute").isEqualTo(parse(stub.frames[1]).path("id").asText())
        }
    }

    @Test
    fun `log frames reach the callback while a command runs`() {
        val lines = CopyOnWriteArrayList<ConsoleLogLine>()
        server { frame, peer ->
            peer.send("""{"type":"log","seq":8,"node":"ingot-1","level":"INFO","text":"hello","dropped":2}""")
            peer.send(result(frame.path("id").asText()))
        }.use { stub ->
            open(stub.url) { lines.add(it) }.use { it.execute("status") { _, _ -> false } }
        }
        assertThat(lines).describedAs("log lines").containsExactly(ConsoleLogLine(seq = 8, node = "ingot-1", level = "INFO", text = "hello", dropped = 2))
    }

    @Test
    fun `a complete returns the candidates and the range they replace`() {
        server { frame, peer ->
            peer.send("""{"type":"complete","id":"${frame.path("id").asText()}","replace":{"start":0,"end":3},"candidates":["status","stats"]}""")
        }.use { stub ->
            val completion = open(stub.url).use { it.complete("sta", 3) }

            assertThat(completion.candidates).describedAs("candidates").containsExactly("status", "stats")
            assertThat(completion.start to completion.end).describedAs("range").isEqualTo(0 to 3)
            val complete = parse(stub.frames[1])
            assertThat(complete.path("line").asText()).isEqualTo("sta")
            assertThat(complete.path("cursor").asInt()).isEqualTo(3)
        }
    }

    @Test
    fun `a refused hello is reported with its code and without the secret`() {
        StubSocket { _, peer ->
            peer.send("""{"type":"error","code":"unauthorized","message":"invalid credentials"}""")
            peer.close(1008)
        }.use { stub ->
            val refused = catchThrowableOfType(ConsoleSocketRefusedException::class.java) { open(stub.url) }

            assertThat(refused.code).describedAs("code").isEqualTo("unauthorized")
            assertThat(refused.message).describedAs("message").doesNotContain("ci-secret")
        }
    }

    @Test
    fun `a legacy server answers a hello without a typed frame and is refused with no code`() {
        StubSocket { _, peer ->
            peer.send("""{"status":401,"message":"Unauthorized CLI access request from 127.0.0.1 (missing credentials)"}""")
            peer.close(1000)
        }.use { stub ->
            val refused = catchThrowableOfType(ConsoleSocketRefusedException::class.java) { open(stub.url) }

            assertThat(refused.code).describedAs("a legacy refusal has no code").isNull()
        }
    }

    @Test
    fun `an error frame for the execute is reported with its code`() {
        server { frame, peer ->
            peer.send("""{"type":"error","id":"${frame.path("id").asText()}","code":"busy","message":"a command runs","retryable":true}""")
        }.use { stub ->
            open(stub.url).use { socket ->
                val refused = catchThrowableOfType(ConsoleSocketRefusedException::class.java) { socket.execute("status") { _, _ -> false } }

                assertThat(refused.code).describedAs("code").isEqualTo("busy")
            }
        }
    }

    @Test
    fun `a drop while a command runs is reported as lost`() {
        server { _, peer -> peer.close(1011) }.use { stub ->
            open(stub.url).use { socket ->
                val lost = catchThrowableOfType(ConsoleSocketLostException::class.java) { socket.execute("status") { _, _ -> false } }

                assertThat(lost).describedAs("the command outcome is unknown").isNotNull()
            }
            assertThat(stub.frames.count { parse(it).path("type").asText() == "execute" }).describedAs("the execute is not sent again").isEqualTo(1)
        }
    }

}
