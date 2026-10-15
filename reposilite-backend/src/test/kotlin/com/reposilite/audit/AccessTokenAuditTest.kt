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

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditAction.TOKEN_CREATED
import com.reposilite.audit.api.AuditAction.TOKEN_DELETED
import com.reposilite.audit.api.AuditAction.TOKEN_ROUTES_CHANGED
import com.reposilite.audit.api.AuditAction.TOKEN_SECRET_REGENERATED
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.EventListener
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.AccessTokenType.PERSISTENT
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.RoutePermission.READ
import com.reposilite.token.application.AccessTokenComponents
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.api.SecretType.RAW
import com.reposilite.token.api.UpdateAccessTokenRequest
import com.reposilite.token.Route
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Token changes are recorded where they happen, so every path that changes a token emits the event
 * with the actor that asked for it. Secrets are part of the request and must never reach an event.
 */
internal class AccessTokenAuditTest {

    private val journalist = InMemoryLogger()
    private val emitted = mutableListOf<AuditEvent>()
    private val facade: AccessTokenFacade = AccessTokenComponents(journalist, database = null).accessTokenFacade(
        extensions = Extensions(journalist).also { extensions ->
            extensions.registerEvent(AuditEvent::class.java, EventListener { emitted += it })
        },
        bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH,
    )

    private val admin = AuditActor.named("admin", "10.0.0.2")

    @Test
    fun `creating a token records its name and type without its secret`() {
        facade.createAccessToken(
            CreateAccessTokenRequest(type = TEMPORARY, name = "deploy-bot", secret = "super-secret-value", secretType = RAW),
            actor = admin,
        )

        val event = emitted.single { it.action == TOKEN_CREATED }
        assertThat(event.actor).isEqualTo(admin)
        assertThat(event.details).containsEntry("token", "deploy-bot").containsEntry("replaced", false)
        assertThat(emitted.describe()).doesNotContain("super-secret-value")
    }

    @Test
    fun `recreating a token with an existing name is recorded as a replacement`() {
        facade.createAccessToken(CreateAccessTokenRequest(type = TEMPORARY, name = "deploy-bot", secret = "first-secret"), actor = admin)
        emitted.clear()

        facade.createAccessToken(CreateAccessTokenRequest(type = TEMPORARY, name = "deploy-bot", secret = "second-secret"), actor = admin)

        assertThat(emitted.single { it.action == TOKEN_CREATED }.details).containsEntry("replaced", true)
        assertThat(emitted.describe()).doesNotContain("first-secret", "second-secret")
    }

    @Test
    fun `a persistent token created through the api is recorded`() {
        facade.createAccessToken(
            CreateAccessTokenRequest(type = PERSISTENT, name = "ci-bot", secret = "ci-secret"),
            actor = admin,
        )

        assertThat(emitted.single { it.action == TOKEN_CREATED }.details).containsEntry("token", "ci-bot")
    }

    @Test
    fun `a temporary token created by the startup bootstrap records nothing`() {
        val (token) = facade.createAccessToken(
            CreateAccessTokenRequest(type = TEMPORARY, name = "startup-bot", secret = "startup-secret"),
            audited = false,
        )
        facade.addPermission(token.identifier, MANAGER, audited = false)

        assertThat(emitted).isEmpty()
    }

    @Test
    fun `regenerating a secret records the event without the new secret`() {
        val token = createToken("deploy-bot")
        emitted.clear()

        facade.regenerateAccessToken(token, secret = "brand-new-secret", secretType = RAW, actor = admin)

        val event = emitted.single()
        assertThat(event.action).isEqualTo(TOKEN_SECRET_REGENERATED)
        assertThat(event.details).containsEntry("token", "deploy-bot")
        assertThat(emitted.describe()).doesNotContain("brand-new-secret")
    }

    @Test
    fun `deleting a token records the name of the deleted token`() {
        val token = createToken("old-bot")
        emitted.clear()

        facade.deleteToken(token.identifier, actor = admin)

        val event = emitted.single()
        assertThat(event.action).isEqualTo(TOKEN_DELETED)
        assertThat(event.details).containsEntry("token", "old-bot")
    }

    @Test
    fun `changing routes records the routes that were added and removed`() {
        val token = facade.createAccessToken(
            CreateAccessTokenRequest(
                type = TEMPORARY,
                name = "reader",
                routes = setOf(CreateAccessTokenRequest.Route("/public", setOf(READ))),
            ),
            actor = admin,
        ).accessToken
        emitted.clear()

        facade.updateAccessToken(
            token.name,
            UpdateAccessTokenRequest(routes = setOf(CreateAccessTokenRequest.Route("/private", setOf(READ)))),
            actor = admin,
        )

        val event = emitted.single { it.action == TOKEN_ROUTES_CHANGED }
        assertThat(event.details).containsEntry("token", "reader")
        assertThat(event.details["routesAdded"]).isEqualTo(listOf("/private (${READ.identifier})"))
        assertThat(event.details["routesRemoved"]).isEqualTo(listOf("/public (${READ.identifier})"))
    }

    @Test
    fun `granting a permission records a change of access rights`() {
        val token = createToken("manager-bot")
        emitted.clear()

        facade.addPermission(token.identifier, MANAGER, actor = admin)

        val event = emitted.single { it.action == TOKEN_ROUTES_CHANGED }
        assertThat(event.details["permissionsAdded"]).isEqualTo(listOf(MANAGER.identifier))
    }

    @Test
    fun `a change made without an actor is recorded as the system`() {
        val token = createToken("console-bot")
        emitted.clear()

        facade.addRoute(token.identifier, Route("/console", READ))

        assertThat(emitted.single().actor).isEqualTo(AuditActor.SYSTEM_ACTOR)
    }

    @Test
    fun `an update that only changes the description records nothing`() {
        val token = createToken("quiet-bot")
        emitted.clear()

        facade.updateAccessToken(token.name, UpdateAccessTokenRequest(description = "new text"), actor = admin)

        assertThat(emitted.map(AuditEvent::action)).doesNotContain(TOKEN_ROUTES_CHANGED, TOKEN_CREATED)
    }

    private fun createToken(name: String): AccessTokenDto =
        facade.createAccessToken(CreateAccessTokenRequest(type = TEMPORARY, name = name, secret = "secret-$name"), actor = admin).accessToken

}
