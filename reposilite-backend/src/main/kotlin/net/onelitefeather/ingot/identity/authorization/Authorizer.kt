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
package net.onelitefeather.ingot.identity.authorization

import net.onelitefeather.ingot.identity.evaluation.Decision

/** One stage of the chain: it answers a question with Allow, Deny or NoOpinion, and never throws for a refusal. */
internal fun interface Authorizer {
    fun decide(request: AuthorizationRequest): AuthorizerDecision
}

/**
 * The answer of one stage. [decision] is the decision the stage took, with the trace it produced, which the chain passes on
 * unchanged. A stage with no opinion may carry no decision.
 */
internal sealed interface AuthorizerDecision {
    val decision: Decision?

    /** The stage allows the request. The chain stops here. */
    data class Allow(override val decision: Decision) : AuthorizerDecision

    /** The stage denies the request explicitly. The chain stops here, and nothing later may allow it. */
    data class Deny(override val decision: Decision) : AuthorizerDecision

    /** The stage has no rule for the request. The chain asks the next stage. */
    data class NoOpinion(override val decision: Decision? = null) : AuthorizerDecision
}

/** The answer of the whole chain: whether the request is allowed, and the decision that answers it. */
internal data class AuthorizationOutcome(val allowed: Boolean, val decision: Decision)
