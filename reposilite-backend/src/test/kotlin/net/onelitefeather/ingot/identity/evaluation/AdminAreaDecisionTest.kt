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
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.SourcedRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * The admin area decision: an instance admin is not restricted by area denials, only by token narrowing, and the node
 * check answers what the real request answers. Pure rules: every test builds its own subject, nothing is shared.
 */
internal class AdminAreaDecisionTest {

    private fun subject(rules: List<SourcedRule> = emptyList(), tokenRules: List<SourcedRule>? = null, disabled: Boolean = false) =
        AccessSubject(
            principalId = 1,
            principalName = "bot",
            kind = PrincipalKind.MACHINE,
            disabled = disabled,
            levels = listOf(AccessLevel(source = DecisionSource.TEAM, depth = 0, rules = rules, grants = emptyList())),
            tokenRules = tokenRules,
        )

    private fun allow(node: String) = SourcedRule("machine:bot", NodePattern.of(node), allowed = true)

    private fun deny(node: String) = SourcedRule("machine:bot", NodePattern.of(node), allowed = false)

    @Test
    fun `an area denial does not restrict an instance admin`() {
        val admin = subject(rules = listOf(allow("ingot.admin.*"), deny("ingot.admin.users")))

        assertThat(PermissionEvaluator.allowsAdminArea(admin, "users")).describedAs("users").isTrue
        assertThat(PermissionEvaluator.decide(admin, NodePattern.admin("users")).decidingKind)
            .describedAs("the shortcut decides").isEqualTo(INSTANCE_ADMIN_KIND)
    }

    @Test
    fun `an instance admin is allowed every area its token allows`() {
        val admin = subject(rules = listOf(allow("ingot.admin.*")), tokenRules = listOf(allow("ingot.admin.*")))

        assertThat(NodePattern.ADMIN_AREAS.all { PermissionEvaluator.allowsAdminArea(admin, it) }).isTrue
    }

    @Test
    fun `a token that denies an area narrows an instance admin away from it`() {
        val narrowed = subject(
            rules = listOf(allow("ingot.admin.*")),
            tokenRules = listOf(allow("ingot.admin.*"), deny("ingot.admin.audit")),
        )

        assertThat(PermissionEvaluator.allowsAdminArea(narrowed, "audit")).describedAs("audit").isFalse
        assertThat(PermissionEvaluator.allowsAdminArea(narrowed, "users")).describedAs("users").isTrue
    }

    @Test
    fun `a token without an area narrows an instance admin away from it`() {
        val narrowed = subject(rules = listOf(allow("ingot.admin.*")), tokenRules = listOf(allow("ingot.admin.users")))

        assertThat(PermissionEvaluator.allowsAdminArea(narrowed, "users")).describedAs("users").isTrue
        assertThat(PermissionEvaluator.allowsAdminArea(narrowed, "audit")).describedAs("audit").isFalse
    }

    @Test
    fun `a principal with an area node is allowed that area only`() {
        val auditor = subject(rules = listOf(allow("ingot.admin.audit")))

        assertThat(PermissionEvaluator.allowsAdminArea(auditor, "audit")).describedAs("audit").isTrue
        assertThat(PermissionEvaluator.allowsAdminArea(auditor, "users")).describedAs("users").isFalse
        assertThat(PermissionEvaluator.isInstanceAdmin(auditor)).describedAs("instance admin").isFalse
    }

    @Test
    fun `a legacy manager keeps every area`() {
        val legacyManager = subject(rules = listOf(allow("ingot.admin.*")), tokenRules = null)

        assertThat(NodePattern.ADMIN_AREAS.all { PermissionEvaluator.allowsAdminArea(legacyManager, it) }).isTrue
    }

    @Test
    fun `a disabled principal is denied every area, even with the area node`() {
        val disabled = subject(rules = listOf(allow("ingot.admin.*")), disabled = true)

        assertThat(NodePattern.ADMIN_AREAS.none { PermissionEvaluator.allowsAdminArea(disabled, it) }).isTrue
    }

    @ParameterizedTest
    @ValueSource(strings = ["settings", "users", "teams", "tokens", "maintenance", "audit", "console", "dashboard", "*"])
    fun `the area check and the node check agree for an area`(area: String) {
        val shapes = listOf(
            subject(),
            subject(rules = listOf(allow("ingot.admin.$area"))),
            subject(rules = listOf(allow("ingot.admin.*"), deny("ingot.admin.$area"))),
            subject(rules = listOf(allow("ingot.admin.*")), tokenRules = listOf(allow("ingot.admin.*"), deny("ingot.admin.$area"))),
            subject(rules = listOf(allow("ingot.admin.$area")), tokenRules = listOf(allow("ingot.admin.*"))),
            subject(rules = listOf(allow("*"), deny("ingot.admin.$area")), tokenRules = listOf(deny("ingot.admin.$area"))),
        )

        shapes.forEachIndexed { index, shape ->
            assertThat(PermissionEvaluator.allowsAdminArea(shape, area))
                .describedAs("shape $index of area $area: the area check must equal the node decision")
                .isEqualTo(PermissionEvaluator.decide(shape, NodePattern.admin(area)).allowed)
        }
    }

    @Test
    fun `a qualified artifact node is decided as the request for its subtree`() {
        val reader = subject(rules = listOf(allow("ingot.repository.releases.artifact.net.example.read")))
        val node = NodePattern.of("ingot.repository.releases.artifact.net.example.read")

        val byNode = PermissionEvaluator.decide(reader, node).allowed
        val byRequest = PermissionEvaluator.allows(reader, AccessRequest("/releases/net/example", Action.READ))

        assertThat(byNode).describedAs("node decision").isTrue
        assertThat(byNode).describedAs("the request for the subtree").isEqualTo(byRequest)
    }

    @Test
    fun `an instance admin is allowed a qualified artifact node with the shortcut named`() {
        val admin = subject(rules = listOf(allow("ingot.admin.*"), deny("ingot.repository.releases.read")))
        val node = NodePattern.of("ingot.repository.releases.artifact.net.example.read")

        val decision = PermissionEvaluator.decide(admin, node)

        assertThat(decision.allowed).describedAs("allowed").isTrue
        assertThat(decision.decidingKind).describedAs("deciding kind").isEqualTo(INSTANCE_ADMIN_KIND)
        assertThat(PermissionEvaluator.allows(admin, AccessRequest("/releases/net/example", Action.READ)))
            .describedAs("the real request agrees").isTrue
    }

    @Test
    fun `an instance admin is allowed an unqualified artifact node with the shortcut named`() {
        val admin = subject(rules = listOf(allow("ingot.admin.*")))
        val decision = PermissionEvaluator.decide(admin, NodePattern.of("ingot.artifact.net.example.read"))

        assertThat(decision.allowed).describedAs("allowed").isTrue
        assertThat(decision.decidingKind).describedAs("deciding kind").isEqualTo(INSTANCE_ADMIN_KIND)
    }

    @Test
    fun `an artifact node check and the request for its subtree agree under a repository denial`() {
        val reader = subject(
            rules = listOf(
                allow("ingot.repository.releases.artifact.net.example.read"),
                deny("ingot.repository.releases.read"),
            ),
        )
        val node = NodePattern.of("ingot.repository.releases.artifact.net.example.read")

        assertThat(PermissionEvaluator.decide(reader, node).allowed)
            .describedAs("node decision equals the real request")
            .isEqualTo(PermissionEvaluator.allows(reader, AccessRequest("/releases/net/example", Action.READ)))
    }
}
