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

package net.onelitefeather.ingot.identity.login.challenge

import com.reposilite.plugin.api.Event
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi

/**
 * A challenge that a dashboard login has to answer while its name is delayed by the account throttle, for example a CAPTCHA
 * or a proof of work. Ingot ships no provider and calls no third-party service by default.
 *
 * The provider is asked only for a dashboard login whose name the account throttle delays at that moment, never for a Basic,
 * Bearer or console client. A verified answer lets exactly one password check through and does not clear the delay. A
 * provider that throws is logged and the login gets the plain `429` of the throttle.
 *
 * Every method but [id] has a default, so that methods added later do not break a plugin built against this version.
 */
@ExperimentalIngotPluginApi
interface LoginChallengeProvider {

    /** The id of the provider: not blank and stable across restarts. Clients send it back in `challenge.provider`. */
    val id: String

    /**
     * The public values a client needs to present or solve a new challenge, such as a site key, a seed or a difficulty. They
     * are sent to the client unchanged as `parameters` and must never hold a secret. The default is an empty object.
     */
    fun parameters(context: LoginChallengeContext): Map<String, Any?> = emptyMap()

    /**
     * Whether [answer], the `challenge.answer` object of the login request, solves a challenge of this provider. A provider
     * that issues single-use challenges is responsible for refusing a replayed answer. The default verifies nothing.
     */
    fun verify(context: LoginChallengeContext, answer: Map<String, Any?>): Boolean = false

}

/** The delayed dashboard login a challenge is asked for or verified for: the name it signs in with and the client address. */
@ExperimentalIngotPluginApi
class LoginChallengeContext(
    val username: String,
    val address: String,
)

/**
 * Emitted once at start, after every plugin has initialised. A plugin registers its [LoginChallengeProvider] here. At most one
 * provider is used: the first registered, which follows the plugin load order. A later one is ignored with a warning.
 */
@ExperimentalIngotPluginApi
class LoginChallengeRegistrationEvent internal constructor(
    private val registrar: (LoginChallengeProvider) -> Unit,
) : Event {

    /** Registers [provider]. A refusal is logged as a warning and never throws. */
    fun register(provider: LoginChallengeProvider) {
        registrar(provider)
    }

}
