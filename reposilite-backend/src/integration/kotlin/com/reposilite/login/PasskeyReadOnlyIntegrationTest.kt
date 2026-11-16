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
import io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.readonly.ReadOnlySetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * A passkey registration on an instance that the environment forces read-only. The sign-in stays available, so the test
 * signs in with the password and then asks for the registration options, which are a write of a challenge.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class PasskeyReadOnlyIntegrationTest : ReposiliteSpecification() {

    override fun readOnlySetting(): ReadOnlySetting =
        ReadOnlySetting(forced = true)

    private val password = "correct horse battery"

    /** A client without cookie management, so that the test decides which cookie a request carries. */
    private val http = Unirest.spawnInstance().also { it.config().enableCookieManagement(false) }

    private val loginFacade: LoginFacade
        get() = useFacade()

    @Test
    fun `a passkey registration on a read-only instance is refused with 503`() {
        // given: a user without a second factor, signed in through the password form
        checkNotNull(loginFacade.createUser("rosa-pk", "rosa-pk", password))
        val login = http.post("$base/api/auth/login")
            .header("Content-Type", "application/json")
            .body("""{"provider":"local","username":"rosa-pk","password":"$password"}""")
            .asString()
        val session = login.headers.get("Set-Cookie").map { it.substringBefore(";") }
        val csrf = session.single { it.startsWith("ingot_csrf=") }.substringAfter("=")

        // when: the registration options are requested
        val response = http.post("$base/api/identity/me/factors/passkey/options")
            .header("Cookie", session.joinToString("; "))
            .header("X-CSRF-Token", csrf)
            .asString()

        // then: the write is refused with a retry hint
        assertThat(response.status).`as`("status of the refused registration").isEqualTo(SERVICE_UNAVAILABLE.code)
        assertThat(response.headers.getFirst("Retry-After")).`as`("Retry-After header").isNotBlank()
    }

}
