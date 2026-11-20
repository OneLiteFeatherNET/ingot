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

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.zip.ZipFile
import kotlin.metadata.Visibility
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.visibility

// The internal filter of the Ingot plugin API report (design D4 of extend-plugin-abi-gate). japicmp sees a Kotlin internal class as
// public, and Kotlin gives an internal member of a public class a name with a '$'. Neither is plugin API. The filter errs on the side
// of keeping a finding: a class whose metadata cannot be read stays in the comparison.
object InternalClassFilter {

    private const val METADATA = "Lkotlin/Metadata;"

    // The binary names of the internal classes of the packages, and of the classes nested in them, sorted.
    fun internalClasses(archive: File, packages: Collection<String>, onUnreadable: (String, Exception) -> Unit): List<String> {
        val prefixes = packages.map { it.replace('.', '/') + "/" }
        val names = mutableListOf<String>()
        val internal = mutableSetOf<String>()
        ZipFile(archive).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".class") && prefixes.any(it.name::startsWith) }
                .forEach { entry ->
                    val name = entry.name.removeSuffix(".class").replace('/', '.')
                    names += name
                    val metadata = metadataOf(zip.getInputStream(entry).use { it.readBytes() }) ?: return@forEach
                    try {
                        val read = KotlinClassMetadata.readLenient(metadata)
                        if (read is KotlinClassMetadata.Class && read.kmClass.visibility == Visibility.INTERNAL) internal += name
                    } catch (error: Exception) {
                        onUnreadable(name, error)
                    }
                }
        }
        return names.filter { name -> name in internal || internal.any { name.startsWith("$it\$") } }.sorted()
    }

    // A member whose name holds a '$' is mangled by Kotlin, or synthetic, and is no plugin API.
    fun isExcludedMember(name: String): Boolean = '$' in name

    private fun metadataOf(bytes: ByteArray): Metadata? {
        val node = ClassNode().also { ClassReader(bytes).accept(it, ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES) }
        val annotation = node.visibleAnnotations?.firstOrNull { it.desc == METADATA } ?: return null
        val values = annotation.values.orEmpty().chunked(2).associate { (key, value) -> key as String to value }
        return kotlin.metadata.jvm.Metadata(
            kind = values["k"] as Int?,
            metadataVersion = (values["mv"] as List<*>?)?.map { it as Int }?.toIntArray(),
            data1 = (values["d1"] as List<*>?)?.map { it as String }?.toTypedArray(),
            data2 = (values["d2"] as List<*>?)?.map { it as String }?.toTypedArray(),
            extraString = values["xs"] as String?,
            packageName = values["pn"] as String?,
            extraInt = values["xi"] as Int?,
        )
    }
}

// Writes the internal classes of the Ingot plugin API packages in the jar of this build, one binary name per line, for the class
// excludes of the comparison. A class whose metadata cannot be read is logged and kept.
abstract class IngotInternalClassesTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archive: RegularFileProperty

    @get:Input
    abstract val packages: ListProperty<String>

    @get:OutputFile
    abstract val internalClasses: RegularFileProperty

    @TaskAction
    fun write() {
        val names = InternalClassFilter.internalClasses(archive.get().asFile, packages.get()) { name, error ->
            logger.warn("Cannot read the Kotlin metadata of $name, so it stays in the Ingot plugin API report: ${error.message}")
        }
        internalClasses.get().asFile.apply { parentFile.mkdirs() }.writeText(names.joinToString("") { "$it\n" })
        logger.lifecycle("Found ${names.size} internal classes in the Ingot plugin API packages.")
    }
}
