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
package com.reposilite.console.infrastructure

import com.reposilite.ReposiliteJournalist
import com.reposilite.auth.specification.AuthenticationSpecification
import com.reposilite.console.CommandExecutor
import com.reposilite.console.ConsoleFacade
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.api.CreateAccessTokenRequest
import net.onelitefeather.ingot.identity.login.LoginFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The log frames of a version 1 connection: each line carries its sequence number and node, the hello may raise the level,
 * and a resume replays the cached lines after the number the client holds, or the whole cache for another boot.
 */
internal class ConsoleWebSocketLogFramesTest : AuthenticationSpecification() {

    private val journalist = ReposiliteJournalist(InMemoryLogger(), 100, testEnv = true, noColor = true)
    private val scheduler = FakeConsoleScheduler()

    private lateinit var handler: ConsoleWebSocketHandler

    @BeforeEach
    fun createHandler() {
        accessTokenFacade.createAccessToken(
            CreateAccessTokenRequest(TEMPORARY, "deploy-bot", secret = "s3cret", permissions = setOf(MANAGER))
        )
        val executor = CommandExecutor(
            journalist = journalist,
            failureFacade = FailureFacade(InMemoryLogger(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            source = ByteArrayInputStream(ByteArray(0)),
        )
        handler = ConsoleWebSocketHandler(
            journalist = journalist,
            consoleAuthenticator = ConsoleAuthenticator(
                accessTokenFacade,
                authenticationFacade,
                LoginFacade(service = null, sessions = null, ownTokens = null),
            ),
            consoleFacade = ConsoleFacade(journalist, executor),
            forwardedIp = Reference.reference("X-Forwarded-For"),
            audit = {},
            scheduler = scheduler,
            nodeId = "node-a",
            boot = BOOT,
        )
    }

    @AfterEach
    fun shutDownJournalist() {
        journalist.shutdown()
    }

    @Test
    fun `sends each live line as a log frame with its sequence number, level, text and this node`() {
        // given: a welcomed connection
        val connection = FakeConsoleConnection()
        handler.handleMessage(connection, hello())

        // when: a line is logged at the visible level
        journalist.visibleLogger.info("live line")

        // then: the log frame names the level, the text and this node, and carries a sequence number
        val frame = connection.json.filterIsInstance<LogFrame>().single()
        assertThat(frame.text).`as`("text").isEqualTo("live line")
        assertThat(frame.level).`as`("level").isEqualTo("INFO")
        assertThat(frame.node).`as`("node").isEqualTo("node-a")
        assertThat(frame.seq).`as`("sequence number").isGreaterThan(0L)
        assertThat(frame.dropped).`as`("dropped").isZero()
    }

    @Test
    fun `numbers the lines of one boot uniquely and in the order they were logged`() {
        // given: a welcomed connection
        val connection = FakeConsoleConnection()
        handler.handleMessage(connection, hello())

        // when: three lines are logged
        journalist.visibleLogger.info("one")
        journalist.visibleLogger.info("two")
        journalist.visibleLogger.warn("three")

        // then: the three numbers are distinct and increase in the order of logging
        val seqs = connection.json.filterIsInstance<LogFrame>().map { it.seq }
        assertThat(seqs).`as`("sequence numbers").hasSize(3).isSorted.doesNotHaveDuplicates()
    }

    @Test
    fun `sends no line below the level the hello asks for`() {
        // given: a connection that asks for warnings only
        val connection = FakeConsoleConnection()
        handler.handleMessage(connection, hello(logLevel = "WARN"))

        // when: an info line and a warning are logged
        journalist.visibleLogger.info("quiet")
        journalist.visibleLogger.warn("loud")

        // then: only the warning is sent
        assertThat(connection.json.filterIsInstance<LogFrame>().map { it.text }).`as`("texts")
            .containsExactly("loud")
    }

    @Test
    fun `sends no line below the visible threshold even when the hello asks for more`() {
        // given: a visible threshold of warnings and a hello that asks for debug lines
        journalist.setVisibleThreshold(Channel.WARN)
        val connection = FakeConsoleConnection()
        handler.handleMessage(connection, hello(logLevel = "DEBUG"))

        // when: an info line and an error are logged
        journalist.visibleLogger.info("below the threshold")
        journalist.visibleLogger.error("above the threshold")

        // then: the lower level does not lower the threshold
        assertThat(connection.json.filterIsInstance<LogFrame>().map { it.text }).`as`("texts")
            .containsExactly("above the threshold")
    }

    @Test
    fun `labels a line from another replica with the node of that replica`() {
        // given: a welcomed connection
        val connection = FakeConsoleConnection()
        handler.handleMessage(connection, hello())

        // when: a line of another replica is delivered
        journalist.deliverRemote(Channel.INFO, origin = "ingot-1", message = "from the other replica")

        // then: the log frame carries the node of that replica
        val frame = connection.json.filterIsInstance<LogFrame>().single()
        assertThat(frame.node).`as`("node").isEqualTo("ingot-1")
        assertThat(frame.text).`as`("text").isEqualTo("from the other replica")
    }

    @Test
    fun `welcomes with the boot and the last sequence number, and without a replay when there was no resume`() {
        // given: one line logged before the connection
        journalist.visibleLogger.info("before")
        val connection = FakeConsoleConnection()

        // when: a hello without resume
        handler.handleMessage(connection, hello())

        // then: the welcome names the boot and the last number, and states no replay
        val welcome = connection.json.first() as WelcomeFrame
        assertThat(welcome.boot).`as`("boot").isEqualTo(BOOT)
        assertThat(welcome.seq).`as`("last sequence number").isEqualTo(journalist.consoleLog.lastSequence())
        assertThat(welcome.replay).`as`("replay").isNull()
    }

    @Test
    fun `resumes after the sequence number of the same boot and says resumed`() {
        // given: a connection that saw three lines and then dropped
        val first = FakeConsoleConnection()
        handler.handleMessage(first, hello())
        journalist.visibleLogger.info("line-a")
        journalist.visibleLogger.info("line-b")
        journalist.visibleLogger.info("line-c")
        val seqOfB = first.json.filterIsInstance<LogFrame>().single { it.text == "line-b" }.seq

        // when: the client resumes after line b in the same boot
        val second = FakeConsoleConnection()
        handler.handleMessage(second, hello(resumeBoot = BOOT, resumeSeq = seqOfB))

        // then: the welcome says resumed, and only the line after b is replayed
        val welcome = second.json.first() as WelcomeFrame
        assertThat(welcome.replay).`as`("replay").isEqualTo("resumed")
        assertThat(second.json.filterIsInstance<LogFrame>().map { it.text }).`as`("replayed texts")
            .containsExactly("line-c")
    }

    @Test
    fun `resumes from another boot with the whole cache, and each replayed line keeps its first number`() {
        // given: a connection that saw two lines, and their numbers
        val first = FakeConsoleConnection()
        handler.handleMessage(first, hello())
        journalist.visibleLogger.info("line-a")
        journalist.visibleLogger.info("line-b")
        val stampedBefore = first.json.filterIsInstance<LogFrame>().associate { it.text to it.seq }

        // when: the client resumes with the number of an earlier process
        val second = FakeConsoleConnection()
        handler.handleMessage(second, hello(resumeBoot = "boot-of-an-earlier-process", resumeSeq = 1L))

        // then: the welcome says full, the whole cache is replayed, and each line has the number it was stamped with
        val welcome = second.json.first() as WelcomeFrame
        assertThat(welcome.replay).`as`("replay").isEqualTo("full")
        val replayed = second.json.filterIsInstance<LogFrame>().associate { it.text to it.seq }
        assertThat(replayed).`as`("replayed lines with their numbers")
            .containsAllEntriesOf(stampedBefore)
        assertThat(replayed.keys).`as`("replayed texts").contains("line-a", "line-b")
    }

    private fun hello(logLevel: String? = null, resumeBoot: String? = null, resumeSeq: Long? = null): String {
        val fields = mutableListOf("\"type\":\"hello\"", "\"protocols\":[1]", "\"token\":\"deploy-bot:s3cret\"")
        logLevel?.let { fields += "\"logLevel\":\"$it\"" }
        if (resumeBoot != null && resumeSeq != null) {
            fields += "\"resume\":{\"boot\":\"$resumeBoot\",\"seq\":$resumeSeq}"
        }
        return fields.joinToString(prefix = "{", postfix = "}")
    }

    private companion object {
        const val BOOT = "boot-a"
    }

}
