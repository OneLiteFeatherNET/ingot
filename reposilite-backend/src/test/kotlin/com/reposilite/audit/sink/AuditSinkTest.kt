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

package com.reposilite.audit.sink

import com.reposilite.audit.api.AuditAction.TOKEN_CREATED
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.InMemoryLogger
import net.onelitefeather.ingot.identity.login.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission.GROUP_READ
import java.nio.file.attribute.PosixFilePermission.GROUP_WRITE
import java.nio.file.attribute.PosixFilePermission.OTHERS_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

internal class AuditSinkTest {

    private val journalist = InMemoryLogger()
    private val clock = MutableClock(Instant.parse("2026-11-10T08:15:30Z"))

    private fun line(actor: String = "admin") = AuditSinkLine(
        time = clock.instant(),
        node = "ingot-a",
        action = TOKEN_CREATED,
        actor = actor,
        actorAddress = null,
        repository = null,
        path = null,
        details = null,
        success = true,
    )

    private fun warnings(): List<String> =
        journalist.messages.filter { it.key == Channel.WARN }.map { it.value }

    @Test
    @Timeout(10)
    fun `drops a line without waiting when the queue is full`() {
        val gate = CountDownLatch(1)
        val writer = RecordingWriter(gate = gate)
        val sink = AuditSink(listOf(writer), clock, journalist.logger, capacity = 1)

        try {
            // The writer is held at the gate, so at most one line is taken and one waits in the queue.
            repeat(10) { sink.offer(line()) }

            assertThat(sink.droppedCount()).describedAs("lines dropped on a full queue").isGreaterThanOrEqualTo(8)
        } finally {
            gate.countDown()
            sink.close()
        }
    }

    @Test
    fun `a failing write is counted and never reaches the caller`() {
        val writer = RecordingWriter(failure = IOException("disk full"))
        val sink = AuditSink(listOf(writer), clock, journalist.logger)

        sink.offer(line())
        sink.close()

        assertThat(sink.failedCount()).describedAs("failed writes").isEqualTo(1)
        assertThat(writer.written).describedAs("lines written").isEmpty()
    }

    @Test
    fun `logs one warning per minute that names the error`() {
        val writer = RecordingWriter(failure = IOException("Cannot append audit lines to /data/audit.jsonl: disk full"))
        val sink = AuditSink(listOf(writer), clock, journalist.logger)

        // The clock does not move, so every failure falls into the same minute.
        repeat(3) { sink.offer(line()) }
        sink.close()

        assertThat(warnings())
            .describedAs("warnings of the audit sink")
            .hasSize(1)
        assertThat(warnings().single()).contains("/data/audit.jsonl", "disk full")
    }

    @Test
    fun `writes each line on its own line with a trailing newline`() {
        val out = ByteArrayOutputStream()
        val sink = AuditSink(listOf(StreamAuditLineWriter(out)), clock, journalist.logger)

        sink.offer(line("alice"))
        sink.offer(line("bob"))
        sink.close()

        val text = out.toString(UTF_8)
        assertThat(text).describedAs("output ends with a newline").endsWith("\n")
        assertThat(text.lines().filter { it.isNotEmpty() }).describedAs("one JSON object per line").hasSize(2)
        assertThat(text.count { it == '\n' }).describedAs("newlines in the output").isEqualTo(2)
    }

    @Test
    fun `a failing writer does not stop the others`() {
        val failing = RecordingWriter(failure = IOException("disk full"))
        val healthy = RecordingWriter()
        val sink = AuditSink(listOf(failing, healthy), clock, journalist.logger)

        sink.offer(line())
        sink.close()

        assertThat(healthy.written).describedAs("lines of the healthy writer").hasSize(1)
    }

    @Test
    fun `reopens the file when it has been renamed`(@TempDir directory: Path) {
        val file = directory.resolve("audit.jsonl")
        val writer = FileAuditLineWriter(file, clock)

        writer.write(listOf("first\n"))
        Files.move(file, directory.resolve("audit.jsonl.1"))
        clock.advance(Duration.ofSeconds(2))
        writer.write(listOf("second\n"))
        writer.close()

        assertThat(Files.readString(file)).describedAs("new file at the original path").isEqualTo("second\n")
        assertThat(Files.readString(directory.resolve("audit.jsonl.1"))).describedAs("renamed file").isEqualTo("first\n")
    }

    @Test
    fun `creates the file with owner and group access only`(@TempDir directory: Path) {
        val file = directory.resolve("audit.jsonl")
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"), "POSIX permissions are not available here")

        FileAuditLineWriter(file, clock).use { it.write(listOf("line\n")) }

        assertThat(Files.getPosixFilePermissions(file))
            .describedAs("permissions of the audit file")
            .containsExactlyInAnyOrder(OWNER_READ, OWNER_WRITE, GROUP_READ)
            .doesNotContain(GROUP_WRITE, OTHERS_READ)
    }

    @Test
    fun `appends to a file that already holds lines`(@TempDir directory: Path) {
        val file = directory.resolve("audit.jsonl")
        Files.writeString(file, "earlier\n")

        FileAuditLineWriter(file, clock).use { it.write(listOf("later\n")) }

        assertThat(Files.readString(file)).isEqualTo("earlier\nlater\n")
    }

    private class RecordingWriter(
        private val failure: IOException? = null,
        private val gate: CountDownLatch? = null,
    ) : AuditLineWriter {

        val written = CopyOnWriteArrayList<String>()

        override fun write(lines: List<String>) {
            gate?.await(5, SECONDS)
            failure?.let { throw it }
            written += lines
        }

        override fun close() {}

    }

}
