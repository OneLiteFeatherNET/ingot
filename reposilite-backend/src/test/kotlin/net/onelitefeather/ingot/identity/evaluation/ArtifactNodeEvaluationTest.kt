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
 * Artifact scoped nodes in the decisions of the permission model: path boundaries, precedence against repository nodes,
 * the specificity ordering inside a level, team inheritance and the deciding node of a check. Each test builds its own
 * subject from in-memory levels.
 */
internal class ArtifactNodeEvaluationTest {

    private fun rule(node: String, allowed: Boolean = true, subject: String = "team") =
        SourcedRule(subject, NodePattern.of(node), allowed)

    private fun user(vararg rules: SourcedRule) =
        AccessLevel(DecisionSource.USER, 0, rules.toList(), emptyList())

    private fun team(vararg rules: SourcedRule) =
        AccessLevel(DecisionSource.TEAM, 0, rules.toList(), emptyList())

    private fun teamWithGrants(rules: List<SourcedRule>, grants: List<Grant>) =
        AccessLevel(DecisionSource.TEAM, 0, rules, grants)

    private fun inherited(depth: Int, vararg rules: SourcedRule) =
        AccessLevel(DecisionSource.INHERITED_TEAM, depth, rules.toList(), emptyList())

    private fun subject(vararg levels: AccessLevel, tokenRules: List<SourcedRule>? = null) =
        AccessSubject(
            principalId = 1,
            principalName = "alice",
            kind = PrincipalKind.USER,
            disabled = false,
            levels = levels.toList(),
            tokenRules = tokenRules,
        )

    private fun deploys(subject: AccessSubject, path: String) =
        PermissionEvaluator.allows(subject, AccessRequest(path, Action.WRITE))

    // allow and no match

    @Test
    fun `a group node allows deploying inside its subtree`() {
        val alice = subject(team(rule("ingot.artifact.net.onelitefeather.deploy")))

        assertThat(deploys(alice, "/releases/net/onelitefeather/mycelium-bom/1.0/a.jar")).describedAs("inside").isTrue
        assertThat(deploys(alice, "/snapshots/net/onelitefeather/sub/group/b.jar")).describedAs("subgroup in another repository").isTrue
    }

    @Test
    fun `a group node denies deploying outside its subtree because no node matches`() {
        val alice = subject(team(rule("ingot.artifact.net.onelitefeather.deploy")))

        assertThat(deploys(alice, "/releases/org/other/lib/1.0/a.jar")).describedAs("other group").isFalse
    }

    @Test
    fun `a group node does not cover a longer sibling name`() {
        val alice = subject(team(rule("ingot.artifact.net.onelitefeather.deploy")))

        assertThat(deploys(alice, "/releases/net/onelitefeatherX/lib/1.0/a.jar")).describedAs("segment boundary").isFalse
        assertThat(deploys(alice, "/releases/net/onelitefeather/lib/1.0/a.jar")).describedAs("real subtree").isTrue
    }

    @Test
    fun `an artifact id node covers only its artifact`() {
        val alice = subject(team(rule("ingot.artifact.net.onelitefeather:mycelium-bom.deploy")))

        assertThat(deploys(alice, "/releases/net/onelitefeather/mycelium-bom/1.0/a.jar")).describedAs("the artifact").isTrue
        assertThat(deploys(alice, "/releases/net/onelitefeather/mycelium-bom-extra/1.0/a.jar")).describedAs("longer artifact name").isFalse
        assertThat(deploys(alice, "/releases/net/onelitefeather/mycelium/1.0/a.jar")).describedAs("sibling artifact").isFalse
    }

    @Test
    fun `an artifact node grants only its own action`() {
        val alice = subject(team(rule("ingot.artifact.net.onelitefeather.read")))

        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/releases/net/onelitefeather/a.jar", Action.READ))).describedAs("read").isTrue
        assertThat(deploys(alice, "/releases/net/onelitefeather/a.jar")).describedAs("deploy").isFalse
        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/releases/net/onelitefeather/a.jar", Action.DELETE))).describedAs("delete").isFalse
    }

