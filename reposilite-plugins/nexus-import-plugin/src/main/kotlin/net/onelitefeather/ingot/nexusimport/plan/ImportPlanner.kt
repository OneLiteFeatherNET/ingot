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

package net.onelitefeather.ingot.nexusimport.plan

/** What the importer learned about one Nexus repository. Settings fields are null when unknown or unreadable. */
data class RepositoryFacts(
    val name: String,
    val format: String,
    /** `hosted`, `proxy` or `group`. */
    val type: String,
    val writePolicy: String?,
    val versionPolicy: String?,
    val remoteUrl: String?,
    val memberNames: List<String>,
    /** False when the Maven settings answered 403, so only the listing is known. */
    val settingsReadable: Boolean,
    val components: Long,
    val bytes: Long,
)

enum class LossStatus { MAPPED, PARTIAL, MANUAL, UNSUPPORTED, EXCLUDED }

enum class IngotType { HOSTED, PROXY, GROUP }

/** One row of the loss report: an object, what becomes of it, and what the operator does about it. */
data class LossRow(
    val objectType: String,
    val nexusId: String,
    val target: String?,
    val status: LossStatus,
    val reason: String,
    val consequence: String,
    val action: String,
)

/** A repository definition the importer would create. It never carries a credential. */
data class PlannedRepository(
    val nexusName: String,
    val targetName: String,
    val type: IngotType,
    val redeployment: Boolean,
    val preserveSnapshots: Boolean,
    val proxyRemoteUrl: String?,
    val members: List<String>,
    /** True when the definition must be entered by hand, while the content can still be copied. */
    val definitionManual: Boolean,
)

data class ImportPlan(
    val rows: List<LossRow>,
    val repositories: List<PlannedRepository>,
    val blockers: List<String>,
    val transferBytes: Long,
    val formats: Map<String, Int>,
)

/**
 * Turns what Nexus holds into the loss report and the definitions to create. It reads nothing and writes nothing, so a dry
 * run can use it alone. An existing Ingot repository is never planned for change: a taken name gets the `-nexus` suffix.
 */
class ImportPlanner(private val existingTargets: Set<String>) {

    fun plan(facts: List<RepositoryFacts>, selection: Set<String>?): ImportPlan {
        val rows = mutableListOf<LossRow>()
        val planned = mutableListOf<PlannedRepository>()
        val blockers = mutableListOf<String>()
        val maven = facts.filter { it.format == "maven2" }.associateBy { it.name }

        facts.forEach { fact ->
            if (selection != null && fact.name !in selection) {
                rows += LossRow("repository", fact.name, null, LossStatus.EXCLUDED, "Deselected by the operator", "Nothing is done for it", "None")
            }
        }
        val chosen = facts.filter { selection == null || it.name in selection }

        chosen.filter { it.format != "maven2" }.forEach { fact ->
            rows += LossRow(
                "repository", fact.name, null, LossStatus.UNSUPPORTED,
                "The ${fact.format} format is not imported yet: ${fact.components} components, ${fact.bytes} bytes",
                "Its content stays in Nexus", "Migrate it with a format tool or keep Nexus for it",
            )
        }

        val mavenChosen = chosen.filter { it.format == "maven2" }
        if (mavenChosen.any { !it.settingsReadable }) {
            rows += LossRow(
                "area", "maven settings", null, LossStatus.MANUAL,
                "The credential may not read the Maven settings of some repositories",
                "Their definitions are not created", "Grant the settings read permission, or enter the definitions by hand",
            )
        }

        mavenChosen.forEach { fact ->
            val target = targetFor(fact.name)
            if (target == null) {
                blockers += "No free name for ${fact.name}: ${fact.name}-nexus is taken as well"
                return@forEach
            }
            val collision = target != fact.name
            val collisionNote = if (collision) "The name ${fact.name} is taken, so the proposal is $target. " else ""
            when (fact.type) {
                "hosted" -> planHosted(fact, target, collisionNote, planned, rows)
                "proxy" -> planProxy(fact, target, collisionNote, planned, rows)
                "group" -> planGroup(fact, target, collisionNote, maven, planned, rows)
                else -> rows += LossRow("repository", fact.name, target, LossStatus.UNSUPPORTED, "Unknown type ${fact.type}", "Nothing is created", "None")
            }
        }

        val formats = facts.groupingBy { it.format }.eachCount()
        val transfer = planned.filter { it.type == IngotType.HOSTED }
            .sumOf { fact -> maven.getValue(fact.nexusName).bytes }
        return ImportPlan(rows, planned, blockers, transfer, formats)
    }

