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

import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.shared.http.CacheScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RepositoryCacheScopeTest : MavenSpecification() {

    override fun repositories() = listOf(
        RepositorySettings("open", visibility = PUBLIC),
        RepositorySettings("closed", visibility = PRIVATE),
        RepositorySettings("open-group", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = "open"),
        )),
        RepositorySettings("mixed-group", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = "open"),
            MirroredRepositorySettings(reference = "closed"),
        )),
        // Remote references only: this instance cannot judge the access rules of the upstream host
        RepositorySettings("remote-proxy", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = REMOTE_REPOSITORY, store = true, authorization = REMOTE_AUTH),
        )),
    )

    @Test
    fun `should answer public for a public group whose members are public`() {
        assertThat(mavenFacade.cacheScopeOf("open-group")).describedAs("scope of open-group").isEqualTo(CacheScope.PUBLIC)
    }

    @Test
    fun `should answer private for a public group that references a private repository`() {
        assertThat(mavenFacade.cacheScopeOf("mixed-group")).describedAs("scope of mixed-group").isEqualTo(CacheScope.PRIVATE)
    }

    @Test
    fun `should answer public for a public repository that references only remote hosts`() {
        assertThat(mavenFacade.cacheScopeOf("remote-proxy")).describedAs("scope of remote-proxy").isEqualTo(CacheScope.PUBLIC)
    }

}