    @Test
    fun `an action wildcard covers read, deploy and delete but never manage`() {
        val alice = subject(team(rule("ingot.artifact.*.*")))

        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/releases/a/b.jar", Action.READ))).describedAs("read").isTrue
        assertThat(deploys(alice, "/releases/a/b.jar")).describedAs("deploy").isTrue
        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/releases/a/b.jar", Action.DELETE))).describedAs("delete").isTrue
        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/releases/a/b.jar", Action.MANAGE_REPOSITORY))).describedAs("manage").isFalse
    }

    @Test
    fun `a qualified node applies in its repository only`() {
        val alice = subject(team(rule("ingot.repository.releases.artifact.net.onelitefeather.deploy")))

        assertThat(deploys(alice, "/releases/net/onelitefeather/a.jar")).describedAs("releases").isTrue
        assertThat(deploys(alice, "/snapshots/net/onelitefeather/a.jar")).describedAs("snapshots").isFalse
    }

    @Test
    fun `a path that cannot be normalised is covered by no artifact node`() {
        val alice = subject(team(rule("ingot.artifact.net.onelitefeather.deploy")))

        assertThat(deploys(alice, "/releases/net/onelitefeather/../../org/other/a.jar")).describedAs("parent segments").isFalse
    }

    @Test
    fun `repeated slashes in the path are normalised before matching`() {
        val alice = subject(team(rule("ingot.artifact.net.onelitefeather.deploy")))

        assertThat(deploys(alice, "/releases//net//onelitefeather/a.jar")).isTrue
    }

    // precedence

    @Test
    fun `an artifact denial beats a repository allowance at the same position and leaves the rest of the repository open`() {
        val alice = subject(team(rule("ingot.repository.releases.deploy"), rule("ingot.artifact.net.onelitefeather.deploy", allowed = false)))

        assertThat(deploys(alice, "/releases/net/onelitefeather/a.jar")).describedAs("inside the denied subtree").isFalse
        assertThat(deploys(alice, "/releases/org/other/a.jar")).describedAs("elsewhere").isTrue
    }

    @Test
    fun `an artifact allowance beats a repository denial inside its subtree only`() {
        val alice = subject(team(rule("ingot.repository.releases.deploy", allowed = false), rule("ingot.artifact.net.onelitefeather.deploy")))

        assertThat(deploys(alice, "/releases/net/onelitefeather/a.jar")).describedAs("inside").isTrue
        assertThat(deploys(alice, "/releases/org/other/a.jar")).describedAs("elsewhere").isFalse
    }

    @Test
    fun `a qualified node beats an unqualified one`() {
        val alice = subject(team(rule("ingot.artifact.net.deploy"), rule("ingot.repository.releases.artifact.net.deploy", allowed = false)))

        assertThat(deploys(alice, "/releases/net/a.jar")).describedAs("qualified denial wins in its repository").isFalse
        assertThat(deploys(alice, "/snapshots/net/a.jar")).describedAs("unqualified allowance elsewhere").isTrue
    }

    @Test
    fun `an artifact id node beats a group node`() {
        val alice = subject(team(rule("ingot.artifact.net.deploy", allowed = false), rule("ingot.artifact.net:lib.deploy")))

        assertThat(deploys(alice, "/releases/net/lib/1.0/a.jar")).describedAs("the artifact").isTrue
        assertThat(deploys(alice, "/releases/net/other/1.0/a.jar")).describedAs("another artifact").isFalse
    }

    @Test
    fun `a longer group beats a shorter group`() {
        val alice = subject(team(rule("ingot.artifact.net.deploy", allowed = false), rule("ingot.artifact.net.onelitefeather.deploy")))

        assertThat(deploys(alice, "/releases/net/onelitefeather/a.jar")).describedAs("longer group allows").isTrue
        assertThat(deploys(alice, "/releases/net/other/a.jar")).describedAs("shorter group denies").isFalse
    }

