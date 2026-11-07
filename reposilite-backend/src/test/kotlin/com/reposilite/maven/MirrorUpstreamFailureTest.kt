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

import com.reposilite.maven.api.LookupRequest
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertError

internal class MirrorUpstreamFailureTest : MavenSpecification() {

    override fun repositories() = listOf(
        RepositorySettings(
            id = "BROKEN",
            visibility = RepositoryVisibility.PUBLIC,
            proxied = listOf(MirroredRepositorySettings(reference = REMOTE_REPOSITORY_BROKEN)),
        ),
        RepositorySettings(
            id = "THROTTLED",
            visibility = RepositoryVisibility.PUBLIC,
            proxied = listOf(MirroredRepositorySettings(reference = REMOTE_REPOSITORY_THROTTLED)),
        ),
    )

    private val gav = "com/example/lib/1.0.0/lib-1.0.0.jar".toLocation()

    @Test
    fun `should return the upstream server error when the file is missing locally and the only upstream fails while looking up details`() {
        // when: the file is not stored locally and the upstream answers 502
        val result = mavenFacade.findDetails(LookupRequest(UNAUTHORIZED, "BROKEN", gav))

        // then: the upstream failure is returned, not a local not found
        assertThat(assertError(result).status).describedAs("status of the upstream failure").isEqualTo(502)
    }

    @Test
    fun `should return the upstream server error when the file is missing locally and the only upstream fails while looking up data`() {
        // when: the file is not stored locally and the upstream answers 502
        val result = mavenFacade.findFile(LookupRequest(UNAUTHORIZED, "BROKEN", gav))

        // then: the upstream failure is returned, not a local not found
        assertThat(assertError(result).status).describedAs("status of the upstream failure").isEqualTo(502)
    }

    @Test
    fun `should return the upstream rate limit when the file is missing locally and the only upstream is throttled`() {
        // when: the file is not stored locally and the upstream answers 429
        val result = mavenFacade.findDetails(LookupRequest(UNAUTHORIZED, "THROTTLED", gav))

        // then: the rate limit is returned, so clients do not cache a false miss
        assertThat(assertError(result).status).describedAs("status of the rate limit").isEqualTo(429)
    }

}
