package com.reposilite.maven

import com.reposilite.maven.RepositoryVisibility.HIDDEN
import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.RoutePermission.READ
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk

internal class RepositoryMembersTest : MavenSpecification() {

    // Deliberately not sorted by id, so the tests also prove that the references keep their configuration order.
    override fun repositories() = listOf(
        RepositorySettings("releases", visibility = PUBLIC),
        RepositorySettings("internal", visibility = PRIVATE),
        RepositorySettings("snapshots", visibility = PUBLIC),
        RepositorySettings("shadow", visibility = HIDDEN),
        RepositorySettings("private-group", visibility = PRIVATE, proxied = mutableListOf(
            MirroredRepositorySettings(reference = "releases"),
        )),
        RepositorySettings("public-group", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = "releases"),
            MirroredRepositorySettings(reference = REMOTE_REPOSITORY, store = true, authorization = REMOTE_AUTH),
            MirroredRepositorySettings(reference = "internal"),
            MirroredRepositorySettings(reference = "snapshots"),
            MirroredRepositorySettings(reference = "shadow"),
        )),
        RepositorySettings("external-only", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = REMOTE_REPOSITORY, store = true, authorization = REMOTE_AUTH),
        )),
    )

    @Nested
    inner class Visibility {

        @Test
        fun `should list only the public references to an anonymous caller`() {
            // when: the members of the group are requested without a token
            val members = membersOf(UNAUTHORIZED, "public-group")

            // then: the private and hidden references are left out
            assertThat(members).containsExactly("releases", "snapshots")
        }

        @Test
        fun `should list a private reference to a token with a route to it`() {
            // given: a token with read access to the private repository
            val token = createAccessToken("internal-reader", "secret", "internal", "com/example", READ)

            // when: the members of the group are requested with that token
            val members = membersOf(token, "public-group")

            // then: the private reference is listed next to the public ones
            assertThat(members).containsExactly("releases", "internal", "snapshots")
        }

        @Test
        fun `should never list a hidden reference to a token with a route to it`() {
            // given: a token with read access to the hidden repository
            val token = createAccessToken("shadow-reader", "secret", "shadow", "com/example", READ)

            // when: the members of the group are requested with that token
            val members = membersOf(token, "public-group")

            // then: the hidden reference stays unlisted, as it does in the repository list
            assertThat(members).containsExactly("releases", "snapshots")
        }

        @Test
        fun `should list a hidden reference to a manager`() {
            // given: a manager token
            val manager = createManagerAccessToken("manager", "secret")

            // when: the members of the group are requested with that token
            val members = membersOf(manager, "public-group")

            // then: every loopback reference is listed in configuration order
            assertThat(members).containsExactly("releases", "internal", "snapshots", "shadow")
        }

        @Test
        fun `should refuse the members of a private group to an anonymous caller`() {
            // when: the members of the private group are requested without a token
            val response = mavenFacade.findMembers(UNAUTHORIZED, "private-group")

            // then: the request is refused, the group itself is not visible
            assertError(response)
        }
    }

    @Nested
    inner class References {

        @Test
        fun `should never list an external proxy`() {
            // when: the members of a group mixing loopback and external references are requested by a manager
            val members = membersOf(createManagerAccessToken("manager", "secret"), "public-group")

            // then: the external upstream is absent, neither as URL nor as any other value
            assertThat(members).noneMatch { it.startsWith("https://") }
            assertThat(members).doesNotContain(REMOTE_REPOSITORY)
        }

        @Test
        fun `should return an empty list for a group that only references external hosts`() {
            // when: the members of the group are requested
            val members = membersOf(UNAUTHORIZED, "external-only")

            // then: nothing is listed
            assertThat(members).isEmpty()
        }

        @Test
        fun `should return an empty list for a repository that is not a group`() {
            // when: the members of a plain hosted repository are requested
            val members = membersOf(UNAUTHORIZED, "releases")

            // then: nothing is listed
            assertThat(members).isEmpty()
        }

        @Test
        fun `should refuse the members of a repository that does not exist`() {
            // when: the members of an unknown repository are requested
            val response = mavenFacade.findMembers(UNAUTHORIZED, "no-such-repository")

            // then: the request is refused as not found
            assertError(response)
        }
    }

    private fun membersOf(accessToken: AccessTokenIdentifier?, repository: String): List<String> =
        assertOk(mavenFacade.findMembers(accessToken, repository)).members
}