    @Test
    fun `a literal action beats the action wildcard`() {
        val alice = subject(team(rule("ingot.artifact.net.*", allowed = false), rule("ingot.artifact.net.read")))

        assertThat(PermissionEvaluator.allows(alice, AccessRequest("/releases/net/a.jar", Action.READ))).describedAs("literal read").isTrue
        assertThat(deploys(alice, "/releases/net/a.jar")).describedAs("wildcard denial").isFalse
    }

    @Test
    fun `a denial wins a tie between equally specific artifact nodes`() {
        val alice = subject(team(rule("ingot.artifact.net.deploy", subject = "deployers"), rule("ingot.artifact.net.deploy", allowed = false, subject = "readers")))

        assertThat(deploys(alice, "/releases/net/a.jar")).isFalse
    }

    @Test
    fun `a direct artifact denial of the principal beats a repository allowance of its team`() {
        val alice = subject(user(rule("ingot.artifact.net.deploy", allowed = false, subject = "alice")), team(rule("ingot.repository.releases.deploy")))

        assertThat(deploys(alice, "/releases/net/a.jar")).describedAs("closest source wins").isFalse
    }

    @Test
    fun `an artifact node that does not match leaves the decision to the next source`() {
        val alice = subject(user(rule("ingot.artifact.net.onelitefeather.deploy", subject = "alice")), team(rule("ingot.repository.releases.deploy")))

        assertThat(deploys(alice, "/releases/org/other/a.jar")).describedAs("the team decides outside the subtree").isTrue
    }

    @Test
    fun `an artifact denial beats a role grant of the same source`() {
        val grant = Grant(team = "deployers", role = Role.DEPLOY, repository = "releases", pathPrefix = null)
        val alice = subject(teamWithGrants(listOf(rule("ingot.artifact.net.onelitefeather.deploy", allowed = false)), listOf(grant)))

        assertThat(deploys(alice, "/releases/net/onelitefeather/a.jar")).describedAs("denied subtree").isFalse
        assertThat(deploys(alice, "/releases/org/other/a.jar")).describedAs("the grant still covers the rest").isTrue
    }

    @Test
    fun `a legacy route stays in force when no artifact node matches`() {
        val legacy = Grant(team = "machine:bot", role = Role.LEGACY_WRITE, repository = null, pathPrefix = "/releases/com/example")
        val bot = subject(teamWithGrants(listOf(rule("ingot.artifact.net.onelitefeather.deploy")), listOf(legacy)))

        assertThat(deploys(bot, "/releases/com/example/x.jar")).describedAs("legacy route").isTrue
        assertThat(deploys(bot, "/releases/net/onelitefeather/x.jar")).describedAs("artifact node").isTrue
        assertThat(deploys(bot, "/releases/org/other/x.jar")).describedAs("neither").isFalse
    }

    // inheritance

    @Test
    fun `an artifact node of a parent team applies through inheritance`() {
        val alice = subject(team(), inherited(1, rule("ingot.artifact.net.onelitefeather.deploy", subject = "parent")))

        assertThat(deploys(alice, "/releases/net/onelitefeather/a.jar")).describedAs("inherited allowance").isTrue
        assertThat(deploys(alice, "/releases/org/other/a.jar")).describedAs("outside").isFalse
    }

    @Test
    fun `a team artifact node beats an artifact node of its parent`() {
        val alice = subject(
            team(rule("ingot.artifact.net.onelitefeather.deploy")),
            inherited(1, rule("ingot.artifact.net.onelitefeather.deploy", allowed = false, subject = "parent")),
        )

        assertThat(deploys(alice, "/releases/net/onelitefeather/a.jar")).describedAs("the team is closer").isTrue
    }

    // administrators and tokens

    @Test
    fun `an instance admin passes every path check without artifact nodes`() {
        val alice = subject(team(rule("ingot.admin.*"), rule("ingot.artifact.net.deploy", allowed = false)))

        assertThat(deploys(alice, "/releases/net/a.jar")).isTrue
    }

    // check

