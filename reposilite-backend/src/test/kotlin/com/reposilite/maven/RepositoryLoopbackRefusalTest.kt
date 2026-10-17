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

import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.api.LookupRequest
import com.reposilite.maven.application.MirrorCredentials
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.shared.http.AuthenticationMethod.BASIC
import com.reposilite.shared.http.AuthenticationMethod.LOOPBACK_LINK
import com.reposilite.storage.api.toLocation
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.RoutePermission.READ
import io.javalin.http.HttpStatus.BAD_GATEWAY
import io.javalin.http.HttpStatus.NOT_FOUND
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertOk

/**
 * A group that mirrors private repositories through loopback references. An internal refusal of the mirror identity
 * must never reach the browser as 401, because the browser then ends a session that is still valid.
 */
internal class RepositoryLoopbackRefusalTest : MavenSpecification() {

    // Maven metadata is mirror first under the default storage policy, so a refusal of a remote reaches the caller
    // unless it is handled. A pom is local first and would hide the refusal behind a local not found.
    private val artifact = "com/example/app/maven-metadata.xml"

    override fun repositories() = listOf(
        RepositorySettings("internal", visibility = PRIVATE),
        RepositorySettings("other", visibility = PRIVATE),
        // The shape of the production group: a private member read with a legacy basic credential the instance does not use.
        RepositorySettings("team", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = "internal", authorization = MirrorCredentials(BASIC, "reposilite-mirror", "legacy-secret")),
        )),
        // The same member read through a mirror identity that the instance knows.
        RepositorySettings("mirrored", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = "internal", authorization = MirrorCredentials(LOOPBACK_LINK, "mirror", "mirror-secret")),
        )),
        RepositorySettings("remote-team", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = REMOTE_REPOSITORY_REFUSING, authorization = REMOTE_AUTH),
        )),
    )

    @Nested
    inner class SignedInCaller {

        @Test
        fun `should answer not found, not unauthorized, for the metadata of a group whose member the mirror cannot read`() {
            // given: a signed in token that has no route to the private member
            val token = createAccessToken("alice", "secret", "other", "com/example", READ)

            // when: the maven metadata of the group is requested, as the details card does
            val response = mavenFacade.findFile(LookupRequest(token, "team", artifact.toLocation()))

            // then: the browser sees that the file is not there, not that its session was refused
            assertThat(response.error.status).isEqualTo(NOT_FOUND.code)
        }
    }

    @Nested
    inner class AnonymousCaller {

        @Test
        fun `should answer not found, not unauthorized, for the metadata of a group whose member the mirror cannot read`() {
            // when: the maven metadata of the group is requested without a token
            val response = mavenFacade.findFile(LookupRequest(null, "team", artifact.toLocation()))

            // then: the refusal of the mirror identity is not an answer about the caller
            assertThat(response.error.status).isEqualTo(NOT_FOUND.code)
        }
    }

    @Nested
    inner class MirrorIdentity {

        @Test
        fun `should serve the metadata of a member that the mirror identity can read`() {
            // given: a mirror identity with a route to the private member, and a file in that member
            createAccessToken("mirror", "mirror-secret", "internal", "com/example", READ)
            addFileToRepository(FileSpec("internal", artifact, "<project/>"))

            // when: the file is requested through the group
            val response = mavenFacade.findFile(LookupRequest(null, "mirrored", artifact.toLocation()))

            // then: the mirror identity reads the member and the file is served
            assertOk(response)
        }
    }

    @Nested
    inner class ExternalMirror {

        @Test
        fun `should answer bad gateway for the metadata of a remote mirror that refuses its credentials`() {
            // when: a file is requested through a remote mirror that refuses its credentials
            val response = mavenFacade.findFile(LookupRequest(null, "remote-team", artifact.toLocation()))

            // then: the failure belongs to the instance, so it is not relayed as 401 to the browser
            assertThat(response.error.status).isEqualTo(BAD_GATEWAY.code)
        }
    }
}
