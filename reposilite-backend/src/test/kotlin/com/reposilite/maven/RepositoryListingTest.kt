package com.reposilite.maven

import com.reposilite.maven.RepositoryVisibility.HIDDEN
import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.api.RepositoryListQuery
import com.reposilite.maven.api.RepositorySummary
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.RoutePermission.READ
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RepositoryListingTest : MavenSpecification() {

    // Deliberately not sorted, so that the tests also prove the listing is sorted by id.
    override fun repositories() = listOf(
        RepositorySettings("open", visibility = PUBLIC, description = "Open source libraries", tags = listOf("team-a", "stable")),
        RepositorySettings("internal", visibility = PRIVATE, description = "Internal builds", tags = listOf("team-b")),
        RepositorySettings("shadow", visibility = HIDDEN, description = "Staging area", tags = listOf("staging")),
        RepositorySettings("bare", visibility = PUBLIC),
    )

    @Test
    fun `should list only public repositories to an anonymous caller`() {
        // when: the list is requested without a token
        val ids = listedIds(UNAUTHORIZED)

        // then: only the public repositories are listed
        assertThat(ids).containsExactly("bare", "open")
    }

    @Test
    fun `should list a private repository to a token with a route to it`() {
        // given: a token with read access to the private repository
        val token = createAccessToken("internal-reader", "secret", "internal", "com/example", READ)

        // when: the list is requested with that token
        val ids = listedIds(token)

        // then: the private repository is listed next to the public ones
        assertThat(ids).containsExactly("bare", "internal", "open")
    }

    @Test
    fun `should not list a private repository to a token whose route points elsewhere`() {
        // given: a token with read access to a different public repository
        val token = createAccessToken("open-reader", "secret", "open", "com/example", READ)

        // when: the list is requested with that token
        val ids = listedIds(token)

        // then: the private repository stays unlisted
        assertThat(ids).containsExactly("bare", "open")
    }

    @Test
    fun `should never list a hidden repository to a token with a route to it`() {
        // given: a token with read access to the hidden repository
        val token = createAccessToken("shadow-reader", "secret", "shadow", "com/example", READ)

        // when: the list is requested with that token
        val ids = listedIds(token)

        // then: the hidden repository is not listed, although the token could browse it
        assertThat(ids).containsExactly("bare", "open")
    }

    @Test
    fun `should list hidden repositories to a manager`() {
        // given: a manager token
        val manager = createManagerAccessToken("manager", "secret")

        // when: the list is requested with that token
        val ids = listedIds(manager)

        // then: every repository is listed, the hidden one included
        assertThat(ids).containsExactly("bare", "internal", "open", "shadow")
    }

    @Test
    fun `should describe each listed repository with its id, visibility, description and tags`() {
        // given: a manager token, so that every repository is listed
        val manager = createManagerAccessToken("manager", "secret")

        // when: the list is requested
        val repositories = mavenFacade.listRepositories(manager, RepositoryListQuery()).repositories

        // then: each entry carries the documented fields
        assertThat(repositories).contains(RepositorySummary("open", PUBLIC, "Open source libraries", listOf("team-a", "stable"), type = "hosted", typeExplicit = false))
        assertThat(repositories).contains(RepositorySummary("bare", PUBLIC, "", emptyList(), type = "hosted", typeExplicit = false))
    }

    @Test
    fun `should sort the repositories by id regardless of their configuration order`() {
        // given: a manager token, so that all repositories are listed
        val manager = createManagerAccessToken("manager", "secret")

        // when: the list is requested without any query
        val ids = listedIds(manager)

        // then: the ids are in ascending order
        assertThat(ids).isSorted()
    }

    @Test
    fun `should match the query against the id case-insensitively`() {
        // when: the query is requested in upper case
        val ids = listedIds(UNAUTHORIZED, query = "OPEN")

        // then: the repository is matched by its id
        assertThat(ids).containsExactly("open")
    }

    @Test
    fun `should match the query against the description case-insensitively`() {
        // when: a part of the description is requested in mixed case
        val ids = listedIds(UNAUTHORIZED, query = "Source LIB")

        // then: the repository is matched by its description
        assertThat(ids).containsExactly("open")
    }

    @Test
    fun `should match the query against the tags case-insensitively`() {
        // when: a tag is requested in upper case
        val ids = listedIds(UNAUTHORIZED, query = "TEAM-A")

        // then: the repository is matched by its tag
        assertThat(ids).containsExactly("open")
    }

    @Test
    fun `should return an empty list when the query matches nothing`() {
        // when: a query without any match is requested
        val ids = listedIds(UNAUTHORIZED, query = "no-such-thing")

        // then: the list is empty
        assertThat(ids).isEmpty()
    }

    @Test
    fun `should not reveal a private repository through the query to an anonymous caller`() {
        // when: the query names a private repository
        val ids = listedIds(UNAUTHORIZED, query = "internal")

        // then: nothing is listed, so the query cannot be used to probe private repositories
        assertThat(ids).isEmpty()
    }

    @Test
    fun `should not reveal a hidden repository through the query to a token with a route to it`() {
        // given: a token with read access to the hidden repository
        val token = createAccessToken("shadow-reader", "secret", "shadow", "com/example", READ)

        // when: the query names the hidden repository
        val ids = listedIds(token, query = "staging")

        // then: the hidden repository is not listed
        assertThat(ids).isEmpty()
    }

    @Test
    fun `should match a hidden repository through the query to a manager`() {
        // given: a manager token
        val manager = createManagerAccessToken("manager", "secret")

        // when: the query names the hidden repository
        val ids = listedIds(manager, query = "staging")

        // then: the hidden repository is matched
        assertThat(ids).containsExactly("shadow")
    }

    @Test
    fun `should keep a repository that carries every requested tag`() {
        // when: two tags the public repository carries are requested
        val ids = listedIds(UNAUTHORIZED, tags = listOf("team-a", "stable"))

        // then: the repository is listed
        assertThat(ids).containsExactly("open")
    }

    @Test
    fun `should drop a repository that lacks one of the requested tags`() {
        // when: one requested tag is carried by the repository, the other one is not
        val ids = listedIds(UNAUTHORIZED, tags = listOf("team-a", "staging"))

        // then: the repository is dropped, because every tag has to match
        assertThat(ids).isEmpty()
    }

    @Test
    fun `should match tags exactly rather than by prefix`() {
        // when: a prefix of a tag the repository carries is requested
        val ids = listedIds(UNAUTHORIZED, tags = listOf("team"))

        // then: no repository is listed
        assertThat(ids).isEmpty()
    }

    @Test
    fun `should match a tag regardless of the case it is requested in`() {
        // when: a tag is requested in upper case
        val ids = listedIds(UNAUTHORIZED, tags = listOf("TEAM-A"))

        // then: the repository carrying the lowercase tag is listed
        assertThat(ids).containsExactly("open")
    }

    @Test
    fun `should ignore a blank query and blank tags`() {
        // when: a whitespace query and an empty tag are requested
        val ids = listedIds(UNAUTHORIZED, query = "   ", tags = listOf(""))

        // then: they filter nothing
        assertThat(ids).containsExactly("bare", "open")
    }

    @Test
    fun `should apply the query and the tags together`() {
        // when: a query and a tag that only the same repository satisfies are requested
        val matching = listedIds(UNAUTHORIZED, query = "open", tags = listOf("stable"))
        // and: a query that matches a different repository than the tag does
        val conflicting = listedIds(UNAUTHORIZED, query = "bare", tags = listOf("stable"))

        // then: both conditions have to hold
        assertThat(matching).containsExactly("open")
        assertThat(conflicting).isEmpty()
    }

    private fun listedIds(
        accessToken: AccessTokenIdentifier?,
        query: String? = null,
        tags: List<String> = emptyList(),
    ): List<String> =
        mavenFacade.listRepositories(accessToken, RepositoryListQuery(query = query, tags = tags))
            .repositories
            .map { it.id }

}
