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
package com.reposilite.maven

import com.reposilite.journalist.Journalist
import com.reposilite.maven.api.UploadChecksumMismatchEvent
import com.reposilite.maven.api.UploadChecksumOutcome
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.api.Location
import io.javalin.http.HttpStatus.BAD_REQUEST
import panda.std.Result
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream

/** The problem type of the `400` that a refused checksum file gets, so that the metrics can tell it apart. */
internal const val UPLOAD_CHECKSUM_MISMATCH_PROBLEM_TYPE = "urn:ingot:problem:upload-checksum-mismatch"

/** Reads a stored file, or returns null when no file is stored under [location]. */
internal fun interface StoredFileReader {
    fun read(location: Location): InputStream?
}

/**
 * Compares an uploaded checksum file with the artifact it describes, before anything is written. The artifact is the
 * same path without the checksum suffix. A checksum file whose artifact is not stored is accepted unchecked, under every
 * policy. The verifier returns the content to store: the whole upload, read from the request once.
 */
internal class UploadChecksumVerifier(
    private val journalist: Journalist,
    private val publish: (UploadChecksumMismatchEvent) -> Unit,
) {

    fun verify(
        repository: String,
        policy: UploadChecksumPolicy,
        gav: Location,
        by: String,
        content: InputStream,
        storedFile: StoredFileReader,
    ): Result<InputStream, ErrorResponse> {
        val algorithm = checksumOfFileName(gav.getSimpleName())
        if (algorithm == null || policy == UploadChecksumPolicy.IGNORE) {
            return Result.ok(content)
        }

        val artifact = gav.resolveSibling(gav.getSimpleName().removeSuffix("." + algorithm.extension))
        val stored = storedFile.read(artifact) ?: return Result.ok(content)

        // Only the first bytes are needed to read the digest; the rest of the upload follows them unchanged.
        val head = content.readNBytes(UPLOAD_CHECKSUM_MAX_BYTES + 1)
        val whole = SequenceInputStream(ByteArrayInputStream(head), content)

        val expected = parseUploadedDigest(head, algorithm)
        val actual = stored.use { algorithm.generate(it) }
        if (expected == actual) {
            return Result.ok(whole)
        }

        val reason = if (expected == null) "is not a ${algorithm.name} digest" else "does not match the stored artifact"
        val outcome = if (policy == UploadChecksumPolicy.REJECT) UploadChecksumOutcome.REJECTED else UploadChecksumOutcome.ACCEPTED
        journalist.logger.warn("CHECKSUM | ${algorithm.name} file $gav uploaded to $repository by $by $reason (${outcome.name.lowercase()})")
        publish(UploadChecksumMismatchEvent(repository, gav, by, algorithm, outcome))

        if (policy == UploadChecksumPolicy.WARN) {
            return Result.ok(whole)
        }

        return Result.error(
            ErrorResponse(BAD_REQUEST, "${algorithm.name} checksum file ${gav.getSimpleName()} $reason, so it was refused")
                .apply { type = UPLOAD_CHECKSUM_MISMATCH_PROBLEM_TYPE }
        )
    }

}
