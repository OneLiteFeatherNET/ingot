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
package net.onelitefeather.ingot.identity.storage

import com.reposilite.journalist.Journalist
import com.reposilite.token.AccessToken
import com.reposilite.token.AccessTokenPermission
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.Route
import com.reposilite.token.RoutePermission
import com.reposilite.token.infrastructure.SqlAccessTokenRepository
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.LocalDate

/**
 * The legacy state of the production instance, without its secrets: five persistent tokens with the routes they carry.
 * Every token is written through the legacy repository, so the rows look exactly as a 1.1.0 installation wrote them.
 *
 * The expectations of the access matrix are not written out by hand. They come from [v110Decisions], which applies the
 * route rule of v1.1.0 (`Route.hasPermissionTo` and `AccessTokenFacade.canSee`) to the route table below.
 */
internal object ProductionShapeDataset {

    const val MIRROR_NAME = "reposilite-mirror"
    const val READER_NAME = "reposilite-reader"
    const val DART_POET_DIRECTORY = "net/theevilreaper/dartpoet/"

    /** Typo in the production route: the repository `onelitefeather-snapshopts` does not exist. Kept on purpose by the test. */
    const val TYPO_ROUTE = "/onelitefeather-snapshopts/net/theevilreaper/dartpoet"

    private val CREATED_AT: LocalDate = LocalDate.of(2026, 1, 15)

    /** Every repository of the instance. Each one is listed in the order of the production settings. */
    val REPOSITORIES: List<String> = listOf(
        "releases",
        "snapshots",
        "public",
        "onelitefeather-releases",
        "onelitefeather-snapshots",
        "onelitefeather-proxy",
        "onelitefeather",
    )

    /** Invented secrets, one per token. Only the bcrypt hashes reach the legacy tables. */
    val secrets: Map<String, String> = linkedMapOf(
        "admin" to "admin-secret-for-the-production-shape",
        "reposilite-publisher" to "publisher-secret-for-the-production-shape",
        READER_NAME to "reader-secret-for-the-production-shape",
        MIRROR_NAME to "mirror-secret-for-the-production-shape",
        "dart-poet" to "dart-poet-secret-for-the-production-shape",
    )

    private val managers: Set<String> = setOf("admin")

    private val descriptions: Map<String, String> = mapOf(
        "admin" to "Operator console",
        "reposilite-publisher" to "Publishes releases and snapshots from CI",
        READER_NAME to "Read-only access for the mirror clients",
        MIRROR_NAME to "Loopback credential of the onelitefeather group",
        "dart-poet" to "Publishes the dartpoet artifacts",
    )

    private fun read(path: String): Pair<String, RoutePermission> = path to RoutePermission.READ

    private fun write(path: String): Pair<String, RoutePermission> = path to RoutePermission.WRITE

    private fun readWrite(path: String): List<Pair<String, RoutePermission>> = listOf(read(path), write(path))

    /** Legacy routes per token, as (path, permission). A token without an entry carries no route. */
    val legacyRoutes: Map<String, List<Pair<String, RoutePermission>>> = mapOf(
        "admin" to emptyList(),
        "reposilite-publisher" to
            listOf("/releases", "/snapshots", "/onelitefeather-releases", "/onelitefeather-snapshots").flatMap(::readWrite) +
            listOf("/public", "/onelitefeather-proxy", "/onelitefeather").map(::read),
        READER_NAME to REPOSITORIES.map { read("/$it") },
        MIRROR_NAME to listOf("/onelitefeather-proxy", "/onelitefeather", "/onelitefeather-releases", "/onelitefeather-snapshots").map(::read),
        "dart-poet" to
            listOf("/releases", "/snapshots", "/onelitefeather-releases", "/onelitefeather-snapshots")
                .flatMap { readWrite("$it/$DART_POET_DIRECTORY") } +
            readWrite(TYPO_ROUTE),
    )

    /** Writes the legacy tables the way the 1.1.0 repository did. Secrets are hashed at the minimum work factor. */
    fun seedLegacyTokens(database: Database, journalist: Journalist) {
        val legacy = SqlAccessTokenRepository(database, journalist, emptyArray())
        secrets.forEach { (name, secret) ->
            val saved = legacy.saveAccessToken(
                AccessToken(
                    name = name,
                    encryptedSecret = AccessTokenSecurityProvider.encodeSecret(secret, AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH),
                    createdAt = CREATED_AT,
                    description = descriptions[name].orEmpty(),
                )
            )
            if (name in managers) {
                legacy.addPermission(saved.identifier, AccessTokenPermission.MANAGER)
            }
            legacyRoutes[name].orEmpty().forEach { (path, permission) ->
                legacy.addRoute(saved.identifier, Route(path, permission))
            }
        }
    }

