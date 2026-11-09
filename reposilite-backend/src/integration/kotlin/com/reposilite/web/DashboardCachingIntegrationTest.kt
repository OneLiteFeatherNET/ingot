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

@file:Suppress("FunctionName")

package com.reposilite.web

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable"
private const val REVALIDATE_CACHE_CONTROL = "no-cache"
private const val OPENER_POLICY = "same-origin"

private fun HttpResponse<*>.headerValue(name: String): String? =
    headers.getFirst(name).takeUnless { it.isNullOrEmpty() }

private fun get(base: String, path: String): HttpResponse<String> =
    Unirest.get("$base$path").asString()

/**
 * The embedded dashboard tells browsers how long to keep its files. A hashed bundle never changes under its name, so
 * it is cached for a year. The document names that bundle, so browsers ask for it again on every load. The document
 * also opens in its own browsing context group, while the files it loads and the api do not carry that policy.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class DashboardCachingIntegrationTest : ReposiliteSpecification() {

    /** The first script the built dashboard loads, read from its document so the hash is never hard-coded. */
    private fun bundlePath(): String {
        val document = get(base, "/").body
        val match = Regex("""/assets/[^"'\s?]+\.js""").find(document)

        assertThat(match).describedAs("a script under /assets/ in the dashboard document").isNotNull
        return match!!.value
    }

    @Test
    fun `should cache an existing bundle for a year as immutable`() {
        // given: a bundle name taken from the dashboard document
        val path = bundlePath()

        // when: the bundle is requested
        val response = get(base, path)

        // then: the hashed file is kept without asking again
        assertThat(response.status).describedAs("status of GET $path").isEqualTo(200)
        assertThat(response.headerValue("Cache-Control"))
            .describedAs("Cache-Control of GET $path")
            .isEqualTo(IMMUTABLE_CACHE_CONTROL)
    }

    @Test
    fun `should not mark a missing script as immutable`() {
        // when: a script that does not exist is requested
        val response = get(base, "/assets/does-not-exist.js")

        // then: the answer is a not found, and a cache must not keep it as an immutable file
        assertThat(response.status).describedAs("status").isEqualTo(404)
        assertThat(response.headerValue("Cache-Control").orEmpty())
            .describedAs("Cache-Control of a missing asset")
            .doesNotContain("immutable")
    }

    @Test
    fun `should ask the browser to revalidate the dashboard document on every load`() {
        listOf("/", "/index.html", "/settings", "/browse/releases/gav").forEach { path ->
            // when: the dashboard document is requested
            val response = get(base, path)

            // then: the document is served again only after the browser has revalidated it
            assertThat(response.status).describedAs("status of GET $path").isEqualTo(200)
            assertThat(response.headerValue("Cache-Control"))
                .describedAs("Cache-Control of GET $path")
                .isEqualTo(REVALIDATE_CACHE_CONTROL)
        }
    }

    @Test
    fun `should open the dashboard document in its own browsing context group`() {
        listOf("/", "/index.html").forEach { path ->
            // when: the dashboard document is requested
            val response = get(base, path)

            // then: a popup opened from the dashboard does not share its browsing context group
            assertThat(response.headerValue("Cross-Origin-Opener-Policy"))
                .describedAs("Cross-Origin-Opener-Policy of GET $path")
                .isEqualTo(OPENER_POLICY)
        }
    }

    @Test
    fun `should not send the opener policy on a bundled asset`() {
        // when: a bundle is requested
        val response = get(base, bundlePath())

        // then: the header belongs to the document only
        assertThat(response.headerValue("Cross-Origin-Opener-Policy"))
            .describedAs("Cross-Origin-Opener-Policy of an asset")
            .isNull()
    }

    @Test
    fun `should not send the opener policy on an api response`() {
        // when: a public api endpoint answers
        val response = get(base, "/api/frontend/settings")

        // then: the header belongs to the document only
        assertThat(response.headerValue("Cross-Origin-Opener-Policy"))
            .describedAs("Cross-Origin-Opener-Policy of an api response")
            .isNull()
    }

}

/**
 * The repository "settings" collides with the dashboard route `/settings`. A Maven request under it must keep the
 * answer of Maven, so the dashboard caching of the document never applies to it.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class DashboardCachingSharedPathIntegrationTest : DashboardRoutesSpecification() {

    @Test
    fun `should keep the answer of Maven on a path whose name is also a dashboard path`() {
        // given: an artifact in a repository named like a dashboard route
        useDocument("settings", "com/example", "sample.jar", "jar-content", store = true)

        // when: the artifact is requested the way Maven requests it
        val response = get(base, "/settings/com/example/sample.jar")

        // then: the body is the artifact, and the dashboard document cache policy was not applied
        assertThat(response.status).describedAs("status").isEqualTo(200)
        assertThat(response.body).describedAs("body").isEqualTo("jar-content")
        assertThat(response.headerValue("Cache-Control"))
            .describedAs("Cache-Control of the Maven answer")
            .isNotEqualTo(REVALIDATE_CACHE_CONTROL)
    }

}
