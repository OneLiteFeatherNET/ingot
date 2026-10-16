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
 * The node decisions of the permission model: precedence of sources, specificity, negation, inheritance distance, token
 * narrowing, the admin node and the path mapping. Each test builds its own subject from in-memory levels.
 */
internal class PermissionDecisionTest {

    private fun rule(subject: String, node: String, allowed: Boolean = true) =
        SourcedRule(subject, NodePattern.of(node), allowed)

    private fun user(vararg rules: SourcedRule) =
        AccessLevel(DecisionSource.USER, 0, rules.toList(), emptyList())

    private fun team(vararg rules: SourcedRule) =
        AccessLevel(DecisionSource.TEAM, 0, rules.toList(), emptyList())

    private fun inherited(depth: Int, vararg rules: SourcedRule) =
        AccessLevel(DecisionSource.INHERITED_TEAM, depth, rules.toList(), emptyList())

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

    private fun decide(subject: AccessSubject, node: String) =
        PermissionEvaluator.decide(subject, NodePattern.of(node))

    @Test
    fun `a wildcard repository node allows the action on every repository`() {
        val alice = subject(team(rule("deployers", "ingot.repository.*.read")))

        assertThat(decide(alice, "ingot.repository.snapshots.read").allowed).describedAs("snapshots").isTrue
        assertThat(decide(alice, "ingot.repository.releases.read").allowed).describedAs("releases").isTrue
    }

    @Test
    fun `a wildcard action node covers every action of its repository`() {
        val alice = subject(team(rule("deployers", "ingot.repository.releases.*")))

        assertThat(decide(alice, "ingot.repository.releases.deploy").allowed).describedAs("deploy").isTrue
        assertThat(decide(alice, "ingot.repository.snapshots.deploy").allowed).describedAs("other repository").isFalse
    }

    @Test
    fun `the star node allows the admin area and the repository actions`() {
        val alice = subject(team(rule("ops", "*")))

        assertThat(decide(alice, "ingot.admin.users").allowed).describedAs("admin area").isTrue
        assertThat(decide(alice, "ingot.repository.releases.manage").allowed).describedAs("manage").isTrue
    }

    @Test
    fun `a more specific denial overrides a broader allowance in the same source`() {
        val alice = subject(team(rule("ops", "ingot.repository.*.*"), rule("ops", "ingot.repository.releases.delete", allowed = false)))

        assertThat(decide(alice, "ingot.repository.releases.delete").allowed).describedAs("denied exact").isFalse
        assertThat(decide(alice, "ingot.repository.releases.read").allowed).describedAs("other action").isTrue
    }

    @Test
    fun `within a team a specific allowance beats a wildcard denial`() {
        val alice = subject(team(rule("deployers", "ingot.repository.*.read", allowed = false), rule("deployers", "ingot.repository.releases.read")))

        assertThat(decide(alice, "ingot.repository.releases.read").allowed).describedAs("specific allow").isTrue
        assertThat(decide(alice, "ingot.repository.snapshots.read").allowed).describedAs("wildcard deny").isFalse
    }

    @Test
    fun `a denial wins a tie between equally specific nodes of two teams`() {
        val alice = subject(team(rule("deployers", "ingot.repository.*.deploy"), rule("readers", "ingot.repository.*.deploy", allowed = false)))

        assertThat(decide(alice, "ingot.repository.releases.deploy").allowed).describedAs("tie").isFalse
    }

    @Test
    fun `a direct node of the principal beats the nodes of its teams`() {
        val alice = subject(user(rule("alice", "ingot.repository.releases.read", allowed = false)), team(rule("deployers", "ingot.repository.releases.read")))

        val decision = decide(alice, "ingot.repository.releases.read")

        assertThat(decision.allowed).describedAs("user denial beats team allowance").isFalse
        assertThat(decision.source).describedAs("source").isEqualTo(DecisionSource.USER)
    }

    @Test
    fun `a team node beats an inherited node of a parent team`() {
        val alice = subject(team(rule("deployers", "ingot.repository.releases.read")), inherited(1, rule("root", "ingot.repository.releases.read", allowed = false)))

        val decision = decide(alice, "ingot.repository.releases.read")

        assertThat(decision.allowed).describedAs("the team is closer than its parent").isTrue
        assertThat(decision.source).describedAs("source").isEqualTo(DecisionSource.TEAM)
    }

    @Test
    fun `the nearest parent decides before a farther one`() {
        val alice = subject(inherited(1, rule("middle", "ingot.repository.releases.read")), inherited(2, rule("root", "ingot.repository.releases.read", allowed = false)))

        val decision = decide(alice, "ingot.repository.releases.read")

        assertThat(decision.allowed).describedAs("nearest parent allows").isTrue
        assertThat(decision.depth).describedAs("distance of the deciding parent").isEqualTo(1)
    }

    @Test
    fun `no matching node denies and names the default source`() {
        val alice = subject(team(rule("deployers", "ingot.repository.snapshots.read")))

        val decision = decide(alice, "ingot.repository.releases.read")

        assertThat(decision.allowed).describedAs("default deny").isFalse
        assertThat(decision.source).describedAs("source").isEqualTo(DecisionSource.DEFAULT)
    }

    @Test
    fun `a disabled principal is denied every node`() {
        val alice = subject(user(rule("alice", "*")), disabled = true)

        assertThat(decide(alice, "ingot.repository.releases.read").allowed).describedAs("disabled").isFalse
        assertThat(PermissionEvaluator.isInstanceAdmin(alice)).describedAs("disabled admin").isFalse
    }

    @Test
    fun `the admin node makes an instance admin`() {
        val alice = subject(team(rule("ops", "ingot.admin.*")))

        assertThat(PermissionEvaluator.isInstanceAdmin(alice)).isTrue
    }

