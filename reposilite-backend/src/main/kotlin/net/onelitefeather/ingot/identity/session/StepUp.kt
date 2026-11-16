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

package net.onelitefeather.ingot.identity.session

import java.time.Duration
import java.time.Instant

/**
 * How long a second factor, or a password entry of a user without a factor, stays recent for the sensitive actions. This is
 * the default of `login.mfa.stepUpMinutes`; the setting itself arrives with the enrolment policy, which passes its value to
 * [isWithinStepUpWindow] and [stepUpAllows].
 */
internal const val STEP_UP_MINUTES = 5L

/** The step-up window of [STEP_UP_MINUTES]. */
internal val STEP_UP_WINDOW: Duration = Duration.ofMinutes(STEP_UP_MINUTES)

/** The request attribute that holds the verified claims of a browser session. Absent for Basic, Bearer and anonymous callers. */
const val SESSION_CLAIMS_ATTRIBUTE = "ingot.session.claims"

/** Whether [authenticatedAt] lies within [window] before [now]. A missing time is never within it. */
fun isWithinStepUpWindow(authenticatedAt: Instant?, now: Instant, window: Duration = STEP_UP_WINDOW): Boolean =
    authenticatedAt != null && Duration.between(authenticatedAt, now) <= window

/**
 * The step-up rule of a sensitive action, see design D8. A caller that is not a browser session ([session] is `null`) is never
 * asked for a second factor. A user with a factor needs a second factor completed inside the window. A user without one needs
 * nothing, except that a first factor enrolment needs the password entry to be inside the window.
 */
fun stepUpAllows(
    session: VerifiedSession?,
    hasFactor: Boolean,
    enrolment: Boolean,
    now: Instant,
    window: Duration = STEP_UP_WINDOW,
): Boolean {
    if (session == null) {
        return true
    }
    val fresh = isWithinStepUpWindow(session.authenticatedAt, now, window)
    return when {
        hasFactor -> session.mfa && fresh
        enrolment -> fresh
        else -> true
    }
}
