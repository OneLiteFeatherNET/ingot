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

package com.reposilite.maven.application

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Min
import com.reposilite.configuration.shared.api.Sensitive
import com.reposilite.configuration.shared.api.SettingsKey
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.configuration.shared.api.ValidatedSettings
import com.reposilite.maven.EffectiveRepositoryType
import com.reposilite.maven.RepositoryType
import com.reposilite.maven.StoragePolicy
import com.reposilite.maven.RepositoryVisibility
import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.effectiveRepositoryType
import com.reposilite.shared.http.AuthenticationMethod
import com.reposilite.shared.http.RemoteCredentials
import com.reposilite.storage.StorageProviderSettings
import com.reposilite.storage.filesystem.FileSystemStorageProviderSettings
import com.reposilite.storage.s3.S3StorageProviderSettings
import io.javalin.openapi.JsonSchema
import io.javalin.openapi.OneOf

@JsonSchema(requireNonNulls = false)
@Doc(title = "Maven", description = "Repositories settings")
data class MavenSettings(
    @get:Doc(title = "Repositories", description = "List of Maven repositories.")
    val repositories: List<RepositorySettings> = listOf(
        RepositorySettings("releases"),
        RepositorySettings("snapshots"),
        RepositorySettings("private", visibility = PRIVATE),
    )
) : ValidatedSettings {

    override fun violations(): List<String> =
        repositories.flatMap { it.violations() } + typeViolations()

    /** Typed repositories drop their blank proxied references, see [RepositorySettings.normalized]. */
    override fun normalized(): MavenSettings =
        copy(repositories = repositories.map { it.normalized() })

    /**
     * The rules of declared repository types, checked where the ids of all repositories are known. A typed group is
     * also checked for cycles, which can run through untyped repositories; a cycle made of untyped repositories alone
     * keeps working as it always did and is not reported.
     */
    private fun typeViolations(): List<String> {
        val localNames = repositories.map { it.id }.toSet()
        val perRepository = repositories.flatMap { repository -> repository.typeViolations(localNames) }
        return perRepository + cycleViolations(localNames)
    }

    /**
     * One message per typed group that a loop of local references leads back to. The loop is named in the message, and
     * the search follows every repository, typed or not, because a loop through either one is a loop. Self references
     * are left to the rule of the group itself.
     */
    private fun cycleViolations(localNames: Set<String>): List<String> {
        val edges: Map<String, List<String>> = repositories.associate { repository ->
            repository.id to repository.proxied.map { it.referencedHost() }.filter { it in localNames && it != repository.id }
        }
        return repositories
            .filter { it.type == RepositoryType.GROUP }
            .mapNotNull { group ->
                pathBackTo(group.id, edges)?.let { path ->
                    "repository '${group.id}': type GROUP is part of a cycle of proxied repositories (${path.joinToString(" -> ")})"
                }
            }
    }

    /** The shortest loop from [start] back to [start] along [edges], starting and ending with [start], or null. */
    private fun pathBackTo(start: String, edges: Map<String, List<String>>): List<String>? {
        val parent = HashMap<String, String?>()
        parent[start] = null
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for (next in edges[node].orEmpty()) {
                if (next == start) {
                    val chain = ArrayDeque<String>()
                    var cursor: String? = node
                    while (cursor != null) {
                        chain.addFirst(cursor)
                        cursor = parent[cursor]
                    }
                    return chain + start
                }
                if (next !in parent) {
                    parent[next] = node
                    queue.add(next)
                }
            }
        }
        return null
    }

}

