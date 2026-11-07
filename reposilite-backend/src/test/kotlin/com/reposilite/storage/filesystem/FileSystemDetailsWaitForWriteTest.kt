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

package com.reposilite.storage.filesystem

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.DocumentInfo
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import java.io.File
import java.util.concurrent.TimeUnit

internal class FileSystemDetailsWaitForWriteTest {

    @TempDir
    lateinit var root: File

    private fun storage(): StorageProvider =
        FileSystemStorageProviderFactory.of(InMemoryLogger(), root.toPath(), "100%")

    @Test
    fun `should describe the committed file when details are read while a write waits for the file lock`() {
        // given: a stored file, and a reader that keeps it open so that the next write waits for the file lock
        val storage = storage()
        val location = Location.of("group/app.jar")
        assertThat(storage.putFile(location, "old".byteInputStream()).isOk).describedAs("initial put").isTrue()
        val reader = storage.getFile(location).get()

        // and: a write with a longer content that is parked at the file lock
        var writeResult: Result<Unit, ErrorResponse>? = null
        val writer = Thread { writeResult = storage.putFile(location, "new-content".byteInputStream()) }
        writer.start()
        awaitParked(writer)

        // when: details are requested while the write is still waiting, and the reader then releases the file
        var details: Result<out FileDetails, ErrorResponse>? = null
        val detailsReader = Thread { details = storage.getFileDetails(location) }
        detailsReader.start()
        awaitSettled(detailsReader)
        reader.close()
        writer.join(TimeUnit.SECONDS.toMillis(10))
        detailsReader.join(TimeUnit.SECONDS.toMillis(10))

        // then: the write committed and the details describe the content that the body of the same request streams
        assertThat(writeResult?.isOk).describedAs("the waiting write completed").isTrue()
        assertThat(details?.isOk).describedAs("details were answered").isTrue()
        val detailsLength = (details!!.get() as DocumentInfo).contentLength
        val bodyLength = storage.getFile(location).get().use { it.readBytes().size.toLong() }
        assertThat(detailsLength)
            .describedAs("content length in details must match the streamed body")
            .isEqualTo(bodyLength)
    }

    /** Waits until [thread] is parked, which for a write on this provider means it is queued on the file lock. */
    private fun awaitParked(thread: Thread) {
        awaitState(thread, setOf(Thread.State.WAITING), "the write reached the file lock")
    }

    /** Waits until [thread] either parked on the file lock or finished, so that its answer is known before the release. */
    private fun awaitSettled(thread: Thread) {
        awaitState(thread, setOf(Thread.State.WAITING, Thread.State.TERMINATED), "the details request either waited or was answered")
    }

    private fun awaitState(thread: Thread, expected: Set<Thread.State>, description: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (thread.state !in expected && System.nanoTime() < deadline) {
            Thread.onSpinWait()
        }
        assertThat(thread.state).describedAs(description).isIn(expected)
    }
}
