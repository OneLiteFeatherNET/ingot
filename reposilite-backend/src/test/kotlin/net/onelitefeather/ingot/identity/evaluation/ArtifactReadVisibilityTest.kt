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
import net.onelitefeather.ingot.identity.model.DecisionSource
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.SourcedRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * What artifact read nodes add to the visibility of a repository: the repository is seen when some subtree of it is
 * readable, and the readable subtrees are the roots a listing may lead into. Each test builds its own subject.
 */
internal class ArtifactReadVisibilityTest {

    private fun rule(node: String, allowed: Boolean = true) =
        SourcedRule("team", NodePattern.of(node), allowed)

    private fun subject(vararg rules: SourcedRule, tokenRules: List<SourcedRule>? = null) =
        AccessSubject(
            principalId = 1,
            principalName = "alice",
            kind = PrincipalKind.USER,
            disabled = false,
            levels = listOf(AccessLevel(DecisionSource.TEAM, 0, rules.toList(), emptyList())),
            tokenRules = tokenRules,
        )

    @Test
    fun `a repository is seen when an artifact read node covers a subtree of it`() {
        val alice = subject(rule("ingot.artifact.net.onelitefeather.read"))

        assertThat(PermissionEvaluator.canSee(alice, "/private")).isTrue
    }

    @Test
    fun `a repository is not seen through an artifact deploy node`() {
        val alice = subject(rule("ingot.artifact.net.onelitefeather.deploy"))

        assertThat(PermissionEvaluator.canSee(alice, "/private")).isFalse
    }

    @Test
    fun `a repository is not seen through an artifact node qualified for another repository`() {
        val alice = subject(rule("ingot.repository.other.artifact.net.onelitefeather.read"))

        assertThat(PermissionEvaluator.canSee(alice, "/private")).isFalse
    }

    @Test
    fun `a repository is seen through an artifact node qualified for it`() {
        val alice = subject(rule("ingot.repository.private.artifact.net.onelitefeather.read"))

        assertThat(PermissionEvaluator.canSee(alice, "/private")).isTrue
    }

    @Test
    fun `a repository is not seen when a denial cancels the only artifact read node`() {
        val alice = subject(
            rule("ingot.artifact.net.onelitefeather.read"),
            rule("ingot.artifact.net.onelitefeather.read", allowed = false),
        )

        assertThat(PermissionEvaluator.canSee(alice, "/private")).isFalse
    }

    @Test
    fun `a token narrowed to an artifact still sees the repository its owner reads`() {
        val alice = subject(
            rule("ingot.repository.private.read"),
            tokenRules = listOf(rule("ingot.artifact.net.onelitefeather.read")),
        )

        assertThat(PermissionEvaluator.canSee(alice, "/private")).isTrue
    }

    @Test
    fun `a token narrowed to an artifact its owner cannot read does not see the repository`() {
        val alice = subject(
            rule("ingot.artifact.org.other.read"),
            tokenRules = listOf(rule("ingot.artifact.net.onelitefeather.read")),
        )

        assertThat(PermissionEvaluator.canSee(alice, "/private")).isFalse
    }

    @Test
    fun `the readable subtrees are the roots of the allowed artifact read nodes`() {
        val alice = subject(
            rule("ingot.artifact.net.onelitefeather.read"),
            rule("ingot.artifact.org.other:lib.read"),
            rule("ingot.artifact.com.example.deploy"),
        )

        assertThat(PermissionEvaluator.readableArtifactSubtrees(alice, "private"))
            .containsExactlyInAnyOrder(listOf("net", "onelitefeather"), listOf("org", "other", "lib"))
    }

    @Test
    fun `a subtree that a denial removes is not readable`() {
        val alice = subject(
            rule("ingot.artifact.net.onelitefeather.read"),
            rule("ingot.artifact.net.onelitefeather.read", allowed = false),
            rule("ingot.artifact.org.other.read"),
        )

        assertThat(PermissionEvaluator.readableArtifactSubtrees(alice, "private")).containsExactly(listOf("org", "other"))
    }

    @Test
    fun `a subtree outside the token is not readable`() {
        val alice = subject(
            rule("ingot.artifact.net.onelitefeather.read"),
            tokenRules = listOf(rule("ingot.artifact.net.onelitefeather:mycelium-bom.read")),
        )

        assertThat(PermissionEvaluator.readableArtifactSubtrees(alice, "private"))
            .containsExactly(listOf("net", "onelitefeather", "mycelium-bom"))
    }

    @Test
    fun `an artifact read rule exists for a repository even when only a denial names it`() {
        val alice = subject(rule("ingot.repository.private.read"), rule("ingot.artifact.net.onelitefeather:secret.read", allowed = false))

        assertThat(PermissionEvaluator.hasArtifactReadRule(alice, "private")).isTrue
        assertThat(PermissionEvaluator.hasArtifactReadRule(alice, "releases")).describedAs("rule is unqualified, applies everywhere").isTrue
    }

    @Test
    fun `no artifact read rule exists for a subject with repository nodes only`() {
        val alice = subject(rule("ingot.repository.private.read"))

        assertThat(PermissionEvaluator.hasArtifactReadRule(alice, "private")).isFalse
    }

    @Test
    fun `no artifact read rule exists for a repository the qualified node does not name`() {
        val alice = subject(rule("ingot.repository.private.artifact.net.onelitefeather.read"))

        assertThat(PermissionEvaluator.hasArtifactReadRule(alice, "private")).isTrue
        assertThat(PermissionEvaluator.hasArtifactReadRule(alice, "releases")).isFalse
    }
}
