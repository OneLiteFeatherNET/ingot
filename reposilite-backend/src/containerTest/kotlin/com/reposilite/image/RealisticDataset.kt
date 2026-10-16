/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.image

import org.assertj.core.api.Assertions.assertThat

/**
 * An installation as an operator leaves it after some months of use. It is built through the product's own HTTP API,
 * so every token, route, repository and setting is one a real instance holds, and none of it is written by hand into a
 * database.
 *
 * The same data serves every phase of the rehearsal: the source product creates it, Ingot takes it over, and the source
 * takes it back. What is compared between phases is [accessMatrix], which records how each token answers each operation.
 */
internal object RealisticDataset {

    /**
     * Persistent tokens and their secrets. Each one differs in what it may do. The temporary bootstrap token that creates
     * them is not part of the dataset, because it does not survive a restart and so cannot test the switch.
     */
    val tokens: Map<String, String> = linkedMapOf(
        "ops-admin" to "ops-admin-secret-for-the-rehearsal",
        "ci-deployer" to "ci-deployer-secret-for-the-rehearsal",
        "reader" to "reader-secret-for-the-rehearsal",
        "writer" to "writer-secret-for-the-rehearsal",
        "pri-reader" to "pri-reader-secret-for-the-rehearsal",
        "mixed" to "mixed-secret-for-the-rehearsal",
        "plain" to "plain-secret-for-the-rehearsal",
    )

    /** One route of a token: the prefix and the permissions it carries. */
    private class Grant(val prefix: String, vararg val permissions: String) {
        fun json(): String =
            """{"path":"$prefix","permissions":[${permissions.joinToString(",") { "\"$it\"" }}]}"""
    }

    /**
     * Routes per token, on different prefixes. "/rel" covers "/releases" and "/pri" covers "/private", both without a
     * segment boundary. Getting that prefix case right is part of the rehearsal, not an accident of the data.
     */
    private val grants: Map<String, List<Grant>> = mapOf(
        "ci-deployer" to listOf(Grant("/snapshots", "r", "w")),
        "reader" to listOf(Grant("/private", "r")),
        "writer" to listOf(Grant("/rel", "w")),
        "pri-reader" to listOf(Grant("/pri", "r")),
        "mixed" to listOf(Grant("/hidden", "r", "w")),
    )

    /** Repositories and their visibility. PRIVATE and HIDDEN are both present, and HIDDEN is a settings change. */
    val repositories: List<Pair<String, String>> = listOf(
        "releases" to "PUBLIC",
        "snapshots" to "PUBLIC",
        "private" to "PRIVATE",
        "hidden" to "HIDDEN",
    )

    const val FRONTEND_TITLE = "Acme Mirror"
    const val ARTIFACT_RELEASE = "releases/com/example/demo/1.0.0/demo-1.0.0.jar"
    const val ARTIFACT_SNAPSHOT = "snapshots/com/example/demo/1.0.0-SNAPSHOT/demo-1.0.0-20261015.120000-1.jar"
    const val ARTIFACT_PRIVATE = "private/com/example/secret/1.0.0/secret-1.0.0.jar"
    const val ARTIFACT_HIDDEN = "hidden/com/example/hidden/1.0.0/hidden-1.0.0.jar"
    const val METADATA_PATH = "releases/com/example/demo/maven-metadata.xml"
    const val METADATA_CONTENT = "<metadata><groupId>com.example.demo</groupId><versioning><latest>1.0.0</latest></versioning></metadata>"

    private val artifacts: Map<String, String> = mapOf(
        ARTIFACT_RELEASE to "release artifact",
        ARTIFACT_SNAPSHOT to "snapshot artifact",
        ARTIFACT_PRIVATE to "private artifact",
        ARTIFACT_HIDDEN to "hidden artifact",
        METADATA_PATH to METADATA_CONTENT,
    )

    /** Paths read by every subject. Each one sits in a repository with a different visibility. */
    private val readPaths = listOf(ARTIFACT_RELEASE, ARTIFACT_SNAPSHOT, ARTIFACT_PRIVATE, ARTIFACT_HIDDEN)

    /** Repositories that every subject tries to write to and delete from. */
    private val writeRepositories = repositories.map { it.first }

    /** Creates the dataset on a running server. [bootstrap] is the temporary manager the source is started with. */
    fun create(url: String, bootstrap: Pair<String, String>) {
        tokens.forEach { (name, secret) ->
            val permissions = if (name == "ops-admin") """["m"]""" else "[]"
            val routes = grants[name].orEmpty().joinToString(",", "[", "]") { it.json() }
            val response = Http.putJson(
                url = "$url/api/tokens/$name",
                json = """{"type":"PERSISTENT","secretType":"RAW","secret":"$secret","permissions":$permissions,"routes":$routes}""",
                credentials = bootstrap,
            )
            assertThat(response.isSuccessful).describedAs("create token %s: %s", name, response.body).isTrue()
        }

        val settings = repositories.joinToString(",", "[", "]") { (id, visibility) -> """{"id":"$id","visibility":"$visibility"}""" }
        val maven = Http.putJson("$url/api/settings/domain/maven", """{"repositories":$settings}""", bootstrap)
        assertThat(maven.isSuccessful).describedAs("set repository visibility: %s", maven.body).isTrue()

        val frontend = Http.putJson("$url/api/settings/domain/frontend", """{"title":"$FRONTEND_TITLE"}""", bootstrap)
        assertThat(frontend.isSuccessful).describedAs("set the frontend title: %s", frontend.body).isTrue()

        val manager = "ops-admin" to tokens.getValue("ops-admin")
        artifacts.forEach { (path, content) ->
            val published = Http.put("$url/$path", content, manager)
            assertThat(published.isSuccessful).describedAs("deploy %s: %s", path, published.body).isTrue()
        }
    }

