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
import com.reposilite.maven.application.MirrorCredentials
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.shared.http.AuthenticationMethod
import com.reposilite.shared.http.CacheScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RepositoryCacheScopeEscapeHatchTest : MavenSpecification() {

    override fun cachePrivateAsPublic(): Boolean = true

    override fun repositories() = listOf(
        RepositorySettings("credentialed-proxy", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(
                reference = REMOTE_REPOSITORY,
                store = true,
                authorization = MirrorCredentials(AuthenticationMethod.BASIC, "panda", "secret"),
            ),
        )),
    )

    @Test
    fun `should answer public for a credentialed mirror when the escape hatch is on`() {
        assertThat(mavenFacade.cacheScopeOf("credentialed-proxy"))
            .describedAs("scope of credentialed-proxy with INGOT_MAVEN_CACHE_PRIVATE_AS_PUBLIC=true")
            .isEqualTo(CacheScope.PUBLIC)
    }

}
