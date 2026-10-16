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
}
