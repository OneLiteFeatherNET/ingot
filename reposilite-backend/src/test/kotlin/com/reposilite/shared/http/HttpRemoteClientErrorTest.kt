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

package com.reposilite.shared.http

import com.reposilite.journalist.backend.InMemoryLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertError

internal class HttpRemoteClientErrorTest {

    @Test
    fun `head should not expose the exception class or message when the remote request fails`() {
        // given: a remote client and a URI whose port cannot have a listener, so the connection is refused locally
        val client = HttpRemoteClient(InMemoryLogger(), proxy = null)

        // when: the remote file is requested
        val error = assertError(client.head("http://127.0.0.1:0/app.jar", credentials = null, connectTimeoutInSeconds = 1, readTimeoutInSeconds = 1))

        // then: the status is still a bad request
        assertThat(error.status).describedAs("status of a failed remote request").isEqualTo(400)

        // and: the exception class and its message do not reach the response
        assertThat(error.message)
            .describedAs("message of a failed remote request")
            .doesNotContain("ConnectException", "java.net", "Connection refused")
    }

}
