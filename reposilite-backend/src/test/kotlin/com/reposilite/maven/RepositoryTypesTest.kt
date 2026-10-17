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

import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.api.DeployRequest
import com.reposilite.maven.api.RepositoryListQuery
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.storage.api.toLocation
import com.reposilite.storage.filesystem.FileSystemStorageProviderSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertOk

internal class RepositoryTypesTest : MavenSpecification() {

    override fun repositories() = listOf(
        RepositorySettings("hosted", visibility = PUBLIC, type = RepositoryType.HOSTED),
        RepositorySettings("typed-proxy", visibility = PUBLIC, type = RepositoryType.PROXY, proxied = mutableListOf(
            MirroredRepositorySettings(reference = REMOTE_REPOSITORY, store = true, authorization = REMOTE_AUTH),
        )),
        RepositorySettings("typed-group", visibility = PUBLIC, type = RepositoryType.GROUP, proxied = mutableListOf(
            MirroredRepositorySettings(reference = "hosted"),
            MirroredRepositorySettings(reference = "typed-proxy"),
        )),
        // The production shape: an untyped group with an empty entry between its loopback references
        RepositorySettings("legacy-group", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = "hosted"),
            MirroredRepositorySettings(reference = ""),
            MirroredRepositorySettings(reference = "typed-proxy"),
        )),
        RepositorySettings("mixed", visibility = PUBLIC, storageProvider = FileSystemStorageProviderSettings(quota = "10GB"), proxied = mutableListOf(
            MirroredRepositorySettings(reference = "hosted"),
            MirroredRepositorySettings(reference = REMOTE_REPOSITORY, store = true, authorization = REMOTE_AUTH),
        )),
    )

    @Nested
    inner class Mirrors {

        @Test
        fun `should keep storing the files a typed proxy fetches from its upstream`() {
            // given: a file the upstream serves to the proxy
            val file = FileSpec("typed-proxy", "/com/example/cached/1.0/cached-1.0.pom", REMOTE_CONTENT)

            // when: the file is requested through the proxy
            assertOk(mavenFacade.findFile(file.toLookupRequest(UNAUTHORIZED)))

            // then: the copy is stored in the proxy, so the proxy caches although it takes no uploads
            assertThat(repo("typed-proxy").storageProvider.exists(file.gav())).isTrue
        }

        @Test
        fun `should serve a file of a typed group from its member without storing it`() {
            // given: a file deployed to the hosted member
            assertOk(mavenFacade.deployFile(DeployRequest(repo("hosted"), "com/example/member/1.0/member-1.0.jar".toLocation(), "tester", "member".byteInputStream(), false)))

            // when: the file is requested through the group
            val response = mavenFacade.findFile(FileSpec("typed-group", "/com/example/member/1.0/member-1.0.jar", "member").toLookupRequest(UNAUTHORIZED))

            // then: the group answers, and the group itself stores nothing
            assertOk(response)
            assertThat(repo("typed-group").storageProvider.exists("com/example/member/1.0/member-1.0.jar".toLocation())).isFalse
        }

    }

    @Nested
    inner class Listing {

        @Test
        fun `should expose the effective type and whether it is explicit for every listed repository`() {
            // when: the repositories are listed
            val summaries = mavenFacade.listRepositories(UNAUTHORIZED, RepositoryListQuery()).repositories
                .associate { it.id to (it.type to it.typeExplicit) }

            // then: typed repositories report their declared type as explicit, untyped ones are derived
            assertThat(summaries).containsExactlyInAnyOrderEntriesOf(
                mapOf(
                    "hosted" to ("hosted" to true),
                    "typed-proxy" to ("proxy" to true),
                    "typed-group" to ("group" to true),
                    "legacy-group" to ("group" to false),
                    "mixed" to ("mixed" to false),
                )
            )
        }

        @Test
        fun `should list the local members of a typed group in configuration order`() {
            // when: the members of the typed group are requested
            val members = assertOk(mavenFacade.findMembers(UNAUTHORIZED, "typed-group")).members

            // then: the local repositories are listed, the upstream is not
            assertThat(members).containsExactly("hosted", "typed-proxy")
        }

    }

    @Nested
    inner class StorageUsage {

        @Test
        fun `should measure a typed group without quota and a mixed repository with its quota`() {
            // when: the storage usage is measured
            val usage = mavenFacade.refreshStorageUsage().join().repositories.associateBy { it.id }

            // then: a group stores nothing of its own, so it is a group without quota
            assertThat(usage.getValue("typed-group").group).isTrue
            assertThat(usage.getValue("typed-group").quota).isNull()
            assertThat(usage.getValue("legacy-group").group).isTrue

            // and: a mixed repository holds content of its own, so it is measured as a repository with its quota
            assertThat(usage.getValue("mixed").group).isFalse
            assertThat(usage.getValue("mixed").quota).isEqualTo("10GB")
        }

    }

    private fun repo(name: String) =
        mavenFacade.getRepository(name)!!

}
