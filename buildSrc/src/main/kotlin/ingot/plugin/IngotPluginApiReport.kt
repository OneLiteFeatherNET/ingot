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

package ingot.plugin

import ingot.plugin.IngotFindingClassifier.Group
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.zip.ZipFile

// The simple names of the annotations on a type and on its members, members keyed by name. Read with ASM, because japicmp does
// not report the annotations Kotlin writes with binary retention, which is how every ExperimentalIngot* marker is declared.
data class TypeMarkers(val annotations: Set<String>, val memberAnnotations: Map<String, Set<String>>) {

    companion object {

        fun read(bytes: ByteArray): TypeMarkers {
            val node = ClassNode().also { ClassReader(bytes).accept(it, ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES) }
            val members = node.methods.map { it.name to names(it.visibleAnnotations, it.invisibleAnnotations) } +
                node.fields.map { it.name to names(it.visibleAnnotations, it.invisibleAnnotations) }
            return TypeMarkers(
                annotations = names(node.visibleAnnotations, node.invisibleAnnotations),
                memberAnnotations = members
                    .filter { it.second.isNotEmpty() }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, sets) -> sets.flatten().toSet() },
            )
        }

        // Reads the named classes, given by binary name, from the archives. A class that no archive holds is left out.
        fun fromArchives(archives: Iterable<File>, classNames: Set<String>): Map<String, TypeMarkers> {
            val wanted = classNames.associateBy { it.replace('.', '/') + ".class" }
            val markers = mutableMapOf<String, TypeMarkers>()
            archives.filter { it.isFile }.forEach { archive ->
                ZipFile(archive).use { zip ->
                    wanted.forEach { (entryName, className) ->
                        val entry = zip.getEntry(entryName) ?: return@forEach
                        markers.putIfAbsent(className, read(zip.getInputStream(entry).use { it.readBytes() }))
                    }
                }
            }
            return markers
        }

        private fun names(vararg lists: List<AnnotationNode>?): Set<String> =
            lists.filterNotNull().flatten().map { it.desc.removeSuffix(";").substringAfterLast('/').substringAfterLast('$') }.toSet()
    }
}

// Writes the Markdown report of the Ingot plugin API (design D3 of extend-plugin-abi-gate). Each changed class is one top-level
// bullet with its changes nested under it, in the section of its group. A class whose changes fall into both groups is listed in
// both sections, each time with its own changes. .github/scripts/plugin_api_summary.py counts the top-level bullets per section.
object IngotPluginApiReport {

    const val NEWLY_EXPERIMENTAL_NOTE = "marked experimental in this build"

    private data class Entry(val className: String, val note: String?, val lines: List<String>)

    fun render(
        classes: List<JapicmpClassChanges>,
        newMarkers: Map<String, TypeMarkers>,
        oldMarkers: Map<String, TypeMarkers>,
        experimentalPackages: Collection<String>,
        baseline: String,
    ): String {
        val entries = classes.flatMap { entriesOf(it, newMarkers, oldMarkers, experimentalPackages) }
        return buildString {
            append("# Ingot plugin API report\n\n")
            append("Binary incompatible changes of the Ingot plugin API packages, compared with $baseline. ")
            append("Promised findings break plugins that use the stable API. Experimental findings are reported for information ")
            append("and never block.\n")
            section("Promised", entries.filter { it.first == Group.PROMISED }.map { it.second })
            section("Experimental", entries.filter { it.first == Group.EXPERIMENTAL }.map { it.second })
        }
    }

    // The names a type is annotated with in one jar: its own annotations and those of the types it is nested in.
    private fun typeAnnotations(className: String, markers: Map<String, TypeMarkers>): Set<String> =
        withEnclosing(className).flatMap { markers[it]?.annotations.orEmpty() }.toSet()

    // The classes whose markers the report reads: every changed class and the classes it is nested in.
    fun requiredClasses(classes: List<JapicmpClassChanges>): Set<String> =
        classes.flatMap { withEnclosing(it.className) }.toSet()

    private fun withEnclosing(className: String): List<String> =
        className.split('$').runningReduce { enclosing, nested -> "$enclosing\$$nested" }

    private fun entriesOf(
        change: JapicmpClassChanges,
        newMarkers: Map<String, TypeMarkers>,
        oldMarkers: Map<String, TypeMarkers>,
        experimentalPackages: Collection<String>,
    ): List<Pair<Group, Entry>> {
        val packageName = change.className.substringBeforeLast('.', "")
        val newAnnotations = typeAnnotations(change.className, newMarkers)
        val oldAnnotations = typeAnnotations(change.className, oldMarkers)
        val typeGroup = IngotFindingClassifier.classify(packageName, newAnnotations + oldAnnotations, experimentalPackages)
        val newlyMarked = change.className in oldMarkers && marked(newAnnotations) && !marked(oldAnnotations)

        val typeLines = change.typeChanges.map { "type: $it" }
        val memberLines = change.members.map { member ->
            val annotations = newMarkers[change.className]?.memberAnnotations?.get(member.name).orEmpty() +
                oldMarkers[change.className]?.memberAnnotations?.get(member.name).orEmpty()
            val group = if (typeGroup == Group.EXPERIMENTAL) typeGroup
            else IngotFindingClassifier.classify(packageName, annotations, experimentalPackages)
            group to "`${member.label}`: ${member.changes.joinToString(", ")}"
        }

        val promised = (if (typeGroup == Group.PROMISED) typeLines else emptyList()) +
            memberLines.filter { it.first == Group.PROMISED }.map { it.second }
        val experimental = (if (typeGroup == Group.EXPERIMENTAL) typeLines else emptyList()) +
            memberLines.filter { it.first == Group.EXPERIMENTAL }.map { it.second }
        return listOfNotNull(
            promised.takeIf { it.isNotEmpty() }?.let { Group.PROMISED to Entry(change.className, null, it) },
            experimental.takeIf { it.isNotEmpty() }?.let {
                Group.EXPERIMENTAL to Entry(change.className, NEWLY_EXPERIMENTAL_NOTE.takeIf { newlyMarked }, it)
            },
        )
    }

    private fun marked(annotations: Set<String>): Boolean =
        annotations.any { it.startsWith(IngotFindingClassifier.EXPERIMENTAL_ANNOTATION_PREFIX) }

    private fun StringBuilder.section(heading: String, entries: List<Entry>) {
        append("\n## $heading\n\n")
        when (entries.size) {
            0 -> append("No findings.\n")
            1 -> append("1 finding.\n\n")
            else -> append("${entries.size} findings.\n\n")
        }
        entries.forEach { entry ->
            append("- `${entry.className}`")
            entry.note?.let { append(" ($it)") }
            append('\n')
            entry.lines.forEach { append("  - $it\n") }
        }
    }
}
