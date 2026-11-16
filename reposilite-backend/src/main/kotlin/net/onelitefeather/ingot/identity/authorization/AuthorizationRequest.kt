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

import net.onelitefeather.ingot.identity.evaluation.AccessRequest
import net.onelitefeather.ingot.identity.evaluation.Decision
import net.onelitefeather.ingot.identity.evaluation.PermissionEvaluator
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.NodePattern

/**
 * One question a check asks of the chain. Each shape is decided by the evaluator call the check made before the chain
 * existed, so routing a check through the chain changes no decision and no trace.
 */
internal sealed interface AuthorizationRequest {

    /** The owner of the token or the principal the question is about. */
    val subject: AccessSubject

    /** The decision a disabled subject receives for this question, named as the evaluator names it. */
    fun refusal(): Decision

    /** A request for one repository path, as a real request makes it. */
    data class Access(override val subject: AccessSubject, val request: AccessRequest) : AuthorizationRequest {
        override fun refusal(): Decision = PermissionEvaluator.refusalOf(request)
    }

    /** A concrete permission node, as the permission check of the identity API decides it. */
    data class Node(override val subject: AccessSubject, val node: NodePattern) : AuthorizationRequest {
        override fun refusal(): Decision = PermissionEvaluator.refusalOf(node)
    }

    /** A path inside a repository for the action read, deploy or delete, as the path check decides it. */
    data class RepositoryPath(
        override val subject: AccessSubject,
        val repository: String,
        val path: String,
        val action: String,
    ) : AuthorizationRequest {
        override fun refusal(): Decision = PermissionEvaluator.refusalOf(NodePattern.repositoryPath(repository, action, path))
    }

    /** An admin area, or the instance administrator when [area] is null. */
    data class AdminArea(override val subject: AccessSubject, val area: String?) : AuthorizationRequest {
        override fun refusal(): Decision =
            PermissionEvaluator.refusalOf(if (area == null) NodePattern.INSTANCE_ADMIN else NodePattern.admin(area))
    }
}