@Doc(title = "Maven Repository", description = "Settings for a given repository.")
data class RepositorySettings(
    @Min(1)
    @get:SettingsKey
    @get:Doc(title = "Id", description = "The id of this repository.")
    val id: String = "",
    @get:Doc(title = "Visibility", description = "The visibility of this repository.")
    val visibility: RepositoryVisibility = PUBLIC,
    @get:Doc(title = "Type", description = """
        The declared type of this repository. Leave it empty to keep the behaviour of earlier versions, where a repository
        serves uploads and proxies whatever is listed under mirrored repositories.<br/>
        HOSTED - stores uploaded artifacts and proxies nothing.<br/>
        PROXY - proxies external URLs only and caches what it fetches; it takes no uploads.<br/>
        GROUP - proxies local repositories only and stores nothing; it takes no uploads.
    """)
    val type: RepositoryType? = null,
    @get:Doc(title = "Description", description = "Short plain-text description shown in the repository list.")
    val description: String = "",
    @get:Doc(title = "Tags", description = "Labels to group and find repositories, e.g. team or purpose.")
    val tags: List<String> = emptyList(),
    @get:Doc(title = "Redeployment", description = "Does this repository accept redeployment of the same artifact version.")
    val redeployment: Boolean = false,
    @get:Doc(title = "Preserved snapshots", "By default, Ingot deletes all deprecated build files. If you'd like to preserve them, set this property to true.")
    val preserveSnapshots: Boolean = false,
    @get:Doc(title = "Storage provider", description = "The storage type of this repository.")
    @get:OneOf(FileSystemStorageProviderSettings::class, S3StorageProviderSettings::class)
    val storageProvider: StorageProviderSettings = FileSystemStorageProviderSettings(),
    @get:Doc(title = "Storage policy", description = """
        Defines how Ingot should handle requests to repository with configured mirrored repositories. <br/>
        PRIORITIZE_UPSTREAM_METADATA - try to fetch the latest version of the artifact from the remote repository <br/>
        STRICT - prioritize cached version over upstream metadata (full offline mode)
    """)
    val storagePolicy: StoragePolicy = StoragePolicy.PRIORITIZE_UPSTREAM_METADATA,
    @get:Doc(title = "Max age of metadata file", description = """
        Fetching metadata files with PRIORITIZE_UPSTREAM_METADATA may be slow, so you can use dedicated TTL threshold for them.<br/>
        If set to "0" then a fetch is done always. (Default: 0 seconds)
    """)
    val metadataMaxAge: Long = 0L,
    @Min(0)
    @get:Doc(title = "Resolution cache size", description = """
        Caches routing decisions for metadata reads (maven-metadata.xml, *.pom, *.module, Ivy XML)
        to speed up repeated requests. Size scales with the number of distinct GAVs touched
        through your mirrors, not with user traffic:<br/>
        0 — disabled (no mirrors, or caching not needed)<br/>
        128 — proxying a small fixed set of artifacts (single vendor SDK, internal repos)<br/>
        512 — proxying focused ecosystems (BOM + transitives, a few curated mirrors)<br/>
        2048+ — proxying broad ecosystems like Maven Central<br/>
        (Default: 0)
    """)
    val resolutionCacheMaxEntries: Int = 0,
    @get:Doc(title = "Mirrored repositories", description = "List of mirrored repositories associated with this repository.")
    val proxied: List<MirroredRepositorySettings> = listOf()
) : SharedSettings {

    /**
     * Describes every rule of [description] and [tags] that this repository breaks, each message
     * naming the repository. An empty list accepts the repository.
     */
    fun violations(): List<String> {
        val violations = mutableListOf<String>()

        if (description.length > DESCRIPTION_MAX_LENGTH) {
            violations += "repository '$id': description must be at most $DESCRIPTION_MAX_LENGTH characters, but has ${description.length}"
        }

        if (tags.size > TAGS_MAX_COUNT) {
            violations += "repository '$id': at most $TAGS_MAX_COUNT tags are allowed, but ${tags.size} were given"
        }

        tags.filterNot { TAG_FORMAT.matches(it) }.forEach { tag ->
            violations += "repository '$id': tag '$tag' is not valid, tags use 1 to 32 lowercase letters, digits or hyphens and start with a letter or digit"
        }

        tags.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { tag ->
            violations += "repository '$id': tag '$tag' is listed more than once"
        }

        return violations
    }

    /**
     * The effective type of this repository, see [effectiveRepositoryType]. [localNames] are the ids of the
     * repositories of this instance: a proxied reference to one of them is a loopback reference.
     */
    fun effectiveType(localNames: Collection<String>): EffectiveRepositoryType {
        val hosts = proxied.map { it.referencedHost() }.filter { it.isNotEmpty() }
        val loopback = hosts.count { it in localNames }
        return effectiveRepositoryType(type, loopbackReferences = loopback, externalReferences = hosts.size - loopback)
    }

    /**
     * The settings as they are stored. A typed repository drops the blank entries of its proxied list, because a blank
     * entry names no repository and the server already ignores it. An untyped repository is returned as it is, so its
     * data is never rewritten.
     */
    fun normalized(): RepositorySettings =
        if (type == null) this else copy(proxied = proxied.filter { it.referencedHost().isNotEmpty() })

    /** The rules of the declared [type] that concern this repository alone. Empty when the repository is untyped. */
    internal fun typeViolations(localNames: Set<String>): List<String> {
        val declared = type ?: return emptyList()
        val entries = proxied.map { it to it.referencedHost() }
        return when (declared) {
            RepositoryType.HOSTED ->
                if (proxied.isEmpty()) emptyList()
                else listOf("repository '$id': type HOSTED takes no proxied repositories, but ${proxied.size} are listed")

            RepositoryType.PROXY -> buildList {
                if (proxied.isEmpty()) {
                    add("repository '$id': type PROXY needs at least one proxied external URL")
                }
                entries.filter { (_, host) -> host.isEmpty() }.forEach { _ ->
                    add("repository '$id': type PROXY has an empty proxied reference")
                }
                entries.filter { (_, host) -> host.isNotEmpty() && host in localNames }.forEach { (_, host) ->
                    add("repository '$id': type PROXY proxies external URLs only, but '$host' is a local repository (use type GROUP to proxy local repositories)")
                }
            }

            RepositoryType.GROUP -> buildList {
                if (proxied.isEmpty()) {
                    add("repository '$id': type GROUP needs at least one proxied local repository")
                }
                entries.forEach { (mirror, host) ->
                    when {
                        host.isEmpty() -> add("repository '$id': type GROUP has an empty proxied reference")
                        host == id -> add("repository '$id': type GROUP cannot proxy itself")
                        host !in localNames -> add("repository '$id': type GROUP proxies local repositories only, but '$host' is not a repository of this instance")
                        mirror.store -> add("repository '$id': type GROUP stores nothing, so proxied '$host' must not set store")
                    }
                }
            }
        }
    }

    companion object {
        const val DESCRIPTION_MAX_LENGTH = 280
        const val TAGS_MAX_COUNT = 10
        private val TAG_FORMAT = Regex("[a-z0-9][a-z0-9-]{0,31}")
    }

}

