package com.reposilite.maven.api

/**
 * The repositories a group references through loopback mirrors, in the order they are configured.
 *
 * Only ids are listed: external proxy URLs, credentials and the other mirror settings never leave the server.
 * A repository that is not a group, or whose references the caller may not see, has an empty list.
 */
data class RepositoryMembersResponse(
    val members: List<String>
)
