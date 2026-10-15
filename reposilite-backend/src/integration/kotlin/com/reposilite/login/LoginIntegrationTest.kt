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
import com.reposilite.shared.ErrorResponse
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.UNAUTHORIZED
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.api.LoginProvidersResponse
import net.onelitefeather.ingot.identity.login.api.LoginResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalLoginIntegrationTest : ReposiliteSpecification() {

    private val password = "correct horse battery"

    private val loginFacade: LoginFacade
        get() = useFacade()

    private fun login(body: String) =
        Unirest.post("$base/api/auth/login")
            .header("Content-Type", "application/json")
            .body(body)

    @Test
    fun `publishes the local password provider`() {
        // when: the login providers are requested without any credentials
        val response = Unirest.get("$base/api/auth/providers").asJacksonObject(LoginProvidersResponse::class)

        // then: the local provider is announced with its label, and token login stays available
        assertSuccessResponse(OK, response) { providers ->
            assertThat(providers.providers.map { it.id to it.type }).containsExactly("local" to "LOCAL")
            assertThat(providers.providers.single().label).isEqualTo("Username and password")
            assertThat(providers.tokenLogin).isTrue()
        }
    }

    @Test
    fun `logs a user in with the right password`() {
        // given: a user with a password
        loginFacade.createUser("alice-login", "Alice", password)

        // when: the user logs in
        val response = login("""{"provider":"local","username":"alice-login","password":"$password"}""")
            .asJacksonObject(LoginResponse::class)

        // then: the principal is reported
        assertSuccessResponse(OK, response) { login ->
            assertThat(login.principal.name).isEqualTo("alice-login")
            assertThat(login.principal.kind).isEqualTo("USER")
            assertThat(login.principal.displayName).isEqualTo("Alice")
            assertThat(login.mustChangePassword).isFalse()
        }
    }

    @Test
    fun `answers a wrong password with 401`() {
        // given: a user with a password
        loginFacade.createUser("bob-login", "Bob", password)

        // when: the user logs in with a wrong password
        val response = login("""{"provider":"local","username":"bob-login","password":"wrong password entirely"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the request is rejected without saying which part was wrong
        assertErrorResponse(UNAUTHORIZED, response)
        assertThat(response.body.message).doesNotContain("bob-login")
    }

    @Test
    fun `refuses a disabled user with 403`() {
        // given: a disabled user with the right password
        loginFacade.createUser("carol-login", "Carol", password, disabled = true)

        // when: the user logs in
        val response = login("""{"provider":"local","username":"carol-login","password":"$password"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the login is refused
        assertErrorResponse(FORBIDDEN, response)
    }

    @Test
    fun `rejects an unknown provider with 400`() {
        // when: a login is sent to a provider that is not configured
        val response = login("""{"provider":"oidc","username":"alice","password":"$password"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the request is a bad request
        assertErrorResponse(BAD_REQUEST, response)
    }

    @Test
    fun `rejects a login without a password with 400`() {
        // when: a login arrives without a password
        val response = login("""{"provider":"local","username":"alice"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the request is a bad request
        assertErrorResponse(BAD_REQUEST, response)
    }

}
