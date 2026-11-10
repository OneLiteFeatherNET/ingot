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

package com.reposilite.status

import com.reposilite.shared.ErrorResponse
import io.javalin.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.javalin.http.HttpStatus.NOT_FOUND
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result

internal class StorageReadinessTest {

    @Test
    fun `should answer an empty storage root as ready`() {
        // given: a listing of a root that holds no entries
        val listing: Result<List<String>, ErrorResponse> = Result.ok(emptyList())

        // when: the listing is read by the readiness check
        val answered = listing.answersListing()

        // then: an empty root counts as ready
        assertThat(answered).`as`("an empty root answers the listing").isTrue
    }

    @Test
    fun `should answer a not found listing as ready because the backend responded`() {
        // given: a listing that the S3 provider answers as not found, which it does for an empty prefix
        val listing: Result<List<String>, ErrorResponse> =
            Result.error(ErrorResponse(NOT_FOUND.code, "Directory not found or is empty"))

        // when: the listing is read by the readiness check
        val answered = listing.answersListing()

        // then: the backend answered, so the storage root is ready
        assertThat(answered).`as`("a not found answer comes from a reachable backend").isTrue
    }

    @Test
    fun `should answer a failed listing as not ready`() {
        // given: a listing that failed on the server side, such as a network or credentials failure
        val listing: Result<List<String>, ErrorResponse> =
            Result.error(ErrorResponse(INTERNAL_SERVER_ERROR.code, "Failed to list /"))

        // when: the listing is read by the readiness check
        val answered = listing.answersListing()

        // then: the storage root is not ready
        assertThat(answered).`as`("a server side failure is not ready").isFalse
    }

}