    @Test
    fun `the check names the artifact node that decided`() {
        val alice = subject(team(rule("ingot.repository.releases.deploy"), rule("ingot.artifact.net.onelitefeather.deploy", allowed = false)))

        val decision = PermissionEvaluator.decide(alice, NodePattern.of("ingot.repository.releases.artifact.net.onelitefeather:mycelium-bom.deploy"))

        assertThat(decision.allowed).describedAs("allowed").isFalse
        assertThat(decision.decidingNode).describedAs("deciding node").isEqualTo("ingot.artifact.net.onelitefeather.deploy")
        assertThat(decision.decidingKind).describedAs("deciding kind").isEqualTo("ARTIFACT")
    }

    @Test
    fun `the check names a repository node that decided`() {
        val alice = subject(team(rule("ingot.repository.releases.deploy")))

        val decision = PermissionEvaluator.decide(alice, NodePattern.of("ingot.repository.releases.artifact.net:x.deploy"))

        assertThat(decision.allowed).describedAs("allowed").isTrue
        assertThat(decision.decidingNode).describedAs("deciding node").isEqualTo("ingot.repository.releases.deploy")
        assertThat(decision.decidingKind).describedAs("deciding kind").isEqualTo("REPOSITORY")
    }

    @Test
    fun `the check names no node when nothing matches`() {
        val alice = subject(team(rule("ingot.artifact.net.deploy")))

        val decision = PermissionEvaluator.decide(alice, NodePattern.of("ingot.artifact.org:x.deploy"))

        assertThat(decision.allowed).describedAs("allowed").isFalse
        assertThat(decision.decidingNode).describedAs("deciding node").isNull()
        assertThat(decision.decidingKind).describedAs("deciding kind").isNull()
    }

    @Test
    fun `the check names a role grant as the deciding kind and no node`() {
        val grant = Grant(team = "deployers", role = Role.DEPLOY, repository = "releases", pathPrefix = null)
        val alice = subject(teamWithGrants(emptyList(), listOf(grant)))

        val decision = PermissionEvaluator.decide(alice, NodePattern.of("ingot.repository.releases.artifact.net:x.deploy"))

        assertThat(decision.allowed).describedAs("allowed").isTrue
        assertThat(decision.decidingNode).describedAs("deciding node").isNull()
        assertThat(decision.decidingKind).describedAs("deciding kind").isEqualTo("GRANT")
    }

    @Test
    fun `the trace of an artifact request explains why a node matched or not`() {
        val alice = subject(team(rule("ingot.artifact.net.onelitefeather.deploy"), rule("ingot.artifact.org.deploy")))

        val trace = PermissionEvaluator.decide(alice, NodePattern.of("ingot.artifact.net.onelitefeather:mycelium-bom.deploy")).trace

        assertThat(trace.map { it.matched }).describedAs("matched flags").containsExactly(true, false)
        assertThat(trace[0].reason).describedAs("matched reason").contains("artifact(").contains("groupSegments=2")
        assertThat(trace[1].reason).describedAs("mismatch reason").contains("outside")
        assertThat(trace.map { it.kind }).describedAs("kinds").containsExactly("ARTIFACT", "ARTIFACT")
    }

    @Test
    fun `an unqualified artifact request is covered by an unqualified node but not by a node of one repository`() {
        val alice = subject(team(rule("ingot.repository.releases.artifact.net.deploy")))
        val bob = subject(team(rule("ingot.artifact.net.deploy")))

        val request = NodePattern.of("ingot.artifact.net:x.deploy")

        assertThat(PermissionEvaluator.decide(alice, request).allowed).describedAs("qualified node").isFalse
        assertThat(PermissionEvaluator.decide(bob, request).allowed).describedAs("unqualified node").isTrue
    }

    @Test
    fun `the effective nodes list an allowed artifact node and drop a denied one`() {
        val alice = subject(team(rule("ingot.artifact.net.read"), rule("ingot.artifact.org.deploy", allowed = false)))

        assertThat(PermissionEvaluator.effectiveNodes(alice)).containsExactly("ingot.artifact.net.read")
    }
}