    @Test
    fun `a single admin area is not the instance admin`() {
        val alice = subject(team(rule("ops", "ingot.admin.users")))

        assertThat(PermissionEvaluator.isInstanceAdmin(alice)).describedAs("only users").isFalse
        assertThat(decide(alice, "ingot.admin.users").allowed).describedAs("users area").isTrue
    }

    @Test
    fun `a direct admin node beats a team denial of the same area`() {
        val alice = subject(user(rule("alice", "ingot.admin.*")), team(rule("ops", "ingot.admin.users", allowed = false)))

        assertThat(decide(alice, "ingot.admin.users").allowed).describedAs("user is closer").isTrue
    }

    @Test
    fun `a token list can remove a right the owner has`() {
        val alice = subject(team(rule("ops", "*")), tokenRules = listOf(rule("token", "ingot.repository.*.read")))

        val deploy = decide(alice, "ingot.repository.releases.deploy")

        assertThat(deploy.allowed).describedAs("token does not list deploy").isFalse
        assertThat(deploy.source).describedAs("source of the denial").isEqualTo(DecisionSource.TOKEN)
        assertThat(decide(alice, "ingot.repository.releases.read").allowed).describedAs("token lists read").isTrue
    }

    @Test
    fun `a token list can never add a right the owner lacks`() {
        val alice = subject(team(rule("ops", "ingot.repository.releases.read")), tokenRules = listOf(rule("token", "ingot.repository.*.deploy")))

        assertThat(decide(alice, "ingot.repository.releases.deploy").allowed).describedAs("owner has no deploy").isFalse
    }

    @Test
    fun `a token without a list keeps the rights of its owner`() {
        val alice = subject(team(rule("ops", "ingot.repository.releases.read")), tokenRules = null)

        assertThat(decide(alice, "ingot.repository.releases.read").allowed).isTrue
    }

    @Test
    fun `a token list without a matching node denies`() {
        val alice = subject(team(rule("ops", "*")), tokenRules = listOf(rule("token", "ingot.admin.*")))

        assertThat(decide(alice, "ingot.repository.releases.read").allowed).describedAs("token admin list does not cover repositories").isFalse
    }

    @Test
    fun `the trace lists every candidate with whether it matched`() {
        val alice = subject(team(rule("deployers", "ingot.repository.*.read"), rule("deployers", "ingot.repository.snapshots.read")))

        val trace = decide(alice, "ingot.repository.releases.read").trace

        assertThat(trace).describedAs("every candidate").hasSize(2)
        assertThat(trace.map { it.matched }).describedAs("matched flags").containsExactly(true, false)
        assertThat(trace.first().subject).describedAs("subject of the candidate").isEqualTo("deployers")
    }

    @Test
    fun `a path request maps its action to the repository node`() {
        val alice = subject(team(rule("deployers", "ingot.repository.releases.deploy")))

        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/releases/com/example/x.jar", Action.WRITE))).describedAs("deploy").isTrue
        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/releases/com/example/x.jar", Action.DELETE))).describedAs("delete").isFalse
    }

    @Test
    fun `an instance admin passes every path check`() {
        val alice = subject(team(rule("ops", "ingot.admin.*")))

        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/any/path", Action.DELETE))).isTrue
    }

    @Test
    fun `a token that narrows away the admin node does not pass path checks as admin`() {
        // The owner is an admin, but its token lists only repository reads: the admin bypass is off, so the owner's
        // repository nodes decide, and this owner has none.
        val alice = subject(team(rule("ops", "ingot.admin.*")), tokenRules = listOf(rule("token", "ingot.repository.*.read")))

        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/releases/x", Action.READ))).describedAs("read without a repository node").isFalse
        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/releases/x", Action.WRITE))).describedAs("write").isFalse
    }

    @Test
    fun `a token that narrows away the admin node still reads through the owner's repository node`() {
        val alice = subject(team(rule("ops", "ingot.admin.*"), rule("ops", "ingot.repository.*.read")), tokenRules = listOf(rule("token", "ingot.repository.*.read")))

        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/releases/x", Action.READ))).describedAs("read through the repository node").isTrue
        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/releases/x", Action.WRITE))).describedAs("write").isFalse
    }

    @Test
    fun `a legacy route on a team applies next to node rules`() {
        val legacy = Grant(team = "machine:bot", role = Role.LEGACY_READ, repository = null, pathPrefix = "/releases/com/example")
        val bot = subject(AccessLevel(DecisionSource.TEAM, 0, emptyList(), listOf(legacy)))

        assertThat(PermissionEvaluator.allows(bot, AccessRequest("/releases/com/example/x.jar", Action.READ))).describedAs("legacy read").isTrue
        assertThat(PermissionEvaluator.allows(bot, AccessRequest("/releases/com/example/x.jar", Action.WRITE))).describedAs("no write").isFalse
    }

    @Test
    fun `a direct denial of the principal beats a path grant of its team`() {
        val grant = Grant(team = "deployers", role = Role.DEPLOY, repository = "releases", pathPrefix = null)
        val alice = subject(user(rule("alice", "ingot.repository.releases.deploy", allowed = false)), AccessLevel(DecisionSource.TEAM, 0, emptyList(), listOf(grant)))

        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/releases/x.jar", Action.WRITE))).describedAs("closest source wins").isFalse
    }

    @Test
    fun `the effective nodes list the allowed patterns and drop the denied ones`() {
        val alice = subject(team(rule("ops", "ingot.repository.*.read"), rule("ops", "ingot.repository.releases.delete", allowed = false)))

        assertThat(PermissionEvaluator.effectiveNodes(alice)).describedAs("effective").containsExactly("ingot.repository.*.read")
    }
}
