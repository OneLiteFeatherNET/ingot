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

package net.onelitefeather.ingot.readonly

import com.reposilite.ReposiliteRunner
import com.reposilite.ReposiliteSpecification
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * An instance that the environment forces read-only. Writes are refused with `503` after authentication and authorization,
 * reads and the exempt routes are served as before. Every test starts its own instance and touches only its own names.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ReadOnlyWriteRefusalIntegrationTest : ReposiliteSpecification() {

    override fun readOnlySetting(): ReadOnlySetting =
        ReadOnlySetting(forced = true)

    private val manager = ReposiliteRunner.DEFAULT_TOKEN

    @Test
    fun `should refuse a token update with 503 and Retry-After`() {
        // when: the manager sends a PUT that would create a token
        val response = Unirest.put("$base/api/tokens/refused-token")
            .basicAuth(manager.first, manager.second)
            .header("Accept", "application/problem+json")
            .header("Content-Type", "application/json")
            .body("{}")
            .asString()

        // then: the write is refused with a retry hint and a problem body
        assertThat(response.status).`as`("status of the refused update").isEqualTo(503)
        assertThat(response.headers.getFirst("Retry-After")).`as`("Retry-After header").isNotBlank()
        assertThat(response.headers.getFirst("Content-Type")).`as`("content type of the refusal").startsWith("application/problem+json")
    }

    @Test
    fun `should not store the token of a refused update`() {
        // given: a refused update of a token name
        Unirest.put("$base/api/tokens/refused-token")
            .basicAuth(manager.first, manager.second)
            .header("Content-Type", "application/json")
            .body("{}")
            .asString()

        // when: the token is read back
        val response = Unirest.get("$base/api/tokens/refused-token")
            .basicAuth(manager.first, manager.second)
            .asString()

        // then: nothing was stored, so the token is not served
        assertThat(response.status).`as`("status of the token read back").isNotEqualTo(200)
    }

    @Test
    fun `should refuse a token deletion and keep the manager token`() {
        // when: the manager deletes its own token while read-only
        val refused = Unirest.delete("$base/api/tokens/${manager.first}")
            .basicAuth(manager.first, manager.second)
            .asString()

        // then: the deletion is refused and the token still authenticates
        assertThat(refused.status).`as`("status of the refused deletion").isEqualTo(503)
        val stillThere = Unirest.get("$base/api/tokens/${manager.first}")
            .basicAuth(manager.first, manager.second)
            .asString()
        assertThat(stillThere.status).`as`("status of the token after the refused deletion").isEqualTo(200)
    }

    @Test
    fun `should refuse a Maven deploy and store no file`() {
        // when: the manager deploys a file to a release repository
        val refused = Unirest.put("$base/releases/org/example/refused/1.0/refused-1.0.jar")
            .basicAuth(manager.first, manager.second)
            .body("content")
            .asString()

        // then: the deploy is refused with a retry hint and the file is absent
        assertThat(refused.status).`as`("status of the refused deploy").isEqualTo(503)
        assertThat(refused.headers.getFirst("Retry-After")).`as`("Retry-After header").isNotBlank()
        val readBack = Unirest.get("$base/releases/org/example/refused/1.0/refused-1.0.jar")
            .basicAuth(manager.first, manager.second)
            .asString()
        assertThat(readBack.status).`as`("status of the file after the refused deploy").isEqualTo(404)
    }

    @Test
    fun `should refuse a Maven delete with 503 before the file is looked up`() {
        // when: the manager deletes a file that does not exist
        val response = Unirest.delete("$base/releases/org/example/missing/1.0/missing-1.0.jar")
            .basicAuth(manager.first, manager.second)
            .asString()

        // then: the delete is refused, so the file is never looked up
        assertThat(response.status).`as`("status of the refused delete").isEqualTo(503)
        assertThat(response.headers.getFirst("Retry-After")).`as`("Retry-After header").isNotBlank()
    }

    @Test
    fun `should serve a GET of the api as before`() {
        // when: the manager reads the token list
        val response = Unirest.get("$base/api/tokens")
            .basicAuth(manager.first, manager.second)
            .asString()

        // then: the read is served
        assertThat(response.status).`as`("status of the read").isEqualTo(200)
    }

    @Test
    fun `should not refuse a HEAD request`() {
        // when: the manager sends a HEAD to the token list
        val response = Unirest.head("$base/api/tokens")
            .basicAuth(manager.first, manager.second)
            .asString()

        // then: the HEAD is not refused, so it carries no 503 and no retry hint
        assertThat(response.status).`as`("status of the HEAD request").isNotEqualTo(503)
        assertThat(response.headers.getFirst("Retry-After")).`as`("Retry-After of the HEAD request").isEmpty()
    }

    @Test
    fun `should keep the health endpoint UP`() {
        // when: the health endpoint is read while read-only is on
        val response = Unirest.get("$base/api/status/health").asString()

        // then: the endpoint answers 200 with status UP
        assertThat(response.status).`as`("status of the health endpoint").isEqualTo(200)
        assertThat(response.body).`as`("body of the health endpoint").contains("UP")
    }

    @Test
    fun `should not refuse the login request`() {
        // when: a login is attempted with wrong credentials
        val response = Unirest.post("$base/api/auth/login")
            .header("Content-Type", "application/json")
            .body("""{"username":"nobody","password":"wrong"}""")
            .asString()

        // then: the login is answered by the login handler, which is never a 503 refusal
        assertThat(response.status).`as`("status of the login").isNotEqualTo(503)
        assertThat(response.headers.getFirst("Retry-After")).`as`("Retry-After of the login").isEmpty()
    }

    @Test
    fun `should not refuse the session refresh`() {
        // when: a refresh is sent without a refresh cookie
        val response = Unirest.post("$base/api/auth/refresh")
            .header("Content-Type", "application/json")
            .body("{}")
            .asString()

        // then: the refresh is answered by its own handler, which is never a 503 refusal
        assertThat(response.status).`as`("status of the refresh").isNotEqualTo(503)
    }

    @Test
    fun `should refuse a write that only shares the auth prefix`() {
        // when: the setup write, which is not an exempt route, is sent
        val response = Unirest.post("$base/api/auth/setup")
            .header("Content-Type", "application/json")
            .body("{}")
            .asString()

        // then: it is refused, because only the exact login and refresh routes are exempt
        assertThat(response.status).`as`("status of the setup write").isEqualTo(503)
    }

    @Test
    fun `should refuse logout, which is not on the exempt list`() {
        // when: a logout is sent while read-only is on
        val response = Unirest.post("$base/api/auth/logout")
            .header("Content-Type", "application/json")
            .body("{}")
            .asString()

        // then: the logout is refused, because only login, refresh and the toggle are exempt
        assertThat(response.status).`as`("status of the logout").isEqualTo(503)
    }

    @Test
    fun `should answer an anonymous write with 401 and not with 503`() {
        // when: a deploy is sent without credentials
        val response = Unirest.put("$base/releases/org/example/anonymous/1.0/anonymous-1.0.jar")
            .body("content")
            .asString()

        // then: authentication decides first, so the answer is 401
        assertThat(response.status).`as`("status of the anonymous deploy").isEqualTo(401)
    }

    @Test
    fun `should answer a token without permission with 403 and not with 503`() {
        // given: a token without any permission
        val (name, secret) = useAuth("reader-token", "reader-secret")

        // when: that token sends a deploy
        val response = Unirest.put("$base/releases/org/example/unauthorized/1.0/unauthorized-1.0.jar")
            .basicAuth(name, secret)
            .body("content")
            .asString()

        // then: authorization decides first, so the answer is 403
        assertThat(response.status).`as`("status of the unauthorized deploy").isEqualTo(403)
    }

}
