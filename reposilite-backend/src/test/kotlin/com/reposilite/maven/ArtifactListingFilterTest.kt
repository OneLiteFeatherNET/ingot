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
package com.reposilite.maven

import net.onelitefeather.ingot.identity.model.AccessLevel
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.DecisionSource
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.SourcedRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Which entries of a directory a subject with artifact read nodes may see: the allowed subtrees, the directories on the
 * way to them, and nothing else. Each test builds its own subject from in-memory rules.
 */
internal class ArtifactListingFilterTest {

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

    private fun filterOf(vararg rules: SourcedRule) =
        ArtifactListingFilter.of(subject(*rules), "private") ?: error("the filter must be active for these rules")

    // activation

    @Test
    fun `the filter is inactive for a subject without artifact read rules`() {
        val alice = subject(rule("ingot.repository.private.read"), rule("ingot.artifact.net.onelitefeather.deploy"))

        assertThat(ArtifactListingFilter.of(alice, "private")).describedAs("legacy path stays in charge").isNull()
    }

    @Test
    fun `the filter is inactive for a repository the qualified node does not name`() {
        val alice = subject(rule("ingot.repository.releases.artifact.net.onelitefeather.read"))

        assertThat(ArtifactListingFilter.of(alice, "private")).isNull()
    }

    @Test
    fun `the filter is active for a denial of an artifact read node`() {
        val alice = subject(rule("ingot.repository.private.read"), rule("ingot.artifact.net.onelitefeather:secret.read", allowed = false))

        assertThat(ArtifactListingFilter.of(alice, "private")).isNotNull
    }

    // the table of cases

    @Test
    fun `the root shows the first segment of an allowed subtree`() {
        val filter = filterOf(rule("ingot.artifact.net.onelitefeather.read"))

        assertThat(filter.shows("", "net")).describedAs("intermediate directory").isTrue
    }

    @Test
    fun `the root hides a sibling outside the allowed subtree`() {
        val filter = filterOf(rule("ingot.artifact.net.onelitefeather.read"))

        assertThat(filter.shows("", "org")).describedAs("sibling directory").isFalse
    }

    @Test
    fun `the root hides a file although a subtree lies beneath the root`() {
        val filter = filterOf(rule("ingot.artifact.net.onelitefeather.read"))

        assertThat(filter.shows("", "index.html", isDirectory = false)).describedAs("file at the root").isFalse
    }

    @Test
    fun `an intermediate directory shows the next segment of the allowed subtree only`() {
        val filter = filterOf(rule("ingot.artifact.net.onelitefeather.read"))

        assertThat(filter.shows("net", "onelitefeather")).describedAs("allowed subtree").isTrue
        assertThat(filter.shows("net", "other")).describedAs("sibling of the subtree").isFalse
    }

    @Test
    fun `a directory below the allowed subtree root shows its children`() {
        val filter = filterOf(rule("ingot.artifact.net.onelitefeather.read"))

        assertThat(filter.shows("net/onelitefeather", "mycelium-bom")).describedAs("artifact directory").isTrue
        assertThat(filter.shows("net/onelitefeather/mycelium-bom/1.0", "mycelium-bom-1.0.jar", isDirectory = false)).describedAs("file").isTrue
    }

    @Test
    fun `a longer sibling name is not part of the subtree`() {
        val filter = filterOf(rule("ingot.artifact.net.onelitefeather.read"))

        assertThat(filter.shows("net", "onelitefeatherX")).isFalse
    }

    @Test
    fun `a negated artifact node hides its subtree inside an allowed subtree`() {
        val filter = filterOf(
            rule("ingot.artifact.net.onelitefeather.read"),
            rule("ingot.artifact.net.onelitefeather:internal.read", allowed = false),
        )

        assertThat(filter.shows("net/onelitefeather", "internal")).describedAs("denied artifact").isFalse
        assertThat(filter.shows("net/onelitefeather", "mycelium-bom")).describedAs("rest of the group").isTrue
    }

    @Test
    fun `a negated artifact node hides a subtree of a repository wide read`() {
        val filter = filterOf(
            rule("ingot.repository.private.read"),
            rule("ingot.artifact.net.onelitefeather:internal.read", allowed = false),
        )

        assertThat(filter.shows("net/onelitefeather", "internal")).describedAs("denied artifact").isFalse
        assertThat(filter.shows("net/onelitefeather", "mycelium-bom")).describedAs("other artifact").isTrue
        assertThat(filter.shows("", "org")).describedAs("repository wide read shows the rest").isTrue
    }

    @Test
    fun `an artifact subtree shows its parents but not its siblings`() {
        val filter = filterOf(rule("ingot.artifact.net.onelitefeather:mycelium-bom.read"))

        assertThat(filter.shows("net/onelitefeather", "mycelium-bom")).describedAs("the artifact").isTrue
        assertThat(filter.shows("net/onelitefeather", "mycelium-api")).describedAs("sibling artifact").isFalse
        assertThat(filter.shows("net", "onelitefeather")).describedAs("parent").isTrue
    }

    @Test
    fun `two subtrees show both of their branches`() {
        val filter = filterOf(rule("ingot.artifact.net.onelitefeather.read"), rule("ingot.artifact.org.other.read"))

        assertThat(filter.shows("", "net")).isTrue
        assertThat(filter.shows("", "org")).isTrue
        assertThat(filter.shows("", "com")).isFalse
    }

    @Test
    fun `a case difference is outside the subtree`() {
        val filter = filterOf(rule("ingot.artifact.net.onelitefeather.read"))

        assertThat(filter.shows("", "Net")).describedAs("upper case root segment").isFalse
        assertThat(filter.shows("net", "OneLiteFeather")).describedAs("upper case group segment").isFalse
    }

    @Test
    fun `a parent segment in the directory shows nothing`() {
        val filter = filterOf(rule("ingot.artifact.net.onelitefeather.read"))

        assertThat(filter.shows("net/../org", "other")).describedAs("path that leaves the allowed subtree").isFalse
        assertThat(filter.shows("net/onelitefeather/..", "other")).describedAs("path that cannot be normalised").isFalse
        assertThat(filter.leadsToReadable("net/../org")).isFalse
    }

    @Test
    fun `a cancelled allow leaves nothing to lead into`() {
        val filter = filterOf(
            rule("ingot.artifact.net.onelitefeather.read"),
            rule("ingot.artifact.net.onelitefeather.read", allowed = false),
        )

        assertThat(filter.shows("", "net")).isFalse
    }

    @Test
    fun `the group wildcard shows every directory`() {
        val filter = filterOf(rule("ingot.artifact.*.read"))

        assertThat(filter.shows("", "org")).isTrue
        assertThat(filter.shows("org/other", "lib")).isTrue
    }

    @Test
    fun `a filter without an allowed artifact read node shows nothing`() {
        val alice = subject(rule("ingot.artifact.net.onelitefeather.deploy"), rule("ingot.artifact.org.other:lib.read", allowed = false))
        val filter = ArtifactListingFilter.of(alice, "private") ?: error("a denial of read activates the filter")

        assertThat(filter.shows("", "net")).isFalse
    }

    @Test
    fun `a directory leads to a readable subtree only on the way to it`() {
        val filter = filterOf(rule("ingot.artifact.net.onelitefeather.read"))

        assertThat(filter.leadsToReadable("")).describedAs("root").isTrue
        assertThat(filter.leadsToReadable("net")).describedAs("intermediate").isTrue
        assertThat(filter.leadsToReadable("org")).describedAs("sibling").isFalse
        assertThat(filter.leadsToReadable("net/other")).describedAs("sibling below").isFalse
    }
}
