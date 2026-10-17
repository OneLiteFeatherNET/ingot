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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class RepositoryTypeTest {

    @ParameterizedTest
    @EnumSource(RepositoryType::class)
    fun `should report a declared type as explicit whatever references it is configured with`(declared: RepositoryType) {
        // when: the effective type is derived for a declared type with local and external references
        val effective = effectiveRepositoryType(declared, loopbackReferences = 2, externalReferences = 1)

        // then: the declared type is reported, the references do not change it
        assertThat(effective).describedAs("effective type of declared $declared").isEqualTo(EffectiveRepositoryType.valueOf(declared.name))
    }

    @Test
    fun `should derive hosted for an untyped repository without proxied repositories`() {
        assertThat(effectiveRepositoryType(null, loopbackReferences = 0, externalReferences = 0)).isEqualTo(EffectiveRepositoryType.HOSTED)
    }

    @Test
    fun `should derive group for an untyped repository whose proxied repositories are all local`() {
        assertThat(effectiveRepositoryType(null, loopbackReferences = 2, externalReferences = 0)).isEqualTo(EffectiveRepositoryType.GROUP)
    }

    @Test
    fun `should derive proxy for an untyped repository whose proxied repositories are all external`() {
        assertThat(effectiveRepositoryType(null, loopbackReferences = 0, externalReferences = 3)).isEqualTo(EffectiveRepositoryType.PROXY)
    }

    @Test
    fun `should derive mixed for an untyped repository that proxies local and external repositories`() {
        assertThat(effectiveRepositoryType(null, loopbackReferences = 1, externalReferences = 1)).isEqualTo(EffectiveRepositoryType.MIXED)
    }

    @Test
    fun `should expose the lowercase wire names of the effective types`() {
        assertThat(EffectiveRepositoryType.entries.map { it.wireName })
            .containsExactly("hosted", "proxy", "group", "mixed")
    }

    @Test
    fun `should accept uploads to an untyped repository`() {
        assertThat(null.acceptsUploads()).isTrue
    }

    @Test
    fun `should accept uploads to a hosted repository`() {
        assertThat(RepositoryType.HOSTED.acceptsUploads()).isTrue
    }

    @Test
    fun `should refuse uploads to a proxy`() {
        assertThat(RepositoryType.PROXY.acceptsUploads()).isFalse
    }

    @Test
    fun `should refuse uploads to a group`() {
        assertThat(RepositoryType.GROUP.acceptsUploads()).isFalse
    }

}
