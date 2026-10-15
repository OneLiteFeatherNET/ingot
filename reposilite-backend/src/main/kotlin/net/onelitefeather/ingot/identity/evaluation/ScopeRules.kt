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
package net.onelitefeather.ingot.identity.evaluation

import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.NodeRule
import net.onelitefeather.ingot.identity.model.SourcedRule

/**
 * Turns the scopes of a token into its node list (identity model, permission nodes, decision D7). A scope narrows the owner
 * to the nodes it names, exactly as the scope did: READ to read, WRITE to deploy (WRITE never covered DELETE, which has its
 * own scope), DELETE to delete, MANAGE_REPOSITORY to manage, INSTANCE_ADMIN to `ingot.admin.*`.
 */
object ScopeRules {

    /** The node list equivalent to [scopes] for a token of [owner]. Every node allows; the token narrows by listing them. */
    fun of(owner: String, scopes: Set<Action>): List<SourcedRule> =
        scopes.sortedBy { it.ordinal }.map { scope -> SourcedRule(owner, NodePattern.of(nodeOf(scope)), allowed = true) }

    /** The node rules a token with these scopes carries. Stored with the token, so the token keeps its rights as it was. */
    fun nodesOf(scopes: Set<Action>): List<NodeRule> =
        scopes.sortedBy { it.ordinal }.map { NodeRule(nodeOf(it), allowed = true) }

    /** The node text a scope becomes. */
    fun nodeOf(scope: Action): String =
        when (scope) {
            Action.READ -> "ingot.repository.*.read"
            Action.WRITE -> "ingot.repository.*.deploy"
            Action.DELETE -> "ingot.repository.*.delete"
            Action.MANAGE_REPOSITORY -> "ingot.repository.*.manage"
            Action.INSTANCE_ADMIN -> "ingot.admin.*"
        }
}
