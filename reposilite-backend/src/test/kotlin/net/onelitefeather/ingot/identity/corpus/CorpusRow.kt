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
package net.onelitefeather.ingot.identity.corpus

import net.onelitefeather.ingot.identity.evaluation.AccessRequest
import net.onelitefeather.ingot.identity.evaluation.Decision
import net.onelitefeather.ingot.identity.evaluation.PermissionEvaluator
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.NodePattern

/** The decision functions of [PermissionEvaluator] the corpus records. Every row names exactly one of them. */
internal enum class EntryPoint {
    ALLOWS,
    DECIDE,
    DECIDE_PATH,
    IS_INSTANCE_ADMIN,
    ALLOWS_ADMIN_AREA,
    CAN_SEE,
    CAN_LIST_HIDDEN,
    HAS_ARTIFACT_READ_RULE,
    READABLE_ARTIFACT_SUBTREES,
}

/**
 * One recorded decision. [expected] is what the code decides on main and [outcome] asks the code again. [note] marks a known
 * surprise or a deliberate reading of the current behaviour. [changesIn] names a later change that alters this outcome.
 */
internal class CorpusRow(
    val id: String,
    val entryPoint: EntryPoint,
    val fixture: String,
    val request: String,
    val expected: String,
    val note: String?,
    val changesIn: String?,
    private val outcome: () -> String,
) {
    /** What the code decides now. An exception is an outcome too, recorded by its class. */
    fun evaluate(): String =
        try {
            outcome()
        } catch (exception: RuntimeException) {
            "threw ${exception::class.simpleName}"
        }

    /** Names the row in a failure message, so a red test points at the row. */
    fun describe(): String =
        "row '$id' ($entryPoint, fixture: $fixture, request: $request)" +
            (note?.let { ", note: $it" } ?: "") +
            (changesIn?.let { ", changesIn: $it" } ?: "")
}

/** `true|false`, the deciding kind and the deciding node. A missing kind or node is `-`. */
internal fun Decision.render(): String = "$allowed ${decidingKind ?: "-"} ${decidingNode ?: "-"}"

/** The readable subtrees, each as its segments below the repository root, `<root>` for the whole repository. */
internal fun List<List<String>>.render(): String =
    if (isEmpty()) "<none>" else joinToString(";") { segments -> segments.ifEmpty { listOf("<root>") }.joinToString("/") }

internal fun decideRow(
    id: String,
    fixture: String,
    subject: () -> AccessSubject,
    node: String,
    expected: String,
    note: String? = null,
    changesIn: String? = null,
) = CorpusRow(id, EntryPoint.DECIDE, fixture, node, expected, note, changesIn) {
    PermissionEvaluator.decide(subject(), NodePattern.of(node)).render()
}

internal fun allowsRow(
    id: String,
    fixture: String,
    subject: () -> AccessSubject,
    path: String,
    action: Action,
    expected: Boolean,
    note: String? = null,
    changesIn: String? = null,
) = CorpusRow(id, EntryPoint.ALLOWS, fixture, "$action $path", expected.toString(), note, changesIn) {
    PermissionEvaluator.allows(subject(), AccessRequest(path, action)).toString()
}

internal fun decidePathRow(
    id: String,
    fixture: String,
    subject: () -> AccessSubject,
    repository: String,
    path: String,
    action: String,
    expected: String,
    note: String? = null,
    changesIn: String? = null,
) = CorpusRow(id, EntryPoint.DECIDE_PATH, fixture, "$action $repository/$path", expected, note, changesIn) {
    PermissionEvaluator.decidePath(subject(), repository, path, action).render()
}

internal fun instanceAdminRow(
    id: String,
    fixture: String,
    subject: () -> AccessSubject,
    expected: Boolean,
    note: String? = null,
) = CorpusRow(id, EntryPoint.IS_INSTANCE_ADMIN, fixture, "ingot.admin.*", expected.toString(), note, null) {
    PermissionEvaluator.isInstanceAdmin(subject()).toString()
}

/** [area] null asks for the instance admin itself. */
internal fun adminAreaRow(
    id: String,
    fixture: String,
    subject: () -> AccessSubject,
    area: String?,
    expected: Boolean,
    note: String? = null,
) = CorpusRow(id, EntryPoint.ALLOWS_ADMIN_AREA, fixture, "area ${area ?: "instance admin"}", expected.toString(), note, null) {
    PermissionEvaluator.allowsAdminArea(subject(), area).toString()
}

internal fun canSeeRow(
    id: String,
    fixture: String,
    subject: () -> AccessSubject,
    fragment: String,
    expected: Boolean,
    note: String? = null,
) = CorpusRow(id, EntryPoint.CAN_SEE, fixture, fragment, expected.toString(), note, null) {
    PermissionEvaluator.canSee(subject(), fragment).toString()
}

internal fun canListHiddenRow(
    id: String,
    fixture: String,
    subject: () -> AccessSubject,
    repository: String,
    expected: Boolean,
    note: String? = null,
) = CorpusRow(id, EntryPoint.CAN_LIST_HIDDEN, fixture, repository, expected.toString(), note, null) {
    PermissionEvaluator.canListHidden(subject(), repository).toString()
}

internal fun artifactRuleRow(
    id: String,
    fixture: String,
    subject: () -> AccessSubject,
    repository: String,
    expected: Boolean,
    note: String? = null,
) = CorpusRow(id, EntryPoint.HAS_ARTIFACT_READ_RULE, fixture, repository, expected.toString(), note, null) {
    PermissionEvaluator.hasArtifactReadRule(subject(), repository).toString()
}

internal fun subtreeRow(
    id: String,
    fixture: String,
    subject: () -> AccessSubject,
    repository: String,
    expected: String,
    note: String? = null,
) = CorpusRow(id, EntryPoint.READABLE_ARTIFACT_SUBTREES, fixture, repository, expected, note, null) {
    PermissionEvaluator.readableArtifactSubtrees(subject(), repository).render()
}
