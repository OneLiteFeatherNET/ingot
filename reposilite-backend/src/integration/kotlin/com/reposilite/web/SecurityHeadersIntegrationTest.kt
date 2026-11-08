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
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val SANDBOX_PROPERTY = "ingot.repository.sandbox"
private const val HSTS_PROPERTY = "ingot.hsts.maxAge"

/**
 * The server answers with the headers that keep a browser from misreading or embedding its pages. The dashboard
 * document gets a content security policy that its own build satisfies. Repository files keep the sandbox they have
 * always had, which an operator can switch off.
 */
internal abstract class SecurityHeadersSpecification : ReposiliteSpecification() {

    /** System properties a test sets before the server starts, and that [restoreProperties] removes again. */
    private val properties = mutableListOf<String>()

    protected fun useProperty(name: String, value: String) {
        properties += name
        System.setProperty(name, value)
    }

    @AfterEach
    fun restoreProperties() {
        properties.forEach { System.clearProperty(it) }
        properties.clear()
    }

    protected fun HttpResponse<*>.header(name: String): String? =
        headers.getFirst(name).takeUnless { it.isNullOrEmpty() }

    protected fun get(path: String, vararg headers: Pair<String, String>): HttpResponse<String> =
        Unirest.get("$base$path")
            .also { request -> headers.forEach { (name, value) -> request.header(name, value) } }
            .asString()

