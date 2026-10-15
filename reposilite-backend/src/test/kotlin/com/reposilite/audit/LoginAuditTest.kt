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

package com.reposilite.audit

import com.reposilite.audit.api.AuditAction.LOGIN_FAILED
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.auth.AuthenticationFacade
import com.reposilite.auth.BasicAuthenticator
import com.reposilite.auth.Authenticator
import com.reposilite.auth.api.Credentials
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.EventListener
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.application.AccessTokenComponents
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.TreeSet

internal class LoginAuditTest {

    private val journalist = InMemoryLogger()
    private val emitted = mutableListOf<AuditEvent>()
    private val extensions = Extensions(journalist).also { it.registerEvent(AuditEvent::class.java, EventListener { event -> emitted += event }) }
    private val accessTokenFacade = AccessTokenComponents(journalist, database = null).accessTokenFacade()
    private val authenticationFacade = AuthenticationFacade(
        journalist = journalist,
        authenticators = TreeSet(Authenticator.priorityComparator).apply { add(BasicAuthenticator(accessTokenFacade)) },
        accessTokenFacade = accessTokenFacade,
        extensions = extensions,
    )

    @Test
    fun `a rejected login is recorded as a failed login of the attempted name and address`() {
        accessTokenFacade.createAccessToken(CreateAccessTokenRequest(type = TEMPORARY, name = "ci", secret = "real-secret"))
        emitted.clear()

        authenticationFacade.authenticateByCredentials(Credentials(ip = "203.0.113.7", name = "ci", secret = "guessed-secret"))

        val event = emitted.single()
        assertThat(event.action).isEqualTo(LOGIN_FAILED)
        assertThat(event.success).isFalse()
        assertThat(event.actor).isEqualTo(AuditActor.named("ci", "203.0.113.7"))
        assertThat(emitted.describe()).doesNotContain("guessed-secret", "real-secret")
    }

    @Test
    fun `a successful login records nothing`() {
        accessTokenFacade.createAccessToken(CreateAccessTokenRequest(type = TEMPORARY, name = "ci", secret = "real-secret"))
        emitted.clear()

        authenticationFacade.authenticateByCredentials(Credentials(ip = "203.0.113.7", name = "ci", secret = "real-secret"))

        assertThat(emitted).isEmpty()
    }

}
