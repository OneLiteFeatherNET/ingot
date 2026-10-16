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

import net.onelitefeather.ingot.identity.model.AccessLevel
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.DecisionSource
import net.onelitefeather.ingot.identity.model.Grant
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.SourcedRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The access check must agree with a real request: for every subject and request, the decision of the check ([PermissionEvaluator.decide]
 * for a node, [PermissionEvaluator.decidePath] for a path) allows exactly when [PermissionEvaluator.allows] allows the request.
 * Each subject is built here, so no test depends on another one.
 */
internal class AccessCheckParityTest {

    private data class Case(val repository: String, val path: String, val action: Action) {
        /** The repository action of the case, as a node names it. */
        val nodeAction: String
            get() = when (action) {
                Action.READ -> "read"
                Action.WRITE -> "deploy"
                Action.DELETE -> "delete"
                else -> error("not a case action: $action")
            }
    }

    private val cases = listOf("releases", "snapshots").flatMap { repository ->
        listOf(
            "net/onelitefeather/mycelium-bom/1.8.9/mycelium-bom-1.8.9.pom",
            "org/other/lib/1.0/lib-1.0.jar",
            "com/example/x.jar",
            "com/examplex/x.jar",
            "net/../org/x.jar",
            "",
        ).flatMap { path -> listOf(Action.READ, Action.WRITE, Action.DELETE).map { Case(repository, path, it) } }
    }

    private fun rule(subject: String, node: String, allowed: Boolean = true) = SourcedRule(subject, NodePattern.of(node), allowed)

    private fun team(vararg rules: SourcedRule, grants: List<Grant> = emptyList()) =
        AccessLevel(DecisionSource.TEAM, 0, rules.toList(), grants)

    private fun subject(
        vararg levels: AccessLevel,
        tokenRules: List<SourcedRule>? = null,
        disabled: Boolean = false,
    ) = AccessSubject(
        principalId = 1,
        principalName = "alice",
        kind = PrincipalKind.USER,
        disabled = disabled,
        levels = levels.toList(),
        tokenRules = tokenRules,
    )

    private val instanceAdmin = subject(team(rule("ops", "ingot.admin.*")))
    private val member = subject(
        team(
            rule("deployers", "ingot.repository.releases.deploy"),
            rule("deployers", "ingot.repository.releases.read"),
            rule("deployers", "ingot.artifact.net.onelitefeather.deploy", allowed = false),
        ),
    )
    private val narrowedMember = subject(
        team(rule("deployers", "ingot.repository.*.deploy"), rule("deployers", "ingot.repository.*.read")),
        tokenRules = listOf(rule("ci", "ingot.artifact.org.other.deploy"), rule("ci", "ingot.repository.*.read")),
    )
    private val adminNarrowedByToken = subject(
        team(rule("ops", "ingot.admin.*")),
        tokenRules = listOf(rule("ci", "ingot.repository.releases.read")),
    )
    private val adminTokenListingAdmin = subject(
        team(rule("ops", "ingot.admin.*")),
        tokenRules = listOf(rule("ci", "ingot.admin.*")),
    )
    private val legacy = subject(
        team(
            grants = listOf(
                Grant(team = "bot", role = Role.LEGACY_READ, repository = null, pathPrefix = "/releases/com/example"),
                Grant(team = "bot", role = Role.LEGACY_READ, repository = null, pathPrefix = "/rel"),
                Grant(team = "bot", role = Role.LEGACY_WRITE, repository = null, pathPrefix = "/snapshots"),
            ),
        ),
    )
    private val disabledAdmin = subject(team(rule("ops", "ingot.admin.*")), disabled = true)

    /** Every case of [subject] must agree in both forms: as a path, and for the root of the repository as a node. */
    private fun assertAgrees(subject: AccessSubject) {
        val mismatches = mutableListOf<String>()
        for (case in cases) {
            val real = PermissionEvaluator.allows(subject, AccessRequest("/${case.repository}/${case.path}", case.action))
            val path = PermissionEvaluator.decidePath(subject, case.repository, case.path, case.nodeAction)
            if (path.allowed != real) mismatches += "path ${case.repository}/${case.path} ${case.action}: check ${path.allowed}, request $real"
            if (case.path.isEmpty()) {
                val rootRequest = PermissionEvaluator.allows(subject, AccessRequest("/${case.repository}", case.action))
                val node = PermissionEvaluator.decide(subject, NodePattern.of("ingot.repository.${case.repository}.${case.nodeAction}"))
                if (node.allowed != rootRequest) {
                    mismatches += "node ingot.repository.${case.repository}.${case.nodeAction}: check ${node.allowed}, request $rootRequest"
                }
            }
        }
        assertThat(mismatches).describedAs("checks that disagree with the real requests").isEmpty()
    }

    @Test
    fun `the check agrees with real requests for an instance admin`() {
        assertAgrees(instanceAdmin)
    }

    @Test
    fun `the check agrees with real requests for a team member with an artifact denial`() {
        assertAgrees(member)
    }

    @Test
    fun `the check agrees with real requests for a member narrowed by a token`() {
        assertAgrees(narrowedMember)
    }

    @Test
    fun `the check agrees with real requests for an instance admin whose token does not list the admin node`() {
        assertAgrees(adminNarrowedByToken)
    }

    @Test
    fun `the check agrees with real requests for an instance admin whose token lists the admin node`() {
        assertAgrees(adminTokenListingAdmin)
    }

    @Test
    fun `the check agrees with real requests for legacy routes, including a route that is a text prefix of the repository`() {
        assertAgrees(legacy)
    }

    @Test
    fun `the check agrees with real requests for a disabled instance admin, which is denied everything`() {
        assertAgrees(disabledAdmin)
    }

    @Test
    fun `the instance admin shortcut is the deciding kind of a repository node`() {
        val decision = PermissionEvaluator.decide(instanceAdmin, NodePattern.of("ingot.repository.releases.deploy"))

        assertThat(decision.allowed).describedAs("allowed").isTrue
        assertThat(decision.decidingKind).describedAs("deciding kind").isEqualTo(INSTANCE_ADMIN_KIND)
        assertThat(decision.decidingNode).describedAs("deciding node").isEqualTo("ingot.admin.*")
    }

    @Test
    fun `the instance admin shortcut is the deciding kind of a path`() {
        val decision = PermissionEvaluator.decidePath(instanceAdmin, "releases", "org/other/lib/1.0/lib-1.0.jar", "deploy")

        assertThat(decision.allowed).describedAs("allowed").isTrue
        assertThat(decision.decidingKind).describedAs("deciding kind").isEqualTo(INSTANCE_ADMIN_KIND)
    }

    @Test
    fun `a denial does not restrict an instance admin, in the check and in a real request`() {
        val admin = subject(team(rule("ops", "*"), rule("ops", "ingot.repository.releases.delete", allowed = false)))

        assertThat(PermissionEvaluator.allows(admin, AccessRequest("/releases/x.jar", Action.DELETE))).describedAs("request").isTrue
        assertThat(PermissionEvaluator.decidePath(admin, "releases", "x.jar", "delete").allowed).describedAs("path check").isTrue
        assertThat(PermissionEvaluator.decide(admin, NodePattern.of("ingot.repository.releases.delete")).allowed).describedAs("node check").isTrue
    }
}
