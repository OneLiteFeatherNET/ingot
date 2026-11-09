/*
 * Copyright (c) 2023 dzikoysk
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

import com.reposilite.maven.RepositoryVisibility.HIDDEN
import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.shared.http.CacheScope
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class MavenCacheScopeTest {

    private fun node(visibility: RepositoryVisibility, vararg loopbackReferences: String) =
        CacheScopeNode(visibility, loopbackReferences.toList())

    private fun scopeOf(start: String, nodes: Map<String, CacheScopeNode>, privateAsPublic: Boolean = false): CacheScope =
        resolveCacheScope(start, { nodes[it] }, privateAsPublic)

    @Test
    fun `should answer public for a public repository without members`() {
        val scope = scopeOf("open", mapOf("open" to node(PUBLIC)))

        assertThat(scope).describedAs("scope of a public repository").isEqualTo(CacheScope.PUBLIC)
    }

    @ParameterizedTest
    @EnumSource(value = RepositoryVisibility::class, names = ["PRIVATE", "HIDDEN"])
    fun `should answer private for a repository that is not public`(visibility: RepositoryVisibility) {
        val scope = scopeOf("closed", mapOf("closed" to node(visibility)))

        assertThat(scope).describedAs("scope of a $visibility repository").isEqualTo(CacheScope.PRIVATE)
    }

    @Test
    fun `should answer public for a public group whose members are all public`() {
        val scope = scopeOf(
            "group",
            mapOf(
                "group" to node(PUBLIC, "open", "other-open"),
                "open" to node(PUBLIC),
                "other-open" to node(PUBLIC),
            )
        )

        assertThat(scope).describedAs("scope of a group of public members").isEqualTo(CacheScope.PUBLIC)
    }

    @Test
    fun `should answer private for a public group with a private member`() {
        val scope = scopeOf(
            "group",
            mapOf(
                "group" to node(PUBLIC, "open", "closed"),
                "open" to node(PUBLIC),
                "closed" to node(PRIVATE),
            )
        )

        assertThat(scope).describedAs("scope of a group that reaches a private member").isEqualTo(CacheScope.PRIVATE)
    }

    @Test
    fun `should find a private member that lies two groups down`() {
        val scope = scopeOf(
            "outer",
            mapOf(
                "outer" to node(PUBLIC, "inner"),
                "inner" to node(PUBLIC, "closed"),
                "closed" to node(HIDDEN),
            )
        )

        assertThat(scope).describedAs("scope of a group that reaches a hidden member through another group").isEqualTo(CacheScope.PRIVATE)
    }

    @Test
    fun `should end the walk on a cycle of public groups`() {
        val scope = scopeOf(
            "first",
            mapOf(
                "first" to node(PUBLIC, "second"),
                "second" to node(PUBLIC, "first"),
            )
        )

        assertThat(scope).describedAs("scope of a cycle of public groups").isEqualTo(CacheScope.PUBLIC)
    }

    @Test
    fun `should answer private for a cycle of groups that reaches a private member`() {
        val scope = scopeOf(
            "first",
            mapOf(
                "first" to node(PUBLIC, "second"),
                "second" to node(PUBLIC, "first", "closed"),
                "closed" to node(PRIVATE),
            )
        )

        assertThat(scope).describedAs("scope of a cycle that reaches a private member").isEqualTo(CacheScope.PRIVATE)
    }

    @Test
    fun `should ignore a loopback reference to a repository that does not exist`() {
        val scope = scopeOf("group", mapOf("group" to node(PUBLIC, "missing")))

        assertThat(scope).describedAs("scope of a group with a missing member").isEqualTo(CacheScope.PUBLIC)
    }

    @Test
    fun `should answer private when the repository itself is unknown`() {
        val scope = scopeOf("unknown", emptyMap())

        assertThat(scope).describedAs("scope of an unknown repository fails closed").isEqualTo(CacheScope.PRIVATE)
    }

    @Test
    fun `should answer public for a private repository when the escape hatch is on`() {
        val scope = scopeOf("closed", mapOf("closed" to node(PRIVATE)), privateAsPublic = true)

        assertThat(scope).describedAs("scope of a private repository with the escape hatch").isEqualTo(CacheScope.PUBLIC)
    }

    @Test
    fun `should read the escape hatch as off when nothing is set`() {
        assertThat(PrivateAsPublicSetting.read(emptyMap())).describedAs("escape hatch without a value").isFalse
    }

    @Test
    fun `should read the escape hatch as on for true`() {
        assertThat(PrivateAsPublicSetting.read(mapOf("INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC" to "true"))).isTrue
    }

    @Test
    fun `should read the escape hatch as off for false`() {
        assertThat(PrivateAsPublicSetting.read(mapOf("INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC" to "false"))).isFalse
    }

    @Test
    fun `should read the legacy spelling when the ingot spelling is not set`() {
        assertThat(PrivateAsPublicSetting.read(mapOf("REPOSILITE_MAVEN_CACHE_PRIVATE_AS_PUBLIC" to "true"))).isTrue
    }

    @Test
    fun `should prefer the ingot spelling over the legacy spelling`() {
        val value = PrivateAsPublicSetting.read(
            mapOf(
                "INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC" to "false",
                "REPOSILITE_MAVEN_CACHE_PRIVATE_AS_PUBLIC" to "true",
            )
        )

        assertThat(value).describedAs("ingot spelling wins over the legacy one").isFalse
    }

    @Test
    fun `should treat an empty value as not set`() {
        assertThat(PrivateAsPublicSetting.read(mapOf("INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC" to "  "))).isFalse
    }

    @Test
    fun `should refuse an unknown value with a message that names the setting`() {
        assertThatThrownBy { PrivateAsPublicSetting.read(mapOf("INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC" to "yes")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC")
            .hasMessageContaining("yes")
    }

}