    /** The kinds of question the access matrix asks. */
    enum class ProbeKind { READ, WRITE, SEE }

    /** One question: may the token read or write [target], or does it see the repository named by [target]. */
    data class Probe(val kind: ProbeKind, val target: String) {
        val key: String get() = "${kind.name.lowercase()} $target"
    }

    private const val ARTIFACT = "com/example/a/1.0/a-1.0.jar"
    private const val DART_POET_ARTIFACT = "${DART_POET_DIRECTORY}x/1.0/x-1.0.jar"

    /** Every probe of the matrix: each repository as a whole, one artifact inside it, the dartpoet artifact, and the typo path. */
    val probes: List<Probe> =
        REPOSITORIES.flatMap { repository ->
            listOf(
                Probe(ProbeKind.READ, "/$repository"),
                Probe(ProbeKind.WRITE, "/$repository"),
                Probe(ProbeKind.SEE, "/$repository"),
                Probe(ProbeKind.READ, "/$repository/$ARTIFACT"),
                Probe(ProbeKind.WRITE, "/$repository/$ARTIFACT"),
                Probe(ProbeKind.READ, "/$repository/$DART_POET_ARTIFACT"),
                Probe(ProbeKind.WRITE, "/$repository/$DART_POET_ARTIFACT"),
            )
        } + listOf(
            Probe(ProbeKind.READ, "$TYPO_ROUTE/x/1.0/x-1.0.jar"),
            Probe(ProbeKind.WRITE, "$TYPO_ROUTE/x/1.0/x-1.0.jar"),
        )

    /**
     * The decisions of v1.1.0 for [token], keyed by [Probe.key]. Read and write follow `Route.hasPermissionTo`: the route
     * permission must match and the path must start with the route path, ignoring case. Visibility follows
     * `AccessTokenFacade.canSee`: a route path either starts with the fragment or the fragment starts with it.
     * The manager permission answers yes to everything.
     */
    fun v110Decisions(token: String): Map<String, Boolean> {
        val routes = legacyRoutes.getValue(token)
        val manager = token in managers
        return probes.associate { probe ->
            val allowed = when (probe.kind) {
                ProbeKind.READ -> manager || routes.any { (path, permission) ->
                    permission == RoutePermission.READ && probe.target.startsWith(path, ignoreCase = true)
                }
                ProbeKind.WRITE -> manager || routes.any { (path, permission) ->
                    permission == RoutePermission.WRITE && probe.target.startsWith(path, ignoreCase = true)
                }
                ProbeKind.SEE -> manager || routes.any { (path, _) ->
                    path.startsWith(probe.target, ignoreCase = true) || probe.target.startsWith(path)
                }
            }
            probe.key to allowed
        }
    }

    /**
     * The shared configuration document of the production instance, as an operator writes it into the shared configuration.
     * The group `onelitefeather` lists its loopback mirrors and one empty entry. The proxy has an external URL on a host that
     * does not resolve, and no test sends it a request that the group does not answer first.
     */
    val sharedDocument: String by lazy {
        val loopback = """{ "method": "LOOPBACK_LINK", "login": "$MIRROR_NAME", "password": "${secrets.getValue(MIRROR_NAME)}" }"""
        """
        {
          "maven": {
            "repositories": [
              { "id": "releases", "visibility": "PUBLIC" },
              { "id": "snapshots", "visibility": "PUBLIC" },
              { "id": "public", "visibility": "PUBLIC" },
              { "id": "onelitefeather-releases", "visibility": "PRIVATE" },
              { "id": "onelitefeather-snapshots", "visibility": "PRIVATE" },
              {
                "id": "onelitefeather-proxy",
                "visibility": "PRIVATE",
                "proxied": [ { "reference": "https://fake-upstream.invalid/maven" } ]
              },
              {
                "id": "onelitefeather",
                "visibility": "PRIVATE",
                "proxied": [
                  { "reference": "onelitefeather-releases", "authorization": $loopback },
                  { "reference": "" },
                  { "reference": "onelitefeather-snapshots", "authorization": $loopback },
                  { "reference": "onelitefeather-proxy", "authorization": $loopback }
                ]
              }
            ]
          }
        }
        """.trimIndent()
    }

}