    /**
     * The status code of every (subject, operation, path) cell. Subjects are the persistent tokens, an anonymous client and
     * a token with a wrong secret. Writes and deletes use names that include [phase], so the probes of one phase never
     * collide with those of another, while the keys themselves stay comparable across phases.
     */
    fun accessMatrix(url: String, phase: String): Map<String, Int> {
        val subjects: List<Pair<String, Pair<String, String>?>> =
            tokens.map { (name, secret) -> name to (name to secret) } +
                listOf("anonymous" to null, "reader-wrong-secret" to ("reader" to "not-the-secret"))

        val cells = linkedMapOf<String, Int>()
        for ((subject, credentials) in subjects) {
            readPaths.forEach { path ->
                cells["$subject GET $path"] = Http.get("$url/$path", credentials).status
            }
            writeRepositories.forEach { repository ->
                cells["$subject PUT $repository"] = Http.put(
                    url = "$url/$repository/com/example/probe/$subject/write-$phase.txt",
                    body = "probe $phase",
                    credentials = credentials,
                ).status
                cells["$subject DELETE $repository"] = Http.delete(
                    url = "$url/$repository/com/example/probe/$subject/absent-$phase.txt",
                    credentials = credentials,
                ).status
            }
        }
        return cells
    }

    /** The `repository=visibility` pairs of the maven settings domain, in the order the server lists them. */
    fun visibilities(url: String, credentials: Pair<String, String>): List<String> {
        val body = Http.get("$url/api/settings/domain/maven", credentials).body
        return Regex("\"id\":\"([^\"]+)\",\"visibility\":\"([^\"]+)\"")
            .findAll(body)
            .map { "${it.groupValues[1]}=${it.groupValues[2]}" }
            .toList()
    }

    fun frontendTitle(url: String, credentials: Pair<String, String>): String? {
        val body = Http.get("$url/api/settings/domain/frontend", credentials).body
        return Regex("\"title\":\"([^\"]*)\"").find(body)?.groupValues?.get(1)
    }

    /** Every persistent token became a machine principal exactly once, and the manager is an instance admin. */
    fun assertMachinePrincipals(url: String, manager: Pair<String, String>) {
        val principals = Http.get("$url/api/identity/principals", manager)
        assertThat(principals.isSuccessful).describedAs("list principals: %s", principals.body).isTrue()

        val machines = Regex("\"kind\":\"MACHINE\",\"name\":\"([^\"]+)\"").findAll(principals.body).map { it.groupValues[1] }.toList()
        assertThat(machines)
            .describedAs("one machine principal per persistent token")
            .containsExactlyInAnyOrderElementsOf(tokens.keys)

        val admin = Http.get("$url/api/identity/principals/ops-admin", manager).body
        assertThat(admin).describedAs("the manager stays an instance admin").contains("\"instanceRole\":true")
    }

    /** The tokens endpoint still answers for the manager, and lists every token of the dataset. */
    fun assertTokenApi(url: String, manager: Pair<String, String>) {
        val response = Http.get("$url/api/tokens", manager)
        assertThat(response.status).describedAs("/api/tokens for the manager").isEqualTo(200)
        tokens.keys.forEach { name ->
            assertThat(response.body).describedAs("token %s is listed", name).contains("\"name\":\"$name\"")
        }
    }

    /** Domains that only Ingot has exist and answer with their defaults. */
    fun assertIdentityDomains(url: String, manager: Pair<String, String>) {
        val domains = Http.get("$url/api/settings/domains", manager).body
        listOf("audit", "cleanup", "login").forEach { domain ->
            assertThat(domains).describedAs("domain %s is registered", domain).contains("\"$domain\"")
            val response = Http.get("$url/api/settings/domain/$domain", manager)
            assertThat(response.status).describedAs("domain %s answers with its defaults", domain).isEqualTo(200)
        }
    }

    /** Artifacts and the metadata file are readable anonymously, byte for byte. */
    fun assertPublicArtifacts(url: String) {
        val metadata = Http.get("$url/$METADATA_PATH")
        assertThat(metadata.status).describedAs("metadata status").isEqualTo(200)
        assertThat(metadata.body).describedAs("metadata content").isEqualTo(METADATA_CONTENT)
        assertThat(Http.get("$url/$ARTIFACT_RELEASE").body).describedAs("release artifact").isEqualTo(artifacts.getValue(ARTIFACT_RELEASE))
    }
}
