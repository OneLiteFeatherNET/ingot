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

/**
 * Takes every check decision. The order is fixed: a disabled principal is refused before any stage, then the restriction
 * stages, the built-in authorizer, the binding stages and the plugin stages. The first Allow or Deny decides. When every
 * stage has no opinion the request is denied, under the decision of the last stage that gave one.
 *
 * The stages are immutable and the chain holds no other state, so one instance serves every caller.
 */
internal class AuthorizationChain(
    private val restrictions: List<Authorizer> = emptyList(),
    private val builtIn: Authorizer,
    private val bindings: List<Authorizer> = emptyList(),
    private val plugins: List<Authorizer> = emptyList(),
) {

    fun decide(request: AuthorizationRequest): AuthorizationOutcome {
        if (request.subject.disabled) return AuthorizationOutcome(allowed = false, decision = request.refusal())

        var lastOpinion = request.refusal()
        for (stage in restrictions + builtIn + bindings + plugins) {
            when (val answer = stage.decide(request)) {
                is AuthorizerDecision.Allow -> return AuthorizationOutcome(allowed = true, decision = answer.decision)
                is AuthorizerDecision.Deny -> return AuthorizationOutcome(allowed = false, decision = answer.decision)
                is AuthorizerDecision.NoOpinion -> answer.decision?.let { lastOpinion = it }
            }
        }
        return AuthorizationOutcome(allowed = false, decision = lastOpinion)
    }

    companion object {
        /** The chain of the running system: no restrictions, bindings or plugins yet, and the built-in evaluator. */
        fun standard(): AuthorizationChain = AuthorizationChain(builtIn = BuiltInAuthorizer)
    }
}
