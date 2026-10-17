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
package net.onelitefeather.ingot.identity.model

import com.reposilite.storage.api.Location
import com.reposilite.storage.api.UnsupportedLocationException

/**
 * A permission node: `ingot.repository.<repository>.<action>`, `ingot.admin.<area>`, `*`, or an artifact scoped node
 * (identity model, permission nodes, decision D1). A pattern may carry the wildcard `*` as a whole segment. A requested
 * node is concrete, so its `*` is literal. The repository part is everything between the prefix and the final action
 * segment, so repository names may contain dots.
 *
 * Artifact scoped nodes restrict an action to a part of the repository tree, the "domain" of a Maven groupId:
 *
 * - `ingot.artifact.<groupId>[:<artifactId>].<action>` applies in every repository.
 * - `ingot.repository.<repository>.artifact.<groupId>[:<artifactId>].<action>` applies in one repository.
 *
 * The actions are `read`, `deploy`, `delete` or `*` (not `manage`, which is not path scoped). The groupId is a
 * dot-separated namespace that maps to the path prefix `net/onelitefeather/` and covers the whole subtree, subgroups
 * included. The groupId `*` covers every path; `*` with an artifactId is ambiguous and refused. With an artifactId a node
 * covers `<group path>/<artifactId>/` only, and an artifactId of `*` is the same as none. Matching compares whole path
 * segments, case-sensitively, so `net/onelitefeather` does not cover `net/onelitefeatherX`.
 *
 * In the qualified form the first segment named `artifact` after `ingot.repository.` separates the repository from the
 * artifact part, so a repository name may not contain a segment named `artifact`.
 *
 * How nodes rank against each other is documented on [NodeSpecificity].
 */
