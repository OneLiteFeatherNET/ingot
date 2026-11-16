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

import com.reposilite.shared.http.CacheScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

internal class PresignedRedirectRuleTest {

    @Test
    fun `should redirect a cacheable GET of a public file without range or conditional headers`() {
        assertThat(presignedRedirectApplies(method = "GET", hasRangeOrConditionalHeaders = false, cacheable = true, scope = CacheScope.PUBLIC))
            .describedAs("redirect of a public release download").isTrue
    }

    @Test
    fun `should not redirect a HEAD request`() {
        assertThat(presignedRedirectApplies(method = "HEAD", hasRangeOrConditionalHeaders = false, cacheable = true, scope = CacheScope.PUBLIC))
            .describedAs("redirect of a HEAD request").isFalse
    }

    @Test
    fun `should not redirect a GET with a range or a conditional header`() {
        assertThat(presignedRedirectApplies(method = "GET", hasRangeOrConditionalHeaders = true, cacheable = true, scope = CacheScope.PUBLIC))
            .describedAs("redirect of a range, If-None-Match, If-Modified-Since or If-Range request").isFalse
    }

    @Test
    fun `should not redirect a file that is not cacheable`() {
        assertThat(presignedRedirectApplies(method = "GET", hasRangeOrConditionalHeaders = false, cacheable = false, scope = CacheScope.PUBLIC))
            .describedAs("redirect of a snapshot or metadata file").isFalse
    }

    @Test
    fun `should not redirect a file whose scope is private`() {
        assertThat(presignedRedirectApplies(method = "GET", hasRangeOrConditionalHeaders = false, cacheable = true, scope = CacheScope.PRIVATE))
            .describedAs("redirect of a private repository file").isFalse
    }

    @Test
    fun `should keep a credentialed proxy out of the redirect even when the escape hatch is set`() {
        val nodes = mapOf("proxy" to CacheScopeNode(visibility = RepositoryVisibility.PUBLIC, loopbackReferences = emptyList(), credentialedRemote = true))

        // the redirect asks for the scope without the escape hatch, so the upstream credentials still count
        val scope = resolveCacheScope(repository = "proxy", nodeOf = nodes::get, privateAsPublic = false)

        assertThat(presignedRedirectApplies(method = "GET", hasRangeOrConditionalHeaders = false, cacheable = true, scope = scope))
            .describedAs("redirect of a credentialed proxy").isFalse
    }

    @Test
    fun `should give the redirect the smaller of maxAge and half the lifetime`() {
        assertThat(redirectCacheControl(maxAgeSeconds = 3600, ttl = Duration.ofSeconds(300)))
            .describedAs("Cache-Control with maxAge 3600 and a lifetime of 300 seconds").isEqualTo("public, max-age=150")
        assertThat(redirectCacheControl(maxAgeSeconds = 60, ttl = Duration.ofSeconds(300)))
            .describedAs("Cache-Control with maxAge 60 and a lifetime of 300 seconds").isEqualTo("public, max-age=60")
    }

}
