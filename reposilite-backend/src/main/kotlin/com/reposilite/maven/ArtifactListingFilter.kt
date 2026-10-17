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
package com.reposilite.maven

import net.onelitefeather.ingot.identity.evaluation.AccessRequest
import net.onelitefeather.ingot.identity.evaluation.PermissionEvaluator
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.NodePattern

/**
 * Decides which entries of a directory listing a subject may see in one repository, for subjects whose rights involve
 * artifact read nodes. An entry is shown when the subject may read it, which is the decision of the evaluator for the path
 * of the entry like any other read, or when it is a directory on the way to a subtree the subject may read.
 *
 * The filter exists only for a repository where some artifact node speaks about reading ([of] returns null otherwise), so
 * every other subject keeps the decisions it always had. It asks the evaluator once per entry, and reads the readable
 * subtrees once when it is created.
 */
internal class ArtifactListingFilter private constructor(
    private val subject: AccessSubject,
    private val repository: String,
    private val subtrees: List<List<String>>,
) {

    /**
     * Whether the entry [name] of the directory [directory] (a path inside the repository, empty for the root) is shown.
     * A file is shown when it may be read. A directory is shown when it may be read, or when a readable subtree lies
     * beneath it.
     */
    fun shows(directory: String, name: String, isDirectory: Boolean = true): Boolean {
        val path = if (directory.isEmpty()) name else "$directory/$name"
        if (PermissionEvaluator.allows(subject, AccessRequest("/$repository/$path", Action.READ))) return true
        return isDirectory && leadsToReadable(path)
    }

    /** Whether a readable subtree lies at or beneath [directory], a path inside the repository (empty for the root). */
    fun leadsToReadable(directory: String): Boolean {
        val segments = NodePattern.segmentsOf(directory) ?: return false
        return subtrees.any { subtree -> subtree.size >= segments.size && subtree.subList(0, segments.size) == segments }
    }

    companion object {
        /** The filter for [subject] in [repository], or null when no artifact node speaks about reading there. */
        fun of(subject: AccessSubject, repository: String): ArtifactListingFilter? =
            if (PermissionEvaluator.hasArtifactReadRule(subject, repository)) {
                ArtifactListingFilter(subject, repository, PermissionEvaluator.readableArtifactSubtrees(subject, repository))
            } else {
                null
            }
    }
}
