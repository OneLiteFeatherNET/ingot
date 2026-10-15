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
package net.onelitefeather.ingot.identity.evaluation

import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.Grant

/** What a request asks for: a full path as `/repository/path/in/repository`, and the action. */
data class AccessRequest(val fullPath: String, val action: Action)

/**
 * Decides what a subject may do. Pure: it reads nothing but its arguments, so every rule can be tested without a
 * database. The rules are those of the identity model specification (section 5). The legacy grants keep the old route
 * semantics exactly, so migrated tokens behave as they did before.
 */
object PermissionEvaluator {

    /** Whether the request is allowed. Disabled subjects are denied everything, scopes can only narrow the rights. */
    fun allows(subject: AccessSubject, request: AccessRequest): Boolean {
        if (subject.disabled) return false
        if (!scopeAllows(subject, request.action)) return false
        if (isInstanceAdmin(subject)) return true

        val (repository, pathInRepository) = splitPath(request.fullPath)
        return subject.grants.any { grant ->
            request.action in grant.role.actions && matches(grant, request.fullPath, repository, pathInRepository)
        }
    }

    /**
     * Whether the subject may see the repository named by [fragment], a repository fragment such as `/releases`. This is
     * the old `AccessTokenFacade.canSee` for legacy grants, with its asymmetric case handling kept as it was.
     */
    fun canSee(subject: AccessSubject, fragment: String): Boolean {
        if (subject.disabled) return false
        if (subject.scopes != null && Action.READ !in subject.scopes) return false
        if (isInstanceAdmin(subject)) return true

        return subject.grants.any { grantReaches(it, fragment) }
    }

    /**
     * Whether a grant reaches the repository of [fragment] at all, whatever its actions. Used by [canSee] and by the access
     * explanation of the identity API, so both answer the same question.
     */
    fun grantReaches(grant: Grant, fragment: String): Boolean =
        if (grant.role.legacy) {
            val path = grant.pathPrefix
            path != null && (path.startsWith(fragment, ignoreCase = true) || fragment.startsWith(path))
        } else {
            grant.repository == null || grant.repository == fragment.removePrefix("/")
        }

    /**
     * Whether a HIDDEN repository is listed for the subject: instance admins, and principals with a grant of role READ or
     * higher on it. Legacy grants never list a HIDDEN repository, since they never did.
     */
    fun canListHidden(subject: AccessSubject, repository: String): Boolean {
        if (subject.disabled) return false
        if (subject.scopes != null && Action.READ !in subject.scopes) return false
        if (isInstanceAdmin(subject)) return true

        return subject.grants.any { grant ->
            !grant.role.legacy && Action.READ in grant.role.actions && (grant.repository == null || grant.repository == repository)
        }
    }

    /** INSTANCE_ADMIN as an instance role, direct or through a team, when the token is not narrowed away from it. */
    fun isInstanceAdmin(subject: AccessSubject): Boolean =
        !subject.disabled && subject.instanceAdmin && (subject.scopes == null || Action.INSTANCE_ADMIN in subject.scopes)

    private fun scopeAllows(subject: AccessSubject, action: Action): Boolean =
        subject.scopes == null || action in subject.scopes

    private fun matches(grant: Grant, fullPath: String, repository: String, pathInRepository: String): Boolean =
        if (grant.role.legacy) {
            // Legacy routes match the full path by plain case-insensitive prefix, without a segment boundary.
            val prefix = grant.pathPrefix
            prefix != null && fullPath.startsWith(prefix, ignoreCase = true)
        } else {
            (grant.repository == null || grant.repository == repository) && withinPrefix(grant.pathPrefix, pathInRepository)
        }

    /** A new-model prefix matches on whole segments, case-sensitively. A missing or root prefix covers the repository. */
    private fun withinPrefix(prefix: String?, pathInRepository: String): Boolean {
        val normalized = prefix?.trim('/').orEmpty()
        if (normalized.isEmpty()) return true
        return pathInRepository == normalized || pathInRepository.startsWith("$normalized/")
    }

    /** Splits `/repository/path/in/repository` into the repository and the path inside it. */
    internal fun splitPath(fullPath: String): Pair<String, String> {
        val trimmed = fullPath.removePrefix("/")
        return trimmed.substringBefore('/') to trimmed.substringAfter('/', "")
    }

}
