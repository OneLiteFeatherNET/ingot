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
import net.onelitefeather.ingot.identity.evaluation.PermissionEvaluator
import net.onelitefeather.ingot.identity.model.NodePattern

/**
 * The existing evaluator as a stage. It changes no logic: it asks the evaluator the question the request asks, and maps the
 * answer. An allowance is Allow. A decision that named a rule which denied is Deny. A decision that named no rule, the
 * default denial, is NoOpinion, so that a later stage may still decide. The token narrowing and the instance admin
 * shortcut stay inside the evaluator.
 */
internal object BuiltInAuthorizer : Authorizer {

    override fun decide(request: AuthorizationRequest): AuthorizerDecision {
        val decision = evaluate(request)
        return when {
            decision.allowed -> AuthorizerDecision.Allow(decision)
            decision.decidingKind == null -> AuthorizerDecision.NoOpinion(decision)
            else -> AuthorizerDecision.Deny(decision)
        }
    }

    private fun evaluate(request: AuthorizationRequest): Decision =
        when (request) {
            is AuthorizationRequest.Access -> PermissionEvaluator.decideAccess(request.subject, request.request)
            is AuthorizationRequest.Node -> PermissionEvaluator.decide(request.subject, request.node)
            is AuthorizationRequest.RepositoryPath ->
                PermissionEvaluator.decidePath(request.subject, request.repository, request.path, request.action)
            is AuthorizationRequest.AdminArea -> request.area?.let { PermissionEvaluator.decide(request.subject, NodePattern.admin(it)) }
                ?: PermissionEvaluator.decideInstanceAdmin(request.subject)
        }
}
