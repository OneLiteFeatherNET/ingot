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

package com.reposilite.token

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.TokenStateChangedEvent
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.infrastructure.InMemoryAccessTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TokenStateChangedEventTest {

    private val logger = InMemoryLogger()

    private fun facade(extensions: Extensions) = AccessTokenFacade(
        journalist = logger,
        temporaryRepository = InMemoryAccessTokenRepository(),
        persistentRepository = InMemoryAccessTokenRepository(),
        exportService = ExportService(),
        extensions = extensions,
        bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH,
    )

    @Test
    fun `creating a token emits exactly one token state change for its name`() {
        // given: a token facade whose events are recorded
        val events = mutableListOf<TokenStateChangedEvent>()
        val extensions = Extensions(logger)
        extensions.registerEvent { event: TokenStateChangedEvent -> events.add(event) }
        val facade = facade(extensions)

        // when: a token is created
        facade.createAccessToken(CreateAccessTokenRequest(TEMPORARY, "ci-bot"))

        // then: exactly one event names the token
        assertThat(events).containsExactly(TokenStateChangedEvent("ci-bot"))
    }

    @Test
    fun `the token listener registered with onTokenChanged still fires on a change`() {
        // given: a token facade with a local listener
        val changed = mutableListOf<String>()
        val facade = facade(Extensions(logger))
        facade.onTokenChanged { changed.add(it) }

        // when: a token is created
        facade.createAccessToken(CreateAccessTokenRequest(TEMPORARY, "ci-bot"))

        // then: the listener receives the token name once
        assertThat(changed).containsExactly("ci-bot")
    }
}
