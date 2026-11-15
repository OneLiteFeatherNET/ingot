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
package net.onelitefeather.ingot.identity.corpus

import net.onelitefeather.ingot.identity.model.AccessLevel
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.DecisionSource
import net.onelitefeather.ingot.identity.model.Grant
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.SourcedRule

/*
 * Subject fixtures of the corpus. Every row builds its subject with these functions, so no row shares state with another.
 * The principal is [PRINCIPAL]: its own rules sit in the USER level at depth 0, team rules in a TEAM or INHERITED_TEAM level.
 */

internal const val PRINCIPAL: String = "alice"
internal const val TEAM: String = "readers"
internal const val LEGACY_TEAM: String = "machine:bot"

internal fun allow(node: String, by: String = PRINCIPAL): SourcedRule = SourcedRule(by, NodePattern.of(node), allowed = true)

internal fun deny(node: String, by: String = PRINCIPAL): SourcedRule = SourcedRule(by, NodePattern.of(node), allowed = false)

internal fun grant(role: Role, repository: String? = null, prefix: String? = null, team: String = TEAM): Grant =
    Grant(team, role, repository, prefix)

/** A legacy route grant of a private machine team. */
internal fun legacy(route: String, role: Role): Grant = Grant(LEGACY_TEAM, role, null, route)

internal fun own(rules: List<SourcedRule> = emptyList(), grants: List<Grant> = emptyList()): AccessLevel =
    AccessLevel(DecisionSource.USER, 0, rules, grants)

internal fun team(rules: List<SourcedRule> = emptyList(), grants: List<Grant> = emptyList()): AccessLevel =
    AccessLevel(DecisionSource.TEAM, 0, rules, grants)

internal fun inherited(depth: Int, rules: List<SourcedRule> = emptyList(), grants: List<Grant> = emptyList()): AccessLevel =
    AccessLevel(DecisionSource.INHERITED_TEAM, depth, rules, grants)

internal fun subjectOf(
    vararg levels: AccessLevel,
    token: List<SourcedRule>? = null,
    disabled: Boolean = false,
): AccessSubject =
    AccessSubject(
        principalId = 1,
        principalName = PRINCIPAL,
        kind = PrincipalKind.USER,
        disabled = disabled,
        levels = levels.toList(),
        tokenRules = token,
    )

internal fun tokenOf(vararg rules: SourcedRule): List<SourcedRule> = rules.toList()
