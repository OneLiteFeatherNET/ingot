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

package net.onelitefeather.ingot.identity.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class FetchMetadataPolicyTest {

    private fun request(
        method: String = "GET",
        path: String = "/api/auth/me",
        authorization: Boolean = false,
        session: String? = "session-jwt",
        refresh: String? = null,
        site: String? = "cross-site",
        mode: String? = "cors",
        dest: String? = "empty",
    ) = FetchMetadataPolicy.Request(
        method = method,
        path = path,
        hasAuthorizationHeader = authorization,
        sessionCookie = session,
        refreshCookie = refresh,
        secFetchSite = site,
        secFetchMode = mode,
        secFetchDest = dest,
    )

    @Test
    fun `refuses a cross-site read that carries the session cookie`() {
        assertThat(FetchMetadataPolicy.violation(request(site = "cross-site")))
            .describedAs("a page on another site must not read an api answer with the victim's cookie")
            .isTrue()
    }

    @Test
    fun `refuses a cross-site change that carries the session cookie`() {
        assertThat(FetchMetadataPolicy.violation(request(method = "POST", site = "cross-site")))
            .describedAs("the rule applies to changes as well as reads")
            .isTrue()
    }

    @Test
    fun `refuses a same-site read from a sibling subdomain`() {
        assertThat(FetchMetadataPolicy.violation(request(site = "same-site")))
            .describedAs("a sibling subdomain is same-site, and it can set the csrf cookie the double-submit check trusts")
            .isTrue()
    }

    @Test
    fun `refuses a same-site change from a sibling subdomain`() {
        assertThat(FetchMetadataPolicy.violation(request(method = "POST", site = "same-site")))
            .describedAs("a sibling subdomain must not change state with the cookies of the dashboard")
            .isTrue()
    }

    @Test
    fun `allows a same-origin request`() {
        assertThat(FetchMetadataPolicy.violation(request(site = "same-origin")))
            .describedAs("the dashboard calls its own api from its own origin")
            .isFalse()
    }

    @Test
    fun `allows a request the user typed in the address bar`() {
        assertThat(FetchMetadataPolicy.violation(request(site = "none", mode = "navigate", dest = "document")))
            .describedAs("a browser-initiated request has no initiator site")
            .isFalse()
    }

    @Test
    fun `allows a request without a Sec-Fetch-Site header`() {
        assertThat(FetchMetadataPolicy.violation(request(site = null, mode = null, dest = null)))
            .describedAs("clients that send no fetch metadata keep working")
            .isFalse()
    }

    @Test
    fun `allows a Sec-Fetch-Site value that is none of the refused ones`() {
        assertThat(FetchMetadataPolicy.violation(request(site = "some-future-value")))
            .describedAs("only same-site and cross-site are refused")
            .isFalse()
    }

    @Test
    fun `allows a top-level navigation from another site`() {
        assertThat(FetchMetadataPolicy.violation(request(site = "cross-site", mode = "navigate", dest = "document")))
            .describedAs("the identity provider redirects the browser back to the callback as a navigation")
            .isFalse()
    }

    @Test
    fun `refuses a cross-site navigation whose destination is a frame`() {
        assertThat(FetchMetadataPolicy.violation(request(site = "cross-site", mode = "navigate", dest = "iframe")))
            .describedAs("an embedded frame is not a top-level navigation")
            .isTrue()
    }

    @Test
    fun `refuses a cross-site navigation without a destination`() {
        assertThat(FetchMetadataPolicy.violation(request(site = "cross-site", mode = "navigate", dest = null)))
            .describedAs("the exemption needs both the mode and the destination")
            .isTrue()
    }

    @Test
    fun `exempts a request with an Authorization header even from another site`() {
        assertThat(FetchMetadataPolicy.violation(request(authorization = true, site = "cross-site")))
            .describedAs("a bearer token is not a cookie, so the browser attached nothing on its own")
            .isFalse()
    }

    @Test
    fun `exempts a cross-site request without a session or refresh cookie`() {
        assertThat(FetchMetadataPolicy.violation(request(session = null, refresh = null, site = "cross-site")))
            .describedAs("without a cookie there is no ambient credential to protect")
            .isFalse()
    }

    @Test
    fun `refuses a cross-site request that carries only the refresh cookie`() {
        assertThat(FetchMetadataPolicy.violation(request(session = null, refresh = "refresh-token", site = "cross-site")))
            .describedAs("the refresh cookie authenticates a session as well")
            .isTrue()
    }

    @Test
    fun `refuses a cross-site request under the api at the root`() {
        assertThat(FetchMetadataPolicy.violation(request(path = "/api/identity/me/tokens", method = "POST"), basePath = "/"))
            .isTrue()
    }

    @Test
    fun `refuses a cross-site request to the api root itself`() {
        assertThat(FetchMetadataPolicy.violation(request(path = "/api")))
            .isTrue()
    }

    @Test
    fun `refuses a cross-site request under the api below the configured base path`() {
        assertThat(FetchMetadataPolicy.violation(request(path = "/ingot/api/auth/me"), basePath = "/ingot"))
            .describedAs("the base path is part of the public address")
            .isTrue()
    }

    @Test
    fun `refuses a cross-site request under the api at the root when a base path is configured`() {
        assertThat(FetchMetadataPolicy.violation(request(path = "/api/auth/me"), basePath = "/ingot"))
            .describedAs("a reverse proxy may strip the base path before the request arrives")
            .isTrue()
    }

    @Test
    fun `allows a cross-site request to a look-alike path`() {
        assertThat(FetchMetadataPolicy.violation(request(path = "/apix/status")))
            .describedAs("only the api prefix is in scope")
            .isFalse()
    }

    @Test
    fun `allows a cross-site request to a repository path that contains the api prefix`() {
        assertThat(FetchMetadataPolicy.violation(request(path = "/releases/api/auth/me")))
            .describedAs("a repository may be called api, and it is not the api of this server")
            .isFalse()
    }

    @Test
    fun `allows a cross-site request to a path under another base path`() {
        assertThat(FetchMetadataPolicy.violation(request(path = "/other/api/auth/me"), basePath = "/ingot"))
            .isFalse()
    }
}
