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
package net.onelitefeather.ingot.identity.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** The node grammar of the permission model (identity model, permission nodes, decision D1). Pure, no storage. */
internal class NodePatternTest {

    @Test
    fun `accepts a repository node with a plain name`() {
        assertThat(NodePattern.problemOf("ingot.repository.releases.read")).describedAs("problem").isNull()
    }

    @Test
    fun `accepts wildcards as whole segments in the repository and action positions`() {
        assertThat(NodePattern.problemOf("ingot.repository.*.read")).isNull()
        assertThat(NodePattern.problemOf("ingot.repository.releases.*")).isNull()
        assertThat(NodePattern.problemOf("ingot.repository.*.*")).isNull()
    }

    @Test
    fun `accepts a repository name that contains dots`() {
        assertThat(NodePattern.problemOf("ingot.repository.maven.central.deploy")).isNull()
    }

    @Test
    fun `accepts every admin area and the admin wildcard`() {
        listOf("settings", "users", "teams", "tokens", "maintenance", "audit", "console", "dashboard", "*").forEach { area ->
            assertThat(NodePattern.problemOf("ingot.admin.$area")).describedAs("admin area $area").isNull()
        }
    }

    @Test
    fun `accepts the star node that allows everything`() {
        assertThat(NodePattern.problemOf("*")).isNull()
    }

    @Test
    fun `refuses a repository node without an action`() {
        assertThat(NodePattern.problemOf("ingot.repository.releases")).describedAs("problem").isNotNull()
    }

    @Test
    fun `refuses a repository wildcard without an action`() {
        assertThat(NodePattern.problemOf("ingot.repository.*")).describedAs("problem").isNotNull()
    }

    @Test
    fun `refuses an unknown action`() {
        assertThat(NodePattern.problemOf("ingot.repository.releases.write")).describedAs("problem").isNotNull()
    }

    @Test
    fun `refuses an unknown admin area`() {
        assertThat(NodePattern.problemOf("ingot.admin.billing")).describedAs("problem").isNotNull()
    }

    @Test
    fun `refuses a wildcard that is not a whole segment`() {
        assertThat(NodePattern.problemOf("ingot.repository.rel*.read")).describedAs("problem").isNotNull()
        assertThat(NodePattern.problemOf("ingot.admin.us*")).describedAs("problem").isNotNull()
    }

    @Test
    fun `refuses a node outside the known prefixes`() {
        assertThat(NodePattern.problemOf("ingot.*")).describedAs("ingot wildcard").isNotNull()
        assertThat(NodePattern.problemOf("ingot.admin")).describedAs("bare admin").isNotNull()
        assertThat(NodePattern.problemOf("foo.bar.read")).describedAs("foreign prefix").isNotNull()
    }

    @Test
    fun `refuses the negation sign, which is a flag and not part of the name`() {
        assertThat(NodePattern.problemOf("-ingot.repository.releases.read")).describedAs("problem").isNotNull()
    }

    @Test
    fun `refuses empty segments and whitespace`() {
        assertThat(NodePattern.problemOf("ingot.repository..read")).describedAs("empty repository").isNotNull()
        assertThat(NodePattern.problemOf("ingot.repository.rel ease.read")).describedAs("whitespace").isNotNull()
    }

    @Test
    fun `refuses a node longer than 255 characters`() {
        val long = "ingot.repository." + "a".repeat(250) + ".read"
        assertThat(NodePattern.problemOf(long)).describedAs("problem").isNotNull()
    }

    @Test
    fun `specificity counts the literal parts of a pattern`() {
        assertThat(NodePattern.of("ingot.repository.releases.read").specificity).describedAs("exact repository node").isEqualTo(4)
        assertThat(NodePattern.of("ingot.repository.*.read").specificity).describedAs("wildcard repository").isEqualTo(3)
        assertThat(NodePattern.of("ingot.repository.*.*").specificity).describedAs("double wildcard").isEqualTo(2)
        assertThat(NodePattern.of("ingot.admin.users").specificity).describedAs("exact admin area").isEqualTo(3)
        assertThat(NodePattern.of("ingot.admin.*").specificity).describedAs("admin wildcard").isEqualTo(2)
        assertThat(NodePattern.of("*").specificity).describedAs("star").isEqualTo(0)
    }

    @Test
    fun `a wildcard pattern matches a concrete repository request`() {
        val request = NodePattern.repository("releases", "read")
        assertThat(NodePattern.of("ingot.repository.*.read").matches(request)).describedAs("any repository").isTrue
        assertThat(NodePattern.of("ingot.repository.releases.*").matches(request)).describedAs("any action").isTrue
    }

