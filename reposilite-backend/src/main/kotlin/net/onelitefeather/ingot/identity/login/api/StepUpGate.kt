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

package net.onelitefeather.ingot.identity.login.api

import com.reposilite.shared.ContextDsl
import com.reposilite.shared.ErrorProblem
import com.reposilite.shared.ErrorResponse
import io.javalin.http.HttpStatus.FORBIDDEN
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.mfa.SecondFactorMethod
import panda.std.Result
import panda.std.asError
import panda.std.asSuccess

/** The problem of a sensitive action that needs a recent second factor. The methods of the user are listed with it. */
internal const val STEP_UP_REQUIRED = "step_up_required"

/** The description of a 403 that a sensitive action can answer with [STEP_UP_REQUIRED]. */
internal const val STEP_UP_RESPONSE = "step_up_required when a recent second factor is needed first, see /api/identity/me/step-up"

/**
 * The step-up gate of a sensitive action, see design D8. A route calls it once the caller is known. A caller that is not a browser
 * session passes unchanged, so Basic and Bearer callers keep working without a prompt. A session of a user with a factor needs a
 * second factor completed inside the step-up window. A first factor enrolment needs the password entry to be inside it.
 *
 * Refused actions answer 403 with [STEP_UP_REQUIRED] and the second factor methods of the user.
 */
internal fun ContextDsl<*>.requireStepUp(facade: LoginFacade?, enrolment: Boolean = false): Result<Unit, ErrorResponse> {
    val session = sessionClaims() ?: return Unit.asSuccess()
    val factors = facade ?: return Unit.asSuccess()
    if (factors.stepUpAllowed(session, enrolment)) {
        return Unit.asSuccess()
    }
    return stepUpRefusal(factors, session.principalId)
}

/**
 * The gate of a change to the access tokens of [principalId], see design D9. A browser session passes the gate of [requireStepUp]. A Basic or
 * Bearer caller passes only when the user has no second factor: a token of a user with a factor must not mint, rotate or delete tokens.
 * Reads of the tokens are not gated here.
 */
internal fun ContextDsl<*>.requireTokenStepUp(facade: LoginFacade, principalId: Int): Result<Unit, ErrorResponse> {
    if (sessionClaims() != null) {
        return requireStepUp(facade)
    }
    if (!facade.hasSecondFactor(principalId)) {
        return Unit.asSuccess()
    }
    return stepUpRefusal(facade, principalId)
}

/** The 403 of a refused sensitive action, with the second factor methods of the user. */
private fun stepUpRefusal(facade: LoginFacade, principalId: Int): Result<Unit, ErrorResponse> {
    val methods = facade.secondFactorMethodsOf(principalId)
    return ErrorResponse(
        FORBIDDEN.code,
        STEP_UP_REQUIRED,
        methods.map { ErrorProblem(node = it.wireName(), message = "Confirm with this second factor at /api/identity/me/step-up") },
    ).asError()
}

/** The name of a second factor method on the wire, as the login answer and the step-up answer name it. */
internal fun SecondFactorMethod.wireName(): String = when (this) {
    SecondFactorMethod.AUTHENTICATOR -> METHOD_AUTHENTICATOR
    SecondFactorMethod.PASSKEY -> METHOD_PASSKEY
    SecondFactorMethod.RECOVERY_CODE -> METHOD_RECOVERY_CODE
}