    /** The name the definition would get, or null when even the suffixed name is taken. */
    private fun targetFor(name: String): String? = when {
        name !in existingTargets -> name
        "$name-nexus" !in existingTargets -> "$name-nexus"
        else -> null
    }

    private fun planHosted(fact: RepositoryFacts, target: String, note: String, planned: MutableList<PlannedRepository>, rows: MutableList<LossRow>) {
        val snapshots = fact.versionPolicy == "SNAPSHOT" || fact.versionPolicy == "MIXED"
        val deny = fact.writePolicy == "DENY"
        val manual = !fact.settingsReadable
        planned += PlannedRepository(
            nexusName = fact.name,
            targetName = target,
            type = IngotType.HOSTED,
            redeployment = fact.writePolicy == "ALLOW",
            preserveSnapshots = snapshots,
            proxyRemoteUrl = null,
            members = emptyList(),
            definitionManual = manual,
        )
        val reasons = buildList {
            if (note.isNotEmpty()) add(note.trim())
            if (deny) add("Write policy DENY has no read-only equivalent yet, so the repository is not redeployable")
            if (snapshots) add("Older snapshot builds are kept, Nexus removes them differently")
            if (manual) add("The definition must be entered by hand")
        }
        val status = if (manual) LossStatus.MANUAL else if (reasons.any { it.startsWith("Write") || it.startsWith("Older") }) LossStatus.PARTIAL else LossStatus.MAPPED
        rows += LossRow("repository", fact.name, target, status, reasons.joinToString(" "), "Private repository, content copied", "Review the definition after the run")
    }

    private fun planProxy(fact: RepositoryFacts, target: String, note: String, planned: MutableList<PlannedRepository>, rows: MutableList<LossRow>) {
        planned += PlannedRepository(fact.name, target, IngotType.PROXY, false, false, fact.remoteUrl, emptyList(), !fact.settingsReadable)
        rows += LossRow(
            "repository", fact.name, target, LossStatus.PARTIAL,
            (note + "The cache is not copied, and the credentials, negative cache and auto block are not carried over").trim(),
            "Proxied files are fetched again from the remote", "Enter the remote credentials again if the remote needs them",
        )
    }

    private fun planGroup(
        fact: RepositoryFacts,
        target: String,
        note: String,
        maven: Map<String, RepositoryFacts>,
        planned: MutableList<PlannedRepository>,
        rows: MutableList<LossRow>,
    ) {
        val kept = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        fact.memberNames.forEach { member ->
            val memberFact = maven[member]
            if (memberFact == null) dropped += member else kept += (targetFor(member) ?: member)
        }
        planned += PlannedRepository(fact.name, target, IngotType.GROUP, false, false, null, kept, false)
        val reason = (note + if (dropped.isEmpty()) "" else "Members of another format were dropped: ${dropped.joinToString(", ")}").trim()
        rows += LossRow(
            "repository", fact.name, target,
            if (dropped.isEmpty()) LossStatus.MAPPED else LossStatus.PARTIAL,
            reason, "Reads resolve through the listed members in order", "Check the member order in the definition",
        )
    }
}

/** The loss report as CSV: a header line, then one quoted line per row. */
object LossReport {

    private val header = listOf("objectType", "nexusId", "target", "status", "reason", "consequence", "action")

    fun csv(rows: List<LossRow>): String = buildString {
        appendLine(header.joinToString(","))
        rows.forEach { row ->
            val fields = listOf(row.objectType, row.nexusId, row.target.orEmpty(), row.status.name, row.reason, row.consequence, row.action)
            appendLine(fields.joinToString(",") { quote(it) })
        }
    }

    private fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
