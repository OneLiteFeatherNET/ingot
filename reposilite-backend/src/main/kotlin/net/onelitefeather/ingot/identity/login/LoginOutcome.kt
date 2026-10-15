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

import net.onelitefeather.ingot.identity.model.PrincipalRecord

/** The result of one login attempt with one provider. */
sealed interface LoginOutcome {

    /** The credentials are valid and the principal may log in. [mustChangePassword] asks the client to change it first. */
    data class Succeeded(val principal: PrincipalRecord, val mustChangePassword: Boolean) : LoginOutcome

    /** The credentials are wrong or the user is unknown. Both cases look the same on purpose. */
    data object Rejected : LoginOutcome

    /** The credentials are right, but the principal is disabled. */
    data object Disabled : LoginOutcome

    /** Too many recent failures for this user name and address. The credentials were not checked. */
    data object Blocked : LoginOutcome

    /** No provider with the requested id is configured. */
    data object UnknownProvider : LoginOutcome

}
