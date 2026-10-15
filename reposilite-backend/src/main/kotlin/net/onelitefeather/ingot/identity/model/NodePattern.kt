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

/**
 * A permission node: `ingot.repository.<repository>.<action>`, `ingot.admin.<area>` or `*` (identity model, permission
 * nodes, decision D1). A pattern may carry the wildcard `*` as a whole segment. A requested node is concrete, so its
 * `*` is literal. The repository part is everything between the prefix and the final action segment, so repository names
 * may contain dots.
 */
class NodePattern private constructor(
    val text: String,
    private val kind: Kind,
    private val repository: String?,
    private val action: String?,
    private val area: String?,
) {

    private enum class Kind { EVERYTHING, REPOSITORY, ADMIN }

    /** Literal parts of the pattern: the closest source and the most specific node win by this count. */
    val specificity: Int = when (kind) {
        Kind.EVERYTHING -> 0
        Kind.REPOSITORY -> 2 + (if (repository != WILDCARD) 1 else 0) + (if (action != WILDCARD) 1 else 0)
        Kind.ADMIN -> 2 + (if (area != WILDCARD) 1 else 0)
    }

    /** Whether this pattern covers the requested node. Requests are concrete, so a wildcard in them is a literal. */
    fun matches(request: NodePattern): Boolean =
        when (kind) {
            Kind.EVERYTHING -> true
            Kind.REPOSITORY -> request.kind == Kind.REPOSITORY &&
                (repository == WILDCARD || repository == request.repository) &&
                (action == WILDCARD || action == request.action)
            Kind.ADMIN -> request.kind == Kind.ADMIN && (area == WILDCARD || area == request.area)
        }

    override fun toString(): String = text

    companion object {
        const val WILDCARD: String = "*"
        const val MAX_LENGTH: Int = 255
        private const val REPOSITORY_PREFIX = "ingot.repository."
        private const val ADMIN_PREFIX = "ingot.admin."

        val REPOSITORY_ACTIONS: List<String> = listOf("read", "deploy", "delete", "manage")
        val ADMIN_AREAS: List<String> = listOf("settings", "users", "teams", "tokens", "maintenance", "audit", "console", "dashboard")

        /** The node that allows everything. */
        val EVERYTHING: NodePattern = NodePattern("*", Kind.EVERYTHING, null, null, null)

        /** `ingot.admin.*`, the former instance admin. */
        val INSTANCE_ADMIN: NodePattern = NodePattern("ingot.admin.*", Kind.ADMIN, null, null, WILDCARD)

        /** A requested repository node. Not validated: callers pass names the repository service already knows. */
        fun repository(repository: String, action: String): NodePattern =
            NodePattern("$REPOSITORY_PREFIX$repository.$action", Kind.REPOSITORY, repository, action, null)

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

        private fun parse(text: String): NodePattern {
            require(text.isNotEmpty()) { "The node is empty" }
            require(text.length <= MAX_LENGTH) { "The node '${text.take(40)}...' is longer than $MAX_LENGTH characters" }
            require(text.none { it.isWhitespace() || it.isISOControl() }) { "The node '$text' contains whitespace or control characters" }
            require(!text.startsWith("-")) { "The node '$text' starts with '-', negation is stored as allowed false and not in the name" }

            return when {
                text == WILDCARD -> EVERYTHING
                text.startsWith(REPOSITORY_PREFIX) -> parseRepository(text)
                text.startsWith(ADMIN_PREFIX) -> parseAdmin(text)
                else -> throw IllegalArgumentException(
                    "The node '$text' is unknown, nodes start with 'ingot.repository.' or 'ingot.admin.', or are '*'",
                )
            }
        }

        private fun parseRepository(text: String): NodePattern {
            val rest = text.removePrefix(REPOSITORY_PREFIX)
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

        private fun parseAdmin(text: String): NodePattern {
            val area = text.removePrefix(ADMIN_PREFIX)
            require(area == WILDCARD || area in ADMIN_AREAS) {
                "The admin area '$area' of '$text' is unknown, supported: ${ADMIN_AREAS.joinToString()} or *"
            }
            return NodePattern(text, Kind.ADMIN, null, null, area)
        }
    }
}
