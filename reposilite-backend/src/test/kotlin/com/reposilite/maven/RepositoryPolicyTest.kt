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

import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class RepositoryPolicyTest : MavenSpecification() {

    override fun repositories() = listOf(
        // A repository that sets neither new field, as every existing installation has it
        RepositorySettings("plain", visibility = PUBLIC),
        RepositorySettings("release-only", visibility = PUBLIC, versionPolicy = VersionPolicy.RELEASE),
        RepositorySettings("snapshot-only", visibility = PUBLIC, versionPolicy = VersionPolicy.SNAPSHOT),
        RepositorySettings("frozen", visibility = PUBLIC, readOnly = true),
        RepositorySettings("typed-proxy", visibility = PUBLIC, type = RepositoryType.PROXY, proxied = mutableListOf(
            MirroredRepositorySettings(reference = REMOTE_REPOSITORY),
        )),
        RepositorySettings("typed-group", visibility = PUBLIC, type = RepositoryType.GROUP, proxied = mutableListOf(
            MirroredRepositorySettings(reference = "plain"),
        )),
    )

    @Nested
    inner class Effective {

        @Test
        fun `should resolve an absent version policy to mixed`() {
            assertThat(mavenFacade.getRepository("plain")!!.versionPolicy)
                .describedAs("version policy of a repository that sets none")
                .isEqualTo(VersionPolicy.MIXED)
        }

        @Test
        fun `should resolve an absent read-only flag to false`() {
            assertThat(mavenFacade.getRepository("plain")!!.readOnly)
                .describedAs("read-only flag of a repository that sets none")
                .isFalse
        }

        @Test
        fun `should keep an explicit version policy of the repository`() {
            assertThat(mavenFacade.getRepository("release-only")!!.versionPolicy).isEqualTo(VersionPolicy.RELEASE)
            assertThat(mavenFacade.getRepository("snapshot-only")!!.versionPolicy).isEqualTo(VersionPolicy.SNAPSHOT)
        }

        @Test
        fun `should keep an explicit read-only flag of the repository`() {
            assertThat(mavenFacade.getRepository("frozen")!!.readOnly).isTrue
        }
    }

    private fun repo(name: String) =
        mavenFacade.getRepository(name)!!

}