    protected fun storeDocument(file: String, content: String): String {
        useDocument("releases", "com/example", file, content, store = true)
        return "/releases/com/example/$file"
    }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SecurityHeadersIntegrationTest : SecurityHeadersSpecification() {

    @Test
    fun `should forbid framing and sniffing on api responses`() {
        // when: a public api endpoint answers
        val response = get("/api/frontend/settings")

        // then: the response cannot be embedded in a frame or reinterpreted by the browser
        assertThat(response.status).describedAs("status").isEqualTo(200)
        assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff")
        assertThat(response.header("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin")
        assertThat(response.header("X-Frame-Options")).isEqualTo("DENY")
        assertThat(response.header("Content-Security-Policy")).isEqualTo("frame-ancestors 'none'")
    }

    @Test
    fun `should answer the dashboard document with a content security policy that blocks inline scripts`() {
        // when: the dashboard is requested
        val response = get("/")

        // then: scripts and styles load from the server itself, nothing may embed the page
        assertThat(response.status).describedAs("status").isEqualTo(200)
        assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff")
        assertThat(response.header("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin")
        assertThat(response.header("X-Frame-Options")).isEqualTo("DENY")
        assertThat(response.header("Permissions-Policy"))
            .isEqualTo("accelerometer=(), camera=(), geolocation=(), gyroscope=(), microphone=(), payment=(), usb=()")
        assertThat(response.header("Content-Security-Policy")).isEqualTo(
            "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https: http:; font-src 'self' data:; connect-src 'self'; " +
                "object-src 'none'; base-uri 'self'; frame-ancestors 'none'"
        )
    }

    @Test
    fun `should not need an inline script or style for the dashboard document`() {
        // when: the dashboard document is rendered with its placeholders resolved
        val body = get("/").body

        // then: nothing in it is executed or styled inline, so the policy cannot block the page
        assertThat(body).describedAs("inline scripts").doesNotContainPattern("<script(?![^>]*\\ssrc=)[^>]*>")
        assertThat(body).describedAs("inline styles").doesNotContain("<style")
        assertThat(body).describedAs("inline event handlers").doesNotContainPattern("\\son[a-z]+=")
    }

    @Test
    fun `should add the same policy to the dashboard on a deep link`() {
        // when: a dashboard address is reloaded
        val response = get("/browse/missing/gav")

        // then: it is the same document with the same headers
        assertThat(response.header("Content-Security-Policy")).startsWith("default-src 'self'")
        assertThat(response.header("X-Frame-Options")).isEqualTo("DENY")
    }

    @Test
    fun `should keep the sandbox of a repository jar and add nosniff and referrer policy`() {
        // given: a stored archive
        val path = storeDocument("sample.jar", "jar-content")

        // when: it is downloaded
        val response = get(path)

        // then: the sandbox is the one it always had, without framing or permissions headers of the dashboard
        assertThat(response.status).describedAs("status").isEqualTo(200)
        assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff")
        assertThat(response.header("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin")
        assertThat(response.header("Content-Security-Policy")).isEqualTo("sandbox")
        assertThat(response.header("X-Frame-Options")).isNull()
        assertThat(response.header("Permissions-Policy")).isNull()
    }

    @Test
    fun `should keep the sandbox of a repository html file unchanged`() {
        // given: an uploaded page
        val path = storeDocument("page.html", "<script>alert(1)</script>")

        // when: it is requested
        val response = get(path)

        // then: the value is exactly what it was before this change
        assertThat(response.header("Content-Security-Policy")).isEqualTo("sandbox")
        assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff")
    }

    @Test
    fun `should sandbox the generated directory index page`() {
        // given: a directory with a file
        storeDocument("sample.jar", "jar-content")

        // when: the directory listing is requested
        val response = get("/releases/com/example")

        // then: the listing carries the sandbox like the files do
        assertThat(response.header("Content-Security-Policy")).isEqualTo("sandbox")
    }

    @Test
    fun `should not send strict transport security by default`() {
        // when: a request arrives over a forwarded https connection and nothing was configured
        val response = get("/api/frontend/settings", "X-Forwarded-Proto" to "https")

        // then: the header is absent, an operator has to opt in
        assertThat(response.header("Strict-Transport-Security")).isNull()
    }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RepositorySandboxDisabledIntegrationTest : SecurityHeadersSpecification() {

    override fun overrideParameters(parameters: ReposiliteParameters) {
        useProperty(SANDBOX_PROPERTY, "false")
    }

    @Test
    fun `should leave repository files without a sandbox when the operator switches it off`() {
        // given: an uploaded page
        val path = storeDocument("page.html", "<p>hello</p>")

        // when: it is requested
        val response = get(path)

        // then: no sandbox is added, the other headers stay
        assertThat(response.header("Content-Security-Policy")).isNull()
        assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff")
    }

    @Test
    fun `should leave the directory index without a sandbox when the operator switches it off`() {
        // given: a directory with a file
        storeDocument("sample.jar", "jar-content")

        // when: the listing is requested
        val response = get("/releases/com/example")

        // then: no sandbox
        assertThat(response.header("Content-Security-Policy")).isNull()
    }

    @Test
    fun `should keep the dashboard policy when the repository sandbox is off`() {
        // when: the dashboard is requested
        val response = get("/")

        // then: only repository content is affected by the switch
        assertThat(response.header("Content-Security-Policy")).startsWith("default-src 'self'")
    }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class StrictTransportSecurityIntegrationTest : SecurityHeadersSpecification() {

    override fun overrideParameters(parameters: ReposiliteParameters) {
        useProperty(HSTS_PROPERTY, "31536000")
    }

    @Test
    fun `should send strict transport security when a proxy terminated https`() {
        // when: a request arrives through a proxy that speaks https to the client
        val response = get("/api/frontend/settings", "X-Forwarded-Proto" to "https")

        // then: browsers are told to keep using https
        assertThat(response.header("Strict-Transport-Security")).isEqualTo("max-age=31536000")
    }

    @Test
    fun `should not send strict transport security on a plain http request`() {
        // when: the same endpoint is called over plain http
        val response = get("/api/frontend/settings")

        // then: the header would be ignored by browsers, so it is not sent
        assertThat(response.header("Strict-Transport-Security")).isNull()
    }

    @Test
    fun `should not send strict transport security when the proxy forwards http`() {
        // when: the proxy says the client used http
        val response = get("/api/frontend/settings", "X-Forwarded-Proto" to "http")

        // then: no header
        assertThat(response.header("Strict-Transport-Security")).isNull()
    }

}
