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

package com.reposilite.login

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.audit.api.AuditActor
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.OwnTokenOutcome
import net.onelitefeather.ingot.identity.login.api.SelfTokenRequest
import net.onelitefeather.ingot.readonly.ReadOnlySetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The security status on an instance that the environment forces read-only. The status is a read and stays available. Token
 * creation is a write and is refused with 503, like every other write, see design D19.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SecurityStatusReadOnlyIntegrationTest : ReposiliteSpecification() {

    override fun readOnlySetting(): ReadOnlySetting =
        ReadOnlySetting(forced = true)

    private val password = "correct horse battery"

    /** A client without cookie management, so that the test decides which cookie a request carries. */
    private val http = Unirest.spawnInstance().also { it.config().enableCookieManagement(false) }

    private val loginFacade: LoginFacade
        get() = useFacade()

    @Test
    fun `the security status answers on a read-only instance`() {
        // given: a user with a Basic token of their own
        val principalId = checkNotNull(loginFacade.createUser("status-ro-read", "status-ro-read", password)).id
        val token = (loginFacade.issueOwnToken(principalId, AuditActor.named("status-ro-read", "127.0.0.1"), SelfTokenRequest(name = "ro-read-ci")) as OwnTokenOutcome.Issued).dto

        // when: the status is read with the Basic credential
        val response = http.get("$base/api/identity/me/security")
            .basicAuth(token.token.name, token.secret)
            .asString()

        // then: the read is answered
        assertThat(response.status).`as`("status of a read on a read-only instance").isEqualTo(OK.code)
    }

    @Test
    fun `creating an own token on a read-only instance is refused with 503`() {
        // given: a user with a Basic token of their own
        val principalId = checkNotNull(loginFacade.createUser("status-ro-write", "status-ro-write", password)).id
        val token = (loginFacade.issueOwnToken(principalId, AuditActor.named("status-ro-write", "127.0.0.1"), SelfTokenRequest(name = "ro-write-ci")) as OwnTokenOutcome.Issued).dto

        // when: a token is created with the Basic credential
        val response = http.post("$base/api/identity/me/tokens")
            .header("Content-Type", "application/json")
            .basicAuth(token.token.name, token.secret)
            .body("""{"name":"ro-write-second"}""")
            .asString()

        // then: the write is refused with a retry hint
        assertThat(response.status).`as`("status of a token creation on a read-only instance").isEqualTo(SERVICE_UNAVAILABLE.code)
        assertThat(response.headers.getFirst("Retry-After")).`as`("Retry-After header").isNotBlank()
    }

}
