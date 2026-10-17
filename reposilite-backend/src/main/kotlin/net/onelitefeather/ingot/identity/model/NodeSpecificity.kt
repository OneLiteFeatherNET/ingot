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
 * How specific a permission node is: the most specific matching node decides, and a denial wins a tie.
 *
 * The key is compared lexicographically, field by field, in this exact order:
 *
 * 1. [tier]: `*` is 0, the repository and admin nodes keep their literal count (2 to 4), an artifact node is
 *    [ARTIFACT_TIER], so an artifact node always decides over a repository node at the same position.
 *
 * Within the artifact tier, compare:
 *
 * 2. [qualified]: a repository-qualified node beats an unqualified one.
 * 3. [hasArtifactId]: a node with an artifactId (not `*`) beats a groupId-only node.
 * 4. [groupSegments]: a longer groupId (more dot-separated segments) beats a shorter one; the groupId `*` has none.
 * 5. [literalAction]: a literal action beats the action `*`.
 *
 * The fields after the tier are 0 or false for every node that is not an artifact node, so the existing node kinds
 * rank exactly by their former Int specificity.
 */
data class NodeSpecificity(
    val tier: Int,
    val qualified: Boolean = false,
    val hasArtifactId: Boolean = false,
    val groupSegments: Int = 0,
    val literalAction: Boolean = false,
) : Comparable<NodeSpecificity> {

    override fun compareTo(other: NodeSpecificity): Int =
        compareValuesBy(this, other, { it.tier }, { it.qualified }, { it.hasArtifactId }, { it.groupSegments }, { it.literalAction })

    override fun toString(): String =
        if (tier == ARTIFACT_TIER) {
            "artifact(qualified=$qualified, artifactId=$hasArtifactId, groupSegments=$groupSegments, literalAction=$literalAction)"
        } else {
            tier.toString()
        }

    companion object {
        /** The tier of an artifact node: above the most specific repository node, which counts 4. */
        const val ARTIFACT_TIER: Int = 5

        /** What a role grant counts: as much as the most specific repository node it can stand for. */
        val ROLE_GRANT: NodeSpecificity = NodeSpecificity(tier = 4)
    }
}