    @Test
    fun `a pattern for another repository or action does not match`() {
        val request = NodePattern.repository("releases", "read")
        assertThat(NodePattern.of("ingot.repository.snapshots.read").matches(request)).describedAs("other repository").isFalse
        assertThat(NodePattern.of("ingot.repository.releases.deploy").matches(request)).describedAs("other action").isFalse
    }

    @Test
    fun `the star node matches every request`() {
        assertThat(NodePattern.of("*").matches(NodePattern.admin("users"))).describedAs("admin area").isTrue
        assertThat(NodePattern.of("*").matches(NodePattern.repository("releases", "manage"))).describedAs("repository").isTrue
    }

    @Test
    fun `the admin wildcard matches the instance admin request but a single area does not match it`() {
        val adminRequest = NodePattern.admin("*")
        assertThat(NodePattern.of("ingot.admin.*").matches(adminRequest)).describedAs("admin wildcard").isTrue
        assertThat(NodePattern.of("ingot.admin.users").matches(adminRequest)).describedAs("single area against the wildcard").isFalse
    }

    @Test
    fun `the text of a pattern is the node as written`() {
        assertThat(NodePattern.of("ingot.repository.*.deploy").text).isEqualTo("ingot.repository.*.deploy")
    }

    // artifact scoped nodes

