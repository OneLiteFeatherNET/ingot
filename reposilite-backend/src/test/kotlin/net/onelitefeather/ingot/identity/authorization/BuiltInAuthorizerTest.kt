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
import net.onelitefeather.ingot.identity.evaluation.INSTANCE_ADMIN_KIND
import net.onelitefeather.ingot.identity.evaluation.PermissionEvaluator
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
 * The built-in authorizer maps the evaluator onto Allow, Deny and NoOpinion without changing a decision. Every test builds
 * its own subject from rules and tokens, with no store and no clock.
 */
internal class BuiltInAuthorizerTest {

    private fun subject(
        rules: List<SourcedRule> = emptyList(),
        disabled: Boolean = false,
        tokenRules: List<SourcedRule>? = null,
    ) = AccessSubject(
        principalId = 1,
        principalName = "bot",
        kind = PrincipalKind.MACHINE,
        disabled = disabled,
        levels = listOf(AccessLevel(source = DecisionSource.TEAM, depth = 0, rules = rules, grants = emptyList())),
        tokenRules = tokenRules,
    )

    private fun rule(node: String, allowed: Boolean) = SourcedRule("machine:bot", NodePattern.of(node), allowed)

    private val read = AuthorizationRequest.Access(subject(), AccessRequest("/releases/net/x.jar", Action.READ))

    @Test
    fun `a request that no rule covers is no opinion`() {
        val answer = BuiltInAuthorizer.decide(read)

        assertThat(answer).describedAs("answer").isInstanceOf(AuthorizerDecision.NoOpinion::class.java)
    }

    @Test
    fun `an explicit denial of a rule is a deny`() {
        val denied = AuthorizationRequest.Access(
            subject(rules = listOf(rule("ingot.repository.releases.read", allowed = false))),
            AccessRequest("/releases/net/x.jar", Action.READ),
        )

        val answer = BuiltInAuthorizer.decide(denied)

        assertThat(answer).describedAs("answer").isInstanceOf(AuthorizerDecision.Deny::class.java)
        assertThat(answer.decision?.allowed).describedAs("allowed").isFalse
    }

    @Test
    fun `the instance admin shortcut allows a path that a repository denial covers when the token does not deny it`() {
        val admin = subject(
            rules = listOf(rule("ingot.admin.*", allowed = true), rule("ingot.repository.releases.read", allowed = false)),
            tokenRules = listOf(SourcedRule("bot", NodePattern.EVERYTHING, allowed = true)),
        )

        val answer = BuiltInAuthorizer.decide(AuthorizationRequest.Access(admin, AccessRequest("/releases/net/x.jar", Action.READ)))

        assertThat(answer).describedAs("answer").isInstanceOf(AuthorizerDecision.Allow::class.java)
        assertThat(answer.decision?.decidingKind).describedAs("deciding kind").isEqualTo(INSTANCE_ADMIN_KIND)
    }

    @Test
    fun `an admin area request is allowed for an instance admin and has no opinion for anyone else`() {
        val admin = subject(rules = listOf(rule("ingot.admin.*", allowed = true)))

        assertThat(BuiltInAuthorizer.decide(AuthorizationRequest.AdminArea(admin, null)))
            .describedAs("instance admin").isInstanceOf(AuthorizerDecision.Allow::class.java)
        assertThat(BuiltInAuthorizer.decide(AuthorizationRequest.AdminArea(subject(), null)))
            .describedAs("plain subject").isInstanceOf(AuthorizerDecision.NoOpinion::class.java)
    }

    @ParameterizedTest(name = "a disabled refusal of {0} names the node the evaluator names")
    @ValueSource(strings = [
        "ingot.repository.releases.read",
        "ingot.repository.releases.artifact.org.other:lib.deploy",
        "ingot.artifact.net.onelitefeather:mycelium-bom.deploy",
        "ingot.admin.users",
        "ingot.admin.*",
    ])
    fun `a request refused for a disabled principal carries the decision the evaluator gives for the same node`(text: String) {
        val disabled = subject(disabled = true)
        val node = NodePattern.of(text)

        val refusal = AuthorizationRequest.Node(disabled, node).refusal()

        assertThat(refusal).describedAs("refusal").isEqualTo(PermissionEvaluator.decide(disabled, node))
    }
}
