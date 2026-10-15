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

    /** Too many recent failures for this user name, or for this address. The credentials were not checked. */
    data class Blocked(val retryAfterSeconds: Long) : LoginOutcome

    /** No provider with the requested id is configured. */
    data object UnknownProvider : LoginOutcome

}

/** The result of a password change or reset. */
sealed interface PasswordOutcome {

    data object Changed : PasswordOutcome

    /** A user changing their own password gave the wrong current one. */
    data object WrongCurrentPassword : PasswordOutcome

    /** The new password breaks the password rules. [problem] says which. */
    data class InvalidInput(val problem: String) : PasswordOutcome

    data object NotFound : PasswordOutcome

    /** Machine principals have no password. */
    data object NotAUser : PasswordOutcome

}

/** The result of the first administrator setup, see `LoginService.completeSetup`. */
sealed interface SetupOutcome {

    /** The first user exists now, as an instance administrator. */
    data class Created(val principal: PrincipalRecord) : SetupOutcome

    /** A user already exists, or the setup code was used up. */
    data object NotRequired : SetupOutcome

    /**
     * Another instance on the same database created the first administrator while this one still held its code. Only
     * the instance that claimed the setup creates the administrator; this one is a conflict, not a plain refusal.
     */
    data object AlreadyCompleted : SetupOutcome

    data object WrongCode : SetupOutcome

    /** Too many wrong codes from this address. The code was not checked. */
    data class Blocked(val retryAfterSeconds: Long) : SetupOutcome

    /** The user name is held by a principal already. */
    data object NameTaken : SetupOutcome

    /** The user name or the password does not meet the rules. [problem] says which. */
    data class InvalidInput(val problem: String) : SetupOutcome

}
