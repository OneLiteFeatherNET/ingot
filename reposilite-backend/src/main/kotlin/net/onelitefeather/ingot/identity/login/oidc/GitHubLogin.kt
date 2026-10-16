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

package net.onelitefeather.ingot.identity.login.oidc

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.fasterxml.jackson.databind.JsonNode
import java.io.IOException
import java.net.URI

/** The endpoints of GitHub's OAuth 2 service. Production uses [PUBLIC]; tests point them at a local stub. */
data class GitHubEndpoints(
    val authorize: String,
    val token: String,
    val api: String,
) {
    companion object {
        val PUBLIC = GitHubEndpoints(
            authorize = "https://github.com/login/oauth/authorize",
            token = "https://github.com/login/oauth/access_token",
            api = "https://api.github.com",
        )
    }
}

/** What GitHub says about the user who authorized the login. */
data class GitHubProfile(
    /** The numeric user id. It does not change when the login is renamed, so it is the subject. */
    val subject: String,
    val login: String,
    val name: String?,
    val email: String?,
    /** `<org>` for each organisation and `<org>/<team-slug>` for each team of the user. */
    val groups: List<String>,
)

/**
 * GitHub is OAuth 2 and issues no ID token, so the identity comes from its API: the user document, the verified primary
 * email when the user document has none, and the organisations and teams. GitHub answers a wrong code with `200` and an
 * `error` field, which counts as a refused code.
 */
internal class GitHubLogin(
    private val http: HttpFetcher,
    private val endpoints: GitHubEndpoints,
) {

    /** The access token of the code exchange. Throws [TokenRefusedException] when GitHub refuses the code. */
    fun exchange(provider: OidcProviderSettings, code: String, verifier: String, redirectUri: String): String {
        val form = linkedMapOf(
            "client_id" to provider.clientId,
            "client_secret" to provider.clientSecret,
            "code" to code,
            "redirect_uri" to redirectUri,
            "code_verifier" to verifier,
        )
        val reply = try {
            http.postForm(URI.create(endpoints.token), form, mapOf("Accept" to "application/json"))
        } catch (exception: IOException) {
            throw OidcUnavailableException("GitHub token endpoint not reachable", exception)
        }
        if (reply.status != 200) {
            throw TokenRefusedException()
        }
        return try {
            DEFAULT_OBJECT_MAPPER.readTree(reply.body).get("access_token")?.asText()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } ?: throw TokenRefusedException()
    }

    /** The profile of the user that [accessToken] belongs to. Throws [OidcUnavailableException] when the API cannot answer. */
    fun profile(accessToken: String): GitHubProfile {
        val user = getJson("${endpoints.api}/user", accessToken)
        val subject = user.get("id")?.asText()?.takeIf { it.isNotBlank() } ?: throw OidcUnavailableException("GitHub user has no id")
        val login = user.text("login") ?: throw OidcUnavailableException("GitHub user has no login")
        val email = user.text("email") ?: primaryEmail(accessToken)
        val teams = allTeams(accessToken)
        val groups = teams.flatMap { team ->
            val org = team.path("organization").path("login").asText()
            val slug = team.path("slug").asText()
            if (org.isBlank() || slug.isBlank()) emptyList() else listOf(org, "$org/$slug")
        }.distinct()
        return GitHubProfile(
            subject = subject,
            login = login,
            name = user.text("name"),
            email = email,
            groups = groups,
        )
    }

    /**
     * Every team of the user, page by page. A short page ends the listing. The listing is never cut short: a user with more
     * teams than [MAX_PAGES] pages fails the login, because a partial list would drop the synchronised memberships of the rest.
     */
    private fun allTeams(accessToken: String): List<JsonNode> {
        val teams = mutableListOf<JsonNode>()
        for (page in 1..MAX_PAGES) {
            val batch = getJson("${endpoints.api}/user/teams?per_page=$PAGE_SIZE&page=$page", accessToken).toList()
            teams.addAll(batch)
            if (batch.size < PAGE_SIZE) return teams
        }
        throw OidcUnavailableException("GitHub lists more teams than the login reads")
    }

    private fun primaryEmail(accessToken: String): String? =
        getJson("${endpoints.api}/user/emails", accessToken)
            .firstOrNull { it.path("primary").asBoolean(false) && it.path("verified").asBoolean(false) }
            ?.text("email")

    private fun getJson(url: String, accessToken: String): JsonNode {
        val reply = try {
            http.get(URI.create(url), mapOf("Authorization" to "Bearer $accessToken", "Accept" to "application/vnd.github+json"))
        } catch (exception: IOException) {
            throw OidcUnavailableException("GitHub API not reachable", exception)
        }
        if (reply.status != 200) {
            throw OidcUnavailableException("GitHub API answered ${reply.status}")
        }
        return try {
            DEFAULT_OBJECT_MAPPER.readTree(reply.body)
        } catch (exception: Exception) {
            throw OidcUnavailableException("GitHub API answered with something that is not JSON", exception)
        }
    }

}

/** A text field of a JSON object, or `null` when it is missing, JSON null or blank. `asText()` would turn null into "null". */
private fun JsonNode.text(field: String): String? =
    get(field)?.takeIf { it.isTextual }?.textValue()?.takeIf { it.isNotBlank() }

private const val PAGE_SIZE = 100
private const val MAX_PAGES = 50

/** Thrown when a provider refuses the authorization code. The login fails with `token_exchange_failed`. */
internal class TokenRefusedException : RuntimeException("the token endpoint refused the code")
