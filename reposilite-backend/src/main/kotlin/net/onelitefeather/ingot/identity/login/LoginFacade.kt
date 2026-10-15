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

package net.onelitefeather.ingot.identity.login

import com.reposilite.plugin.api.Facade
import net.onelitefeather.ingot.identity.model.PrincipalRecord

/**
 * What the rest of Ingot asks of human login. Without a database there are no users and no sessions, so every login
 * answers [LoginOutcome.UnknownProvider] and no provider is announced.
 */
class LoginFacade internal constructor(
    private val service: LoginService?,
) : Facade {

    fun providers(): List<LoginProvider> =
        service?.providers().orEmpty()

    fun setupRequired(): Boolean =
        service?.let { !it.hasUsers() } ?: false

    fun login(providerId: String, username: String, password: String, ip: String): LoginOutcome =
        service?.login(providerId, username, password, ip) ?: LoginOutcome.UnknownProvider

    /** Creates a human user with a local password. Returns `null` when the name is taken. */
    fun createUser(name: String, displayName: String, password: String, disabled: Boolean = false): PrincipalRecord? =
        service?.createUser(name, displayName, password, disabled)

    fun passwordProblem(password: String): String? =
        service?.passwordProblem(password)

}