@Doc(title = "Mirrored Maven Repository", description = "Configuration of proxied host")
data class MirroredRepositorySettings(
    @Min(1)
    @get:SettingsKey
    @get:Doc(title = "Link", description = "Either the id of other local repository or the URL of a remote repository.")
    val reference: String = "",
    @get:Doc(title = "Store", description = "Ingot can store proxied artifacts locally to reduce response time and improve stability.")
    val store: Boolean = false,
    @get:Doc(title = "Allowed Groups", description = "Allowed artifact groups. If none are given, all artifacts can be obtained from this mirror.")
    val allowedGroups: List<String> = listOf(),
    @get:Doc(title = "Allowed Extensions", description = "List of accepted file extensions. If none are given, all files can be obtained from this mirror. Use the special value '&lt;none&gt;' to permit files without an extension (e.g. native binaries).")
    val allowedExtensions: List<String> = listOf(".jar", ".war", ".aar", ".pom", ".xml", ".module", ".md5", ".sha1", ".sha256", ".sha512", ".asc"),
    @Min(0)
    @get:Doc(title = "Connect Timeout", description = "How long Ingot can wait for establishing the connection with a remote host. (In seconds)")
    val connectTimeout: Int = 3,
    @Min(0)
    @get:Doc(title = "Read Timeout", description = "How long Ingot can read data from remote proxy. (In seconds)")
    val readTimeout: Int = 15,
    // Adding:
    // @Doc(title = "Authorization", description = "The authorization information of the proxied repository.")
    // Results in converting 'authorization` property into 'allOf` component that is currently broken
    // ~ https://github.com/dzikoysk/reposilite/issues/1320
    val authorization: MirrorCredentials? = null,
    @get:Sensitive
    @get:Doc(title = "HTTP Proxy", description = """
        Custom proxy configuration for HTTP/SOCKS client used by Ingot to connect to the mirrored repository. Examples: <br/>
        HTTP 127.0.0.1:1081 <br/>
        SOCKS 127.0.0.1:1080 login password 
    """)
    val httpProxy: String = "",
    @get:Doc(title = "Authenticated Fetching Only", description = """
        Whether to require an authenticated connection before fetching uncached artifacts from the remote repository.<br/>
        _This should only ever be turned on in conjunction with the store option or with a secondary mirror host that has this option turned off.<br/>
        If you are looking to hide this mirror from unauthenticated connections, please use repository visibility options instead._
    """)
    val authenticatedFetchingOnly: Boolean = false
) : SharedSettings

@Doc(title = "Mirror Credentials", description = "The authorization credentials used to access mirrored repository.")
data class MirrorCredentials(
    @get:Doc(title = "Method", description = "Basic or custom header")
    override val method: AuthenticationMethod = AuthenticationMethod.BASIC,
    @get:Doc(title = "Login", description = "Login or custom header name to use")
    override val login: String = "",
    @get:Sensitive
    @get:Doc(title = "Password", description = "Raw password or header value used by HTTP client to connect to the given repository")
    override val password: String = ""
) : SharedSettings, RemoteCredentials

/**
 * The host a proxied reference names: trimmed, without one trailing slash. A reference is a loopback reference when this
 * host is the id of a repository of the instance, and the factory and the validation both decide it this way.
 * Empty for a blank reference.
 */
internal fun MirroredRepositorySettings.referencedHost(): String {
    val trimmed = reference.trim()
    return if (trimmed.endsWith("/")) trimmed.dropLast(1) else trimmed
}
