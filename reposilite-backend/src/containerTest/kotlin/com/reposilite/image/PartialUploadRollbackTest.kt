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

package com.reposilite.image

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * A rollback after Ingot was stopped in the middle of an upload. The upload to the second artifact is held back by its
 * client, so its partial file is still in the volume when Ingot stops. The upstream image then starts on the same volume
 * and must start, serve the artifact that completed before the stop with the same SHA-1, and list its directory.
 *
 * The test waits for the partial file with a bounded poll, because the file exists only while the client is sending.
 */
class PartialUploadRollbackTest {

    @Test
    fun `upstream starts and serves the completed artifact when Ingot stopped during an upload`(@TempDir directory: Path) {
        DataDirectory(directory, ImageUnderTest.server).use { data ->
            val release = CountDownLatch(1)
            try {
                serverContainer(
                    image = ImageUnderTest.server,
                    data = data,
                    environment = mapOf("REPOSILITE_OPTS" to "--token $BOOTSTRAP_NAME:$BOOTSTRAP_SECRET"),
                ).use { ingot ->
                    ingot.startOrExplain()
                    val url = ingot.baseUrl()

                    val stored = Http.put("$url$COMPLETED_PATH", COMPLETED_BODY, BOOTSTRAP)
                    assertThat(stored.isSuccessful).describedAs("store the artifact that completes before the stop: %s", stored.body).isTrue

                    // The client sends one chunk and then holds the rest back, so the upload is still running when Ingot stops.
                    thread(isDaemon = true, name = "stalled-upload") {
                        // The outcome is ignored on purpose: the server is stopped under this request.
                        runCatching { HttpClient.newHttpClient().send(stalledPut("$url$PARTIAL_UPLOAD_PATH", release), BodyHandlers.discarding()) }
                    }

                    val partials = awaitPartialUpload(data)
                    assertThat(partials)
                        .describedAs("partial file of the interrupted upload in the volume before Ingot stops")
                        .isNotEmpty
                }
            } finally {
                release.countDown()
            }

            serverContainer(ImageUnderTest.upstream, data).use { upstream ->
                upstream.startOrExplain()
                val url = upstream.baseUrl()

                val artifact = Http.get("$url$COMPLETED_PATH")
                assertThat(artifact.status).describedAs("status of the completed artifact on upstream").isEqualTo(200)
                assertThat(sha1(artifact.body))
                    .describedAs("SHA-1 of the completed artifact on upstream")
                    .isEqualTo(sha1(COMPLETED_BODY))

                val listing = Http.get("$url$ARTIFACT_DIRECTORY_URL")
                assertThat(listing.status).describedAs("status of the directory listing on upstream").isEqualTo(200)
                assertThat(listing.body)
                    .describedAs("directory listing on upstream")
                    .contains(COMPLETED_NAME)
                    .doesNotContain(PARTIAL_PREFIX)
            }
        }
    }

    /** Polls the volume until a partial file shows up, for at most [UPLOAD_WAIT]. Returns the partial names found. */
    private fun awaitPartialUpload(data: DataDirectory): List<String> {
        val deadline = System.nanoTime() + UPLOAD_WAIT.toNanos()
        while (true) {
            val partials = data.list(ARTIFACT_DIRECTORY).filter { it.startsWith(PARTIAL_PREFIX) && it.endsWith(PARTIAL_SUFFIX) }
            if (partials.isNotEmpty() || System.nanoTime() > deadline) {
                return partials
            }
            Thread.sleep(POLL_INTERVAL.toMillis())
        }
    }

    private fun stalledPut(url: String, release: CountDownLatch): HttpRequest {
        val credentials = Base64.getEncoder().encodeToString("$BOOTSTRAP_NAME:$BOOTSTRAP_SECRET".toByteArray())
        return HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Basic $credentials")
            .header("Content-Type", "application/octet-stream")
            .PUT(BodyPublishers.ofInputStream { StalledBody(release) })
            .build()
    }

    /** Delivers the first chunk of a body, then holds the rest back until the latch is released or the wait runs out. */
    private class StalledBody(private val release: CountDownLatch) : InputStream() {
        private val head = ByteArray(FIRST_CHUNK) { 'p'.code.toByte() }
        private var position = 0

        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) == -1) -1 else single[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position < head.size) {
                val count = minOf(length, head.size - position)
                head.copyInto(buffer, offset, position, position + count)
                position += count
                return count
            }
            release.await(STALL_LIMIT.toMillis(), TimeUnit.MILLISECONDS)
            return -1
        }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val BOOTSTRAP_NAME = "bootstrap"
        const val BOOTSTRAP_SECRET = "bootstrap-secret-for-the-partial-upload"
        val BOOTSTRAP = BOOTSTRAP_NAME to BOOTSTRAP_SECRET

        const val ARTIFACT_DIRECTORY = "releases/com/example/rollback/1.0"
        const val ARTIFACT_DIRECTORY_URL = "/$ARTIFACT_DIRECTORY/"
        const val COMPLETED_NAME = "rollback-1.0.jar"
        const val COMPLETED_PATH = "/$ARTIFACT_DIRECTORY/$COMPLETED_NAME"
        const val COMPLETED_BODY = "complete artifact body for the rollback check\n"
        const val PARTIAL_UPLOAD_PATH = "/$ARTIFACT_DIRECTORY/rollback-2.0.jar"

        const val PARTIAL_PREFIX = ".ingot-"
        const val PARTIAL_SUFFIX = ".part"
        const val FIRST_CHUNK = 64 * 1024

        val UPLOAD_WAIT: Duration = Duration.ofSeconds(60)
        val POLL_INTERVAL: Duration = Duration.ofMillis(500)
        val STALL_LIMIT: Duration = Duration.ofSeconds(120)
    }
}