    @ParameterizedTest(name = "{0}")
    @MethodSource("validArtifactNodes")
    fun `parses a valid artifact node into its parts`(
        name: String,
        text: String,
        repository: String?,
        groupId: String,
        artifactId: String?,
        action: String,
    ) {
        assertThat(NodePattern.problemOf(text)).describedAs("problem of $text ($name)").isNull()
        val node = NodePattern.of(text)

        assertThat(node.repository).describedAs("repository").isEqualTo(repository)
        assertThat(node.groupId).describedAs("groupId").isEqualTo(groupId)
        assertThat(node.artifactId).describedAs("artifactId").isEqualTo(artifactId)
        assertThat(node.action).describedAs("action").isEqualTo(action)
        assertThat(node.text).describedAs("text as written").isEqualTo(text)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidArtifactNodes")
    fun `refuses an invalid artifact node and names the reason`(name: String, text: String, expectedReason: String) {
        val problem = NodePattern.problemOf(text)

        assertThat(problem).describedAs("problem of $text ($name)").isNotNull()
        assertThat(problem).describedAs("reason for $text ($name)").contains(expectedReason)
    }

    @Test
    fun `refuses an artifact node longer than 255 characters`() {
        val long = "ingot.artifact." + "a".repeat(250) + ".read"
        assertThat(NodePattern.problemOf(long)).describedAs("problem").isNotNull()
    }

    @Test
    fun `the repository name rule is stated when the artifact part of a qualified node is malformed`() {
        val problem = NodePattern.problemOf("ingot.repository.my.artifact.read")

        assertThat(problem).describedAs("problem").contains("may not contain a segment named 'artifact'")
        assertThat(problem).describedAs("separator hint").contains("separates the repository from the artifact part")
    }

    @Test
    fun `a repository node whose name has no artifact segment stays a repository node`() {
        val node = NodePattern.of("ingot.repository.artifacts.read")

        assertThat(node.repository).describedAs("repository").isEqualTo("artifacts")
        assertThat(node.groupId).describedAs("not an artifact node").isNull()
    }

    @Test
    fun `a group segment named artifact is a plain group segment`() {
        val node = NodePattern.of("ingot.repository.releases.artifact.org.artifact.deploy")

        assertThat(node.repository).describedAs("repository").isEqualTo("releases")
        assertThat(node.groupId).describedAs("groupId").isEqualTo("org.artifact")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("artifactMatches")
    fun `matches a concrete request by repository, action and path`(name: String, pattern: String, request: NodePattern, expected: Boolean) {
        assertThat(NodePattern.of(pattern).matches(request))
            .describedAs("$pattern against ${request.text} ($name)")
            .isEqualTo(expected)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("artifactOrdering")
    fun `ranks the more specific artifact node higher`(name: String, lower: String, higher: String) {
        val lowerRank = NodePattern.of(lower).rank
        val higherRank = NodePattern.of(higher).rank

        assertThat(higherRank).describedAs("$higher must rank above $lower ($name)").isGreaterThan(lowerRank)
        assertThat(lowerRank).describedAs("$lower must rank below $higher ($name)").isLessThan(higherRank)
    }

    @Test
    fun `artifact nodes that differ only in the group name rank equal`() {
        assertThat(NodePattern.of("ingot.artifact.net.read").rank).isEqualTo(NodePattern.of("ingot.artifact.org.read").rank)
    }

    @Test
    fun `an artifact node ranks above every repository node and carries the artifact tier as its Int specificity`() {
        assertThat(NodePattern.of("ingot.artifact.*.*").rank).describedAs("weakest artifact node")
            .isGreaterThan(NodePattern.of("ingot.repository.releases.read").rank)
        assertThat(NodePattern.of("ingot.artifact.*.*").specificity).describedAs("tier").isEqualTo(5)
    }

    @Test
    fun `the rank of the existing node kinds equals their Int specificity`() {
        listOf("*", "ingot.repository.*.*", "ingot.repository.releases.read", "ingot.admin.users", "ingot.admin.*").forEach { text ->
            val node = NodePattern.of(text)
            assertThat(node.rank).describedAs("rank of $text").isEqualTo(NodeSpecificity(node.specificity))
        }
    }

    @Test
    fun `a request path is normalised before it is matched`() {
        val pattern = NodePattern.of("ingot.artifact.net.onelitefeather.deploy")

        assertThat(pattern.matches(NodePattern.repositoryPath("releases", "deploy", "//net//onelitefeather/x.jar")))
            .describedAs("repeated slashes").isTrue
        assertThat(pattern.matches(NodePattern.repositoryPath("releases", "deploy", "net/onelitefeather/../other/x.jar")))
            .describedAs("parent segment never matches").isFalse
    }

    @Test
    fun `names the kind of a node`() {
        assertThat(NodePattern.of("*").kindName).isEqualTo("EVERYTHING")
        assertThat(NodePattern.of("ingot.repository.releases.read").kindName).isEqualTo("REPOSITORY")
        assertThat(NodePattern.of("ingot.admin.users").kindName).isEqualTo("ADMIN")
        assertThat(NodePattern.of("ingot.artifact.net.read").kindName).isEqualTo("ARTIFACT")
    }

    companion object {

        @JvmStatic
        fun validArtifactNodes(): Stream<Arguments> = Stream.of(
            Arguments.of("group only", "ingot.artifact.net.onelitefeather.deploy", null, "net.onelitefeather", null, "deploy"),
            Arguments.of("group and artifact", "ingot.artifact.net.onelitefeather:mycelium-bom.deploy", null, "net.onelitefeather", "mycelium-bom", "deploy"),
            Arguments.of("single segment group", "ingot.artifact.net.read", null, "net", null, "read"),
            Arguments.of("every group", "ingot.artifact.*.read", null, "*", null, "read"),
            Arguments.of("action wildcard", "ingot.artifact.net.onelitefeather.*", null, "net.onelitefeather", null, "*"),
            Arguments.of("delete action", "ingot.artifact.net.delete", null, "net", null, "delete"),
            Arguments.of("artifact id with dots", "ingot.artifact.net:my.lib.read", null, "net", "my.lib", "read"),
            Arguments.of("artifact id wildcard equals no artifact id", "ingot.artifact.net:*.read", null, "net", null, "read"),
            Arguments.of("star group with star artifact id", "ingot.artifact.*:*.read", null, "*", null, "read"),
            Arguments.of("underscore and hyphen", "ingot.artifact.my_org.sub-group:a-b_c.read", null, "my_org.sub-group", "a-b_c", "read"),
            Arguments.of("qualified with dotted repository", "ingot.repository.my.repo.artifact.net:x.read", "my.repo", "net", "x", "read"),
            Arguments.of("qualified group only", "ingot.repository.releases.artifact.net.onelitefeather.*", "releases", "net.onelitefeather", null, "*"),
        )

        @JvmStatic
        fun invalidArtifactNodes(): Stream<Arguments> = Stream.of(
            Arguments.of("manage is not path scoped", "ingot.artifact.net.manage", "manage"),
            Arguments.of("unknown action", "ingot.artifact.net.write", "unknown"),
            Arguments.of("no group", "ingot.artifact.read", "needs a groupId and an action"),
            Arguments.of("nothing after the prefix", "ingot.artifact.", "needs a groupId and an action"),
            Arguments.of("star group with an artifact id is ambiguous", "ingot.artifact.*:mycelium.read", "ambiguous"),
            Arguments.of("wildcard inside a group", "ingot.artifact.net.*.read", "whole"),
            Arguments.of("partial wildcard in the group", "ingot.artifact.ne*t.read", "whole"),
            Arguments.of("empty group segment", "ingot.artifact.net..x.read", "empty segment"),
            Arguments.of("slash in the group", "ingot.artifact.net/x.read", "invalid character"),
            Arguments.of("two artifact separators", "ingot.artifact.net:a:b.read", "one ':'"),
            Arguments.of("empty artifact id", "ingot.artifact.net:.read", "artifactId"),
            Arguments.of("empty group before the artifact id", "ingot.artifact.:lib.read", "groupId"),
            Arguments.of("parent segment in the artifact id", "ingot.artifact.net:a..b.read", "empty segment"),
            Arguments.of("slash in the artifact id", "ingot.artifact.net:a/b.read", "invalid character"),
            Arguments.of("qualified manage", "ingot.repository.releases.artifact.net.x.manage", "manage"),
            Arguments.of("qualified without group", "ingot.repository.releases.artifact.read", "needs a groupId and an action"),
            Arguments.of("repository name with an artifact segment", "ingot.repository.my.artifact.read", "may not contain a segment named 'artifact'"),
            Arguments.of("repository named artifact", "ingot.repository.artifact.net.x.read", "may not contain a segment named 'artifact'"),
            Arguments.of("repository wildcard in the qualified form", "ingot.repository.*.artifact.net.x.read", "ingot.artifact."),
        )

        private fun path(repository: String, action: String, path: String): NodePattern =
            NodePattern.repositoryPath(repository, action, path)

        @JvmStatic
        fun artifactMatches(): Stream<Arguments> = Stream.of(
            // path requests, as the repository endpoints make them
            Arguments.of("group covers its subtree", "ingot.artifact.net.onelitefeather.deploy", path("releases", "deploy", "net/onelitefeather/mycelium-bom/1.0/a.jar"), true),
            Arguments.of("group covers its own directory", "ingot.artifact.net.onelitefeather.deploy", path("releases", "deploy", "net/onelitefeather"), true),
            Arguments.of("segment boundary protects a longer sibling", "ingot.artifact.net.onelitefeather.deploy", path("releases", "deploy", "net/onelitefeatherX/lib/a.jar"), false),
            Arguments.of("other group", "ingot.artifact.net.onelitefeather.deploy", path("releases", "deploy", "net/other/a.jar"), false),
            Arguments.of("path shorter than the group", "ingot.artifact.net.onelitefeather.deploy", path("releases", "deploy", "net"), false),
            Arguments.of("artifact id covers its subtree", "ingot.artifact.net.onelitefeather:mycelium-bom.deploy", path("releases", "deploy", "net/onelitefeather/mycelium-bom/1.0/a.jar"), true),
            Arguments.of("artifact id protects a longer sibling", "ingot.artifact.net.onelitefeather:mycelium-bom.deploy", path("releases", "deploy", "net/onelitefeather/mycelium-bom-extra/1.0/a.jar"), false),
            Arguments.of("artifact id protects another artifact", "ingot.artifact.net.onelitefeather:mycelium-bom.deploy", path("releases", "deploy", "net/onelitefeather/other/1.0/a.jar"), false),
            Arguments.of("artifact id with dots is one segment", "ingot.artifact.net:my.lib.deploy", path("releases", "deploy", "net/my.lib/1/a.jar"), true),
            Arguments.of("star group covers every path", "ingot.artifact.*.deploy", path("releases", "deploy", "any/thing/a.jar"), true),
            Arguments.of("star group covers the repository root", "ingot.artifact.*.deploy", path("releases", "deploy", ""), true),
            Arguments.of("group does not cover the repository root", "ingot.artifact.net.deploy", path("releases", "deploy", ""), false),
            Arguments.of("other action", "ingot.artifact.net.onelitefeather.read", path("releases", "deploy", "net/onelitefeather/a.jar"), false),
            Arguments.of("action wildcard covers deploy", "ingot.artifact.net.onelitefeather.*", path("releases", "deploy", "net/onelitefeather/a.jar"), true),
            Arguments.of("action wildcard never covers manage", "ingot.artifact.*.*", path("releases", "manage", "net/onelitefeather/a.jar"), false),
            Arguments.of("unqualified node covers any repository", "ingot.artifact.net.deploy", path("snapshots", "deploy", "net/a.jar"), true),
            Arguments.of("qualified node covers its repository", "ingot.repository.releases.artifact.net.deploy", path("releases", "deploy", "net/a.jar"), true),
            Arguments.of("qualified node does not cover another repository", "ingot.repository.releases.artifact.net.deploy", path("snapshots", "deploy", "net/a.jar"), false),
            Arguments.of("paths are case sensitive", "ingot.artifact.net.onelitefeather.deploy", path("releases", "deploy", "Net/OneLiteFeather/a.jar"), false),
            Arguments.of("plain repository request has no path below the root", "ingot.artifact.net.deploy", NodePattern.repository("releases", "deploy"), false),
            Arguments.of("plain repository request is covered by the star group", "ingot.artifact.*.deploy", NodePattern.repository("releases", "deploy"), true),
            // concrete artifact requests, as the check endpoint makes them
            Arguments.of("group node covers an artifact request", "ingot.artifact.net.onelitefeather.deploy", NodePattern.of("ingot.artifact.net.onelitefeather:mycelium-bom.deploy"), true),
            Arguments.of("artifact node covers the same artifact", "ingot.artifact.net.onelitefeather:mycelium-bom.deploy", NodePattern.of("ingot.artifact.net.onelitefeather:mycelium-bom.deploy"), true),
            Arguments.of("artifact node does not cover a group request", "ingot.artifact.net.onelitefeather:mycelium-bom.deploy", NodePattern.of("ingot.artifact.net.onelitefeather.deploy"), false),
            Arguments.of("artifact node does not cover a sibling artifact", "ingot.artifact.net.onelitefeather:mycelium-bom.deploy", NodePattern.of("ingot.artifact.net.onelitefeather:mycelium.deploy"), false),
            Arguments.of("qualified node does not cover an unqualified request", "ingot.repository.releases.artifact.net.deploy", NodePattern.of("ingot.artifact.net:x.deploy"), false),
            Arguments.of("unqualified node covers a qualified request", "ingot.artifact.net.deploy", NodePattern.of("ingot.repository.releases.artifact.net:x.deploy"), true),
            Arguments.of("qualified node covers a request in its repository", "ingot.repository.releases.artifact.net.deploy", NodePattern.of("ingot.repository.releases.artifact.net:x.deploy"), true),
            Arguments.of("repository node covers an artifact request in its repository", "ingot.repository.releases.deploy", NodePattern.of("ingot.repository.releases.artifact.net:x.deploy"), true),
            Arguments.of("repository node of another repository", "ingot.repository.snapshots.deploy", NodePattern.of("ingot.repository.releases.artifact.net:x.deploy"), false),
            Arguments.of("repository wildcard covers an unqualified request", "ingot.repository.*.deploy", NodePattern.of("ingot.artifact.net:x.deploy"), true),
            Arguments.of("named repository does not cover an unqualified request", "ingot.repository.releases.deploy", NodePattern.of("ingot.artifact.net:x.deploy"), false),
            Arguments.of("star node covers an artifact request", "*", NodePattern.of("ingot.artifact.net:x.deploy"), true),
            Arguments.of("admin node does not cover an artifact request", "ingot.admin.*", NodePattern.of("ingot.artifact.net:x.deploy"), false),
            Arguments.of("artifact node does not cover an admin request", "ingot.artifact.*.*", NodePattern.admin("users"), false),
        )

        /** Each row: the lower node, the higher node, and the step of the documented ordering it exercises. */
        @JvmStatic
        fun artifactOrdering(): Stream<Arguments> = Stream.of(
            Arguments.of("artifact above the most specific repository node", "ingot.repository.releases.read", "ingot.artifact.*.*"),
            Arguments.of("literal action beats wildcard action", "ingot.artifact.net.*", "ingot.artifact.net.read"),
            Arguments.of("longer group beats shorter group", "ingot.artifact.net.*", "ingot.artifact.net.onelitefeather.*"),
            Arguments.of("group length outranks the literal action", "ingot.artifact.net.read", "ingot.artifact.net.onelitefeather.*"),
            Arguments.of("star group is the weakest group", "ingot.artifact.*.read", "ingot.artifact.net.*"),
            Arguments.of("artifact id beats a longer group without one", "ingot.artifact.a.b.c.d.e.read", "ingot.artifact.a:x.*"),
            Arguments.of("artifact id with literal action beats the wildcard action", "ingot.artifact.a:x.*", "ingot.artifact.a:x.read"),
            Arguments.of("longer group beats shorter group with an artifact id", "ingot.artifact.a:x.read", "ingot.artifact.a.b:x.*"),
            Arguments.of("qualified beats unqualified with every other part equal", "ingot.artifact.net.read", "ingot.repository.r.artifact.net.read"),
            Arguments.of("qualified beats the most specific unqualified node", "ingot.artifact.a.b.c:x.read", "ingot.repository.r.artifact.*.*"),
            Arguments.of("qualified literal action beats qualified wildcard", "ingot.repository.r.artifact.net.*", "ingot.repository.r.artifact.net.read"),
            Arguments.of("qualified artifact id beats qualified group", "ingot.repository.r.artifact.a.b.c.read", "ingot.repository.r.artifact.a:x.*"),
        )
    }
}
