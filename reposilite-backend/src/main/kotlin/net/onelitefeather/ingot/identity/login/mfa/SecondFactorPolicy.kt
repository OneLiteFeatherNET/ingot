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

package net.onelitefeather.ingot.identity.login.mfa

import java.time.Clock
import java.time.Duration
import java.time.Instant
import net.onelitefeather.ingot.identity.evaluation.PermissionEvaluator
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.session.SessionScope
import net.onelitefeather.ingot.identity.storage.IdentityStore

/**
 * Who the second factor policy covers, and the scope of a session (design D10). A covered user is an instance administrator,
 * directly or through a team, or a direct member of a team that `login.mfa.requiredForTeams` names.
 *
 * Coverage is evaluated at every sign-in and at every issue of a session. The first time it holds, the instant is recorded as the
 * user's `requiredSince`, and the grace period counts from there. Nothing clears it, so a policy that is switched off and on again
 * does not give the user a new grace.
 */
internal class SecondFactorPolicy(
    private val identities: IdentityStore,
    /** The login settings, read on every call so that a change applies at once. */
    private val settings: () -> LoginSettings,
    private val clock: Clock,
    /** Whether the user holds a second factor. */
    private val secondFactorRequired: (Int) -> Boolean,
    /**
     * Whether the user has a local password. A covered account without one, such as a provider account, is not restricted after the
     * grace: it cannot enrol a factor yet, so the restriction would lock it out. This is the open question of the change.
     */
    private val hasLocalPassword: (Int) -> Boolean,
) {

    /**
     * Whether the policy covers [principal] at [at]. A covered user gets `requiredSince` recorded, once, as [at] when it is the first
     * time. Without a covering rule the answer is `false` and nothing is read from the database.
     */
    fun covers(principal: PrincipalRecord, at: Instant = clock.instant()): Boolean {
        val policy = settings().mfa
        if (!policy.requiredForAdmins && policy.requiredForTeams.isEmpty()) {
            return false
        }
        val subject = identities.loadAccess(principal, tokenNodes = null)
        val covered = (policy.requiredForAdmins && PermissionEvaluator.isInstanceAdmin(subject)) ||
            subject.teams.any { team -> policy.requiredForTeams.any { it.equals(team, ignoreCase = true) } }
        if (covered) {
            identities.markRequiredSince(principal.id, at)
        }
        return covered
    }

    /**
     * The scope of a session of [principal] issued at [now]. The coverage is recorded at [now] too, so that a grace of zero days
     * restricts the session that starts now. A covered user with a factor, or without a local password, keeps the full scope. A
     * covered user without a factor keeps it until the grace period, which ends at `requiredSince + graceDays`, and gets the
     * restricted scope from that instant on.
     */
    fun scopeOf(principal: PrincipalRecord, now: Instant): SessionScope {
        if (!covers(principal, now)) {
            return SessionScope.FULL
        }
        if (secondFactorRequired(principal.id) || !hasLocalPassword(principal.id)) {
            return SessionScope.FULL
        }
        val since = identities.requiredSinceOf(principal.id) ?: return SessionScope.FULL
        val graceEnd = since.plus(Duration.ofDays(settings().mfa.graceDays))
        return if (now.isBefore(graceEnd)) SessionScope.FULL else SessionScope.ENROL
    }

}

/**
 * What the login gate of a sign-in through an external provider decides, see `LoginFacade.externalSecondFactor`. [Pass] lets the
 * session start, [Challenge] starts a sign-in challenge that the user completes with an Ingot factor, and [Refused] stops the sign-in.
 */
sealed interface ExternalSecondFactor {

    data object Pass : ExternalSecondFactor

    /** [challenge] is the id of the sign-in challenge, to be handed to the browser in the challenge cookie. */
    data class Challenge(val challenge: String) : ExternalSecondFactor

    data object Refused : ExternalSecondFactor

}

/** The answer of a first factor passkey sign-in start, see `LoginFacade.startPasskeyFirstFactor`. */
sealed interface FirstFactorPasskey {

    /** The switch `login.mfa.passkeyFirstFactor` is off. Nothing is stored. */
    data object Disabled : FirstFactorPasskey

    data class Started(val challenge: PasskeyChallenge) : FirstFactorPasskey

    /** An unknown or disabled user, or a user without a passkey. The answer is the same for all three. */
    data object Unavailable : FirstFactorPasskey

}