class NodePattern private constructor(
    val text: String,
    private val kind: Kind,
    /** The repository part, `*` for a wildcard, null for the admin shapes and for an unqualified artifact node. */
    val repository: String?,
    /** The action part, `*` for a wildcard, null for the other shapes. */
    val action: String?,
    /** The admin area, `*` for a wildcard, null for the other shapes. */
    val area: String?,
    /** The groupId of an artifact node, `*` for every group, null for the other shapes. */
    val groupId: String? = null,
    /** The artifactId of an artifact node, null when there is none (or it is `*`) and for the other shapes. */
    val artifactId: String? = null,
    /**
     * The path segments below the repository root. For an artifact node the subtree it covers, for a request the path it
     * asks about (empty for the whole repository, null for a path that cannot be normalised).
     */
    private val pathSegments: List<String>? = emptyList(),
) {

    private enum class Kind { EVERYTHING, REPOSITORY, ADMIN, ARTIFACT }

    /** The kind of the node: `EVERYTHING`, `REPOSITORY`, `ADMIN` or `ARTIFACT`. */
    val kindName: String
        get() = kind.name

    /**
     * Literal parts of the pattern: the closest source and the most specific node win by this count. An artifact node
     * counts [NodeSpecificity.ARTIFACT_TIER]; the full ordering of artifact nodes is [rank].
     */
    val specificity: Int = when (kind) {
        Kind.EVERYTHING -> 0
        Kind.REPOSITORY -> 2 + (if (repository != WILDCARD) 1 else 0) + (if (action != WILDCARD) 1 else 0)
        Kind.ADMIN -> 2 + (if (area != WILDCARD) 1 else 0)
        Kind.ARTIFACT -> NodeSpecificity.ARTIFACT_TIER
    }

    /** The comparable specificity, which orders artifact nodes among each other as documented on [NodeSpecificity]. */
    val rank: NodeSpecificity = when (kind) {
        Kind.ARTIFACT -> NodeSpecificity(
            tier = NodeSpecificity.ARTIFACT_TIER,
            qualified = repository != null,
            hasArtifactId = artifactId != null,
            groupSegments = if (groupId == WILDCARD) 0 else groupId.orEmpty().split('.').size,
            literalAction = action != WILDCARD,
        )
        else -> NodeSpecificity(specificity)
    }

    /**
     * Whether this pattern covers the requested node. Requests are concrete, so a wildcard in them is a literal. A request
     * is a repository action (optionally for a path), an admin area, or a concrete artifact. An unqualified artifact
     * request stands for every repository, so only nodes that cover every repository match it.
     */
    fun matches(request: NodePattern): Boolean =
        when (kind) {
            Kind.EVERYTHING -> true
            Kind.REPOSITORY -> request.isRepositoryScoped &&
                (repository == WILDCARD || (request.repository != null && repository == request.repository)) &&
                (action == WILDCARD || action == request.action)
            Kind.ADMIN -> request.kind == Kind.ADMIN && (area == WILDCARD || area == request.area)
            Kind.ARTIFACT -> mismatchOfArtifact(request) == null
        }

    /** The reason a trace shows for this pattern against the request: what it matched with, or why it did not match. */
    fun explain(request: NodePattern): String =
        if (matches(request)) {
            "matches with specificity ${if (kind == Kind.ARTIFACT) rank.toString() else specificity.toString()}"
        } else {
            "does not match ${request.text}" + (if (kind == Kind.ARTIFACT) ": ${mismatchOfArtifact(request)}" else "")
        }

    /** Why this artifact node does not cover the request, null when it does. */
    private fun mismatchOfArtifact(request: NodePattern): String? {
        if (!request.isRepositoryScoped) return "an artifact node covers repository paths only"
        if (repository != null && repository != request.repository) {
            return if (request.repository == null) {
                "the node is for repository $repository and the request is for every repository"
            } else {
                "the node is for repository $repository"
            }
        }
        val requested = request.action
        val actionCovered = action == requested ||
            (action == WILDCARD && (requested == WILDCARD || requested in ARTIFACT_ACTIONS))
        if (!actionCovered) return "the action $action does not cover $requested"
        val path = request.pathSegments ?: return "the request path cannot be normalised"
        val covered = pathSegments.orEmpty()
        if (path.size < covered.size || path.subList(0, covered.size) != covered) {
            return "the path '${path.joinToString("/")}' is outside '${covered.joinToString("/")}/'"
        }
        return null
    }

    private val isRepositoryScoped: Boolean
        get() = kind == Kind.REPOSITORY || kind == Kind.ARTIFACT

    /** The path segments of the subtree an artifact node covers (empty for the groupId `*`), null for every other kind. */
    val coveredSegments: List<String>?
        get() = if (kind == Kind.ARTIFACT) pathSegments.orEmpty() else null

    /**
     * Whether this is an artifact node that grants [action] (`read`, `deploy` or `delete`) in [repository]: its action is
     * that action or `*`, and it is unqualified or qualified for that repository.
     */
    fun isArtifactNodeFor(repository: String, action: String): Boolean =
        kind == Kind.ARTIFACT &&
            (this.repository == null || this.repository == repository) &&
            (this.action == action || (this.action == WILDCARD && action in ARTIFACT_ACTIONS))

    override fun toString(): String = text

    companion object {
        const val WILDCARD: String = "*"
        const val MAX_LENGTH: Int = 255
        private const val REPOSITORY_PREFIX = "ingot.repository."
        private const val ADMIN_PREFIX = "ingot.admin."
        private const val ARTIFACT_PREFIX = "ingot.artifact."
        private const val ARTIFACT_SEGMENT = "artifact"
        private val NAME_SEGMENT = Regex("[A-Za-z0-9_-]+")

        private const val REPOSITORY_NAME_RULE =
            "A repository name may not contain a segment named 'artifact', because the first '.artifact.' after " +
                "'ingot.repository.' separates the repository from the artifact part"

        /** The repository actions. `manage` is reserved: it is stored and listed, but no request is checked against it yet. */
        val REPOSITORY_ACTIONS: List<String> = listOf("read", "deploy", "delete", "manage")

        /** The actions of an artifact node: the repository actions that are scoped to a path, so not `manage`. */
        val ARTIFACT_ACTIONS: List<String> = listOf("read", "deploy", "delete")
        val ADMIN_AREAS: List<String> = listOf("settings", "users", "teams", "tokens", "maintenance", "audit", "console", "dashboard")

        /** The node that allows everything. */
        val EVERYTHING: NodePattern = NodePattern("*", Kind.EVERYTHING, null, null, null)

        /** `ingot.admin.*`, the former instance admin. */
        val INSTANCE_ADMIN: NodePattern = NodePattern("ingot.admin.*", Kind.ADMIN, null, null, WILDCARD)

        /** A requested repository node, for the whole repository. Not validated: callers pass names the repository service already knows. */
        fun repository(repository: String, action: String): NodePattern =
            NodePattern("$REPOSITORY_PREFIX$repository.$action", Kind.REPOSITORY, repository, action, null)

        /**
         * A requested repository action for one path inside the repository, so that artifact nodes can decide it too. The
         * path is normalised like every request location; a path that cannot be normalised (a parent segment, for
         * example) is covered by no artifact node.
         */
        fun repositoryPath(repository: String, action: String, pathInRepository: String): NodePattern =
            NodePattern(
                "$REPOSITORY_PREFIX$repository.$action", Kind.REPOSITORY, repository, action, null,
                pathSegments = segmentsOf(pathInRepository),
            )

        /** A requested admin node for one area. */
        fun admin(area: String): NodePattern =
            NodePattern("$ADMIN_PREFIX$area", Kind.ADMIN, null, null, area)

        /** The node as written, or the reason it is not valid. Null when the node is valid. */
        fun problemOf(text: String): String? =
            try {
                parse(text)
                null
            } catch (exception: IllegalArgumentException) {
                exception.message
            }

        /** Parses a valid node. Throws [IllegalArgumentException] with the reason when the node is not valid. */
        fun of(text: String): NodePattern = parse(text)

        /**
         * The segments of a path inside a repository, normalised like every request location, or null for a path that cannot
         * be normalised (a parent segment, for example). The same normalisation decides artifact nodes and listings.
         */
        fun segmentsOf(path: String): List<String>? =
            try {
                Location.of(path).toString().split('/').filter { it.isNotEmpty() }
            } catch (exception: UnsupportedLocationException) {
                null
            }

        private fun parse(text: String): NodePattern {
            require(text.isNotEmpty()) { "The node is empty" }
            require(text.length <= MAX_LENGTH) { "The node '${text.take(40)}...' is longer than $MAX_LENGTH characters" }
            require(text.none { it.isWhitespace() || it.isISOControl() }) { "The node '$text' contains whitespace or control characters" }
            require(!text.startsWith("-")) { "The node '$text' starts with '-', negation is stored as allowed false and not in the name" }

            return when {
                text == WILDCARD -> EVERYTHING
                text.startsWith(REPOSITORY_PREFIX) -> parseRepository(text)
                text.startsWith(ADMIN_PREFIX) -> parseAdmin(text)
                text.startsWith(ARTIFACT_PREFIX) -> parseArtifact(text, repository = null, body = text.removePrefix(ARTIFACT_PREFIX), hint = "")
                else -> throw IllegalArgumentException(
                    "The node '$text' is unknown, nodes start with 'ingot.repository.', 'ingot.artifact.' or 'ingot.admin.', or are '*'",
                )
            }
        }

        private fun parseRepository(text: String): NodePattern {
            val rest = text.removePrefix(REPOSITORY_PREFIX)
            val segments = rest.split('.')
            val artifactAt = segments.indexOf(ARTIFACT_SEGMENT)
            require(artifactAt != 0) { "The repository node '$text' is invalid. $REPOSITORY_NAME_RULE" }
            if (artifactAt > 0) return parseQualifiedArtifact(text, segments, artifactAt)

            val split = rest.lastIndexOf('.')
            require(split > 0) { "The repository node '$text' needs a repository and an action, for example ingot.repository.releases.read" }
            val repository = rest.substring(0, split)
            val action = rest.substring(split + 1)
            require(action == WILDCARD || action in REPOSITORY_ACTIONS) {
                "The action '$action' of '$text' is unknown, supported: ${REPOSITORY_ACTIONS.joinToString()} or *"
            }
            require(repository == WILDCARD || (!repository.contains(WILDCARD) && repository.split('.').none { it.isEmpty() })) {
                "The repository '$repository' of '$text' is invalid, a wildcard must be the whole repository segment"
            }
            return NodePattern(text, Kind.REPOSITORY, repository, action, null)
        }

        private fun parseQualifiedArtifact(text: String, segments: List<String>, artifactAt: Int): NodePattern {
            val repository = segments.subList(0, artifactAt).joinToString(".")
            require(repository != WILDCARD) {
                "The repository wildcard of '$text' is not allowed for an artifact node, use 'ingot.artifact.<groupId>.<action>' for every repository"
            }
            require(!repository.contains(WILDCARD) && segments.subList(0, artifactAt).none { it.isEmpty() }) {
                "The repository '$repository' of '$text' is invalid, a wildcard must be the whole repository segment"
            }
            val body = segments.subList(artifactAt + 1, segments.size).joinToString(".")
            return parseArtifact(text, repository, body, hint = ". $REPOSITORY_NAME_RULE")
        }

        /** Parses `<groupId>[:<artifactId>].<action>`; [hint] is appended to the structural error of the qualified form. */
        private fun parseArtifact(text: String, repository: String?, body: String, hint: String): NodePattern {
            val split = body.lastIndexOf('.')
            require(split > 0) {
                "The artifact node '$text' needs a groupId and an action, for example ingot.artifact.net.example.deploy$hint"
            }
            val coordinate = body.substring(0, split)
            val action = body.substring(split + 1)
            require(action == WILDCARD || action in ARTIFACT_ACTIONS) {
                "The action '$action' of '$text' is unknown for an artifact node, supported: ${ARTIFACT_ACTIONS.joinToString()} or * " +
                    "(manage is not scoped to a path, use a repository node)"
            }
            require(coordinate.count { it == ':' } <= 1) {
                "The coordinate '$coordinate' of '$text' can contain only one ':' between the groupId and the artifactId"
            }
            val groupId = coordinate.substringBefore(':')
            val rawArtifactId = if (coordinate.contains(':')) coordinate.substringAfter(':') else null
            require(groupId.isNotEmpty()) { "The groupId of '$text' is empty" }
            require(rawArtifactId == null || rawArtifactId.isNotEmpty()) { "The artifactId of '$text' is empty" }
            val artifactId = rawArtifactId?.takeUnless { it == WILDCARD }

            if (groupId == WILDCARD) {
                require(artifactId == null) {
                    "The groupId * with the artifactId '$artifactId' in '$text' is ambiguous, name the groupId of the artifact"
                }
            } else {
                requireNamespace("groupId", groupId, text)
            }
            if (artifactId != null) requireNamespace("artifactId", artifactId, text)

            val covered = if (groupId == WILDCARD) emptyList() else groupId.split('.') + listOfNotNull(artifactId)
            return NodePattern(
                text, Kind.ARTIFACT, repository, action, null,
                groupId = groupId, artifactId = artifactId, pathSegments = covered,
            )
        }

        private fun requireNamespace(label: String, value: String, text: String) {
            require(!value.contains(WILDCARD)) { "The $label '$value' of '$text' is invalid, a wildcard must be the whole $label" }
            val segments = value.split('.')
            require(segments.none { it.isEmpty() }) { "The $label '$value' of '$text' has an empty segment" }
            segments.firstOrNull { !NAME_SEGMENT.matches(it) }?.let { segment ->
                throw IllegalArgumentException(
                    "The $label '$value' of '$text' has an invalid character in '$segment', allowed are letters, digits, '_', '-' and '.'",
                )
            }
        }

        private fun parseAdmin(text: String): NodePattern {
            val area = text.removePrefix(ADMIN_PREFIX)
            require(area == WILDCARD || area in ADMIN_AREAS) {
                "The admin area '$area' of '$text' is unknown, supported: ${ADMIN_AREAS.joinToString()} or *"
            }
            return NodePattern(text, Kind.ADMIN, null, null, area)
        }
    }
}
