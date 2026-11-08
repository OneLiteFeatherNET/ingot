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

package net.onelitefeather.ingot.cluster

import com.google.common.base.Ticker
import com.reposilite.auth.AuthenticationFacade
import com.reposilite.auth.Authenticator
import com.reposilite.auth.BasicAuthenticator
import com.reposilite.auth.api.Credentials
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.TokenStateChangedEvent
import com.reposilite.shared.ErrorResponse
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.ExportService
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.infrastructure.InMemoryAccessTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import panda.std.Result
import panda.std.ResultAssertions.assertOk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A token changed on another instance must not leave its cached credentials working here. Instance A emits the change,
 * instance B receives it over an in-memory bus. Every wait is bounded by a latch, and the clock never moves.
 */
internal class ClusterCredentialInvalidationTest {

    private val hub = InMemoryClusterHub()
    private val now = Instant.parse("2026-11-08T10:15:30Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val frozenTicker = object : Ticker() {
        override fun read(): Long = 0L
    }

    @AfterEach
    fun closeHub() {
        hub.close()
    }

    /** The authenticator of instance B, counting every request that reaches it. */
    private class CountingAuthenticator(private val delegate: Authenticator) : Authenticator {

        var calls = 0

        override fun authenticate(credentials: Credentials): Result<AccessTokenDto, ErrorResponse> {
            calls++
            return delegate.authenticate(credentials)
        }

        override fun enabled(): Boolean = delegate.enabled()

        override fun priority(): Double = delegate.priority()

        override fun realm(): String = delegate.realm()

    }

    private class Receiver(
        val extensions: Extensions,
        val plugin: ClusterPlugin,
    )

    private fun startInstance(instanceId: String): Receiver {
        val variables = mapOf(
            "INGOT_CLUSTER_URL" to "nats://hub.example:4222",
            "INGOT_CLUSTER_INSTANCE_ID" to instanceId,
        )
        val extensions = Extensions(InMemoryLogger())
        val plugin = ClusterPlugin(
            sources = ClusterSources(property = { null }, variable = variables::get, hostName = { "host" }),
            transports = mapOf("nats" to hub.factory()),
            clusterIdentifier = { "cluster-1" },
            clock = clock,
        )
        extensions.registerPlugin(plugin)
        plugin.initialize()
        return Receiver(extensions, plugin)
    }

    @Test
    fun `a token change received from another instance makes the next request check the database`() {
        // given: a token on instance B whose credentials are cached by a successful request
        val journalist = InMemoryLogger()
        val receiver = startInstance("ingot-b")
        val tokens = AccessTokenFacade(
            journalist = journalist,
            temporaryRepository = InMemoryAccessTokenRepository(clock),
            persistentRepository = InMemoryAccessTokenRepository(clock),
            exportService = ExportService(),
            bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH,
        )
        val counting = CountingAuthenticator(BasicAuthenticator(tokens, clock))
        val authentication = AuthenticationFacade(
            journalist = journalist,
            authenticators = sortedSetOf(Authenticator.priorityComparator, counting),
            accessTokenFacade = tokens,
            clock = clock,
            ticker = frozenTicker,
        )
        receiver.extensions.registerFacade(authentication)
        tokens.createAccessToken(CreateAccessTokenRequest(type = TEMPORARY, name = "deploy-bot", secret = "secret", expiresAt = null))
        assertOk(authentication.authenticateByCredentials(Credentials("host", "deploy-bot", "secret")))
        assertThat(counting.calls).`as`("authenticator calls before the change").isEqualTo(1)

        // when: instance A changes the token, and instance B applies the received change
        val delivered = CountDownLatch(1)
        receiver.plugin.onReceived(ClusterEventType.TOKEN_CHANGED) { delivered.countDown() }
        val source = startInstance("ingot-a")
        source.extensions.emitEvent(TokenStateChangedEvent("deploy-bot"))
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("token change applied within five seconds").isTrue()

        // then: the same credentials are checked again by the authenticator instead of being served from the cache
        assertOk(authentication.authenticateByCredentials(Credentials("host", "deploy-bot", "secret")))
        assertThat(counting.calls).`as`("authenticator calls after the received change").isEqualTo(2)
    }

    @Test
    fun `applying a received token change publishes nothing on the receiving instance`() {
        // given: an instance B that owns a cached credential and a plugin that receives the change
        val journalist = InMemoryLogger()
        val receiver = startInstance("ingot-b")
        val tokens = AccessTokenFacade(
            journalist = journalist,
            temporaryRepository = InMemoryAccessTokenRepository(clock),
            persistentRepository = InMemoryAccessTokenRepository(clock),
            exportService = ExportService(),
            bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH,
        )
        val authentication = AuthenticationFacade(
            journalist = journalist,
            authenticators = sortedSetOf(Authenticator.priorityComparator, BasicAuthenticator(tokens, clock)),
            accessTokenFacade = tokens,
            clock = clock,
            ticker = frozenTicker,
        )
        receiver.extensions.registerFacade(authentication)
        val delivered = CountDownLatch(1)
        receiver.plugin.onReceived(ClusterEventType.TOKEN_CHANGED) { delivered.countDown() }

        // when: the change arrives from instance A
        val source = startInstance("ingot-a")
        source.extensions.emitEvent(TokenStateChangedEvent("deploy-bot"))
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("token change applied within five seconds").isTrue()

        // then: applying it caused no event of its own on instance B
        assertThat(hub.publishedBy("ingot-b")).isZero()
    }
}
