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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Path
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.metadata.KmClass
import kotlin.metadata.Visibility
import kotlin.metadata.jvm.JvmMetadataVersion
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.visibility

// Every test writes its own class files into a jar in a temporary directory. The Kotlin metadata of a fixture is written with
// kotlin-metadata-jvm, as the compiler writes it, so no class file is read from the repository.
class InternalClassFilterTest {

    @TempDir
    lateinit var tempDir: Path

    private val packages = listOf("net.onelitefeather.ingot.search")

    @Test
    fun internalKotlinClassIsListed() {
        val jar = jarOf(
            "net.onelitefeather.ingot.search.application.IndexWriter" to kotlinClass("net/onelitefeather/ingot/search/application/IndexWriter", Visibility.INTERNAL),
            "net.onelitefeather.ingot.search.backend.SearchBackend" to kotlinClass("net/onelitefeather/ingot/search/backend/SearchBackend", Visibility.PUBLIC),
        )

        assertThat(internalClassesOf(jar))
            .describedAs("only the class with the Kotlin visibility internal is excluded, the public one is compared")
            .containsExactly("net.onelitefeather.ingot.search.application.IndexWriter")
    }

    @Test
    fun nestedClassOfInternalClassIsListed() {
        val jar = jarOf(
            "net.onelitefeather.ingot.search.Index" to kotlinClass("net/onelitefeather/ingot/search/Index", Visibility.INTERNAL),
            "net.onelitefeather.ingot.search.Index\$Entry" to kotlinClass("net/onelitefeather/ingot/search/Index.Entry", Visibility.PUBLIC),
        )

        assertThat(internalClassesOf(jar))
            .describedAs("a nested class is no more visible than the internal class it is declared in")
            .containsExactly("net.onelitefeather.ingot.search.Index", "net.onelitefeather.ingot.search.Index\$Entry")
    }

    @Test
    fun classWithUnreadableMetadataIsKeptAndLogged() {
        val broken = kotlin.metadata.jvm.Metadata(kind = 1, metadataVersion = intArrayOf(2, 4, 0), data1 = arrayOf("not protobuf"))
        val jar = jarOf("net.onelitefeather.ingot.search.Broken" to classWith("net/onelitefeather/ingot/search/Broken", broken))
        val unreadable = mutableListOf<String>()

        val internal = InternalClassFilter.internalClasses(jar, packages) { name, _ -> unreadable += name }

        assertThat(internal).describedAs("a class that cannot be classified is kept in the comparison").isEmpty()
        assertThat(unreadable).describedAs("the class is named, so the gap is visible").containsExactly("net.onelitefeather.ingot.search.Broken")
    }

    @Test
    fun javaClassWithoutMetadataIsKeptSilently() {
        val jar = jarOf("net.onelitefeather.ingot.search.JavaType" to classWith("net/onelitefeather/ingot/search/JavaType", null))
        val unreadable = mutableListOf<String>()

        val internal = InternalClassFilter.internalClasses(jar, packages) { name, _ -> unreadable += name }

        assertThat(internal).describedAs("a class without Kotlin metadata has no internal visibility").isEmpty()
        assertThat(unreadable).describedAs("missing metadata is not an error").isEmpty()
    }

    @Test
    fun classOutsideThePackagesIsNotListed() {
        val jar = jarOf("net.onelitefeather.ingot.storage.Hidden" to kotlinClass("net/onelitefeather/ingot/storage/Hidden", Visibility.INTERNAL))

        assertThat(internalClassesOf(jar)).describedAs("only the compared packages are read").isEmpty()
    }

    @Test
    fun memberWithDollarInItsNameIsExcluded() {
        assertThat(InternalClassFilter.isExcludedMember("index\$reposilite_backend"))
            .describedAs("Kotlin mangles an internal member with a '$'")
            .isTrue()
        assertThat(InternalClassFilter.isExcludedMember("index")).describedAs("a plain member is compared").isFalse()
    }

    private fun internalClassesOf(jar: File): List<String> =
        InternalClassFilter.internalClasses(jar, packages) { name, error -> throw AssertionError("unexpected unreadable $name", error) }

    private fun kotlinClass(kotlinName: String, visibility: Visibility): ByteArray {
        val kmClass = KmClass().apply {
            name = kotlinName
            this.visibility = visibility
        }
        val metadata = KotlinClassMetadata.Class(kmClass, JvmMetadataVersion.LATEST_STABLE_SUPPORTED, 0).write()
        return classWith(kotlinName.replace('.', '$'), metadata)
    }

    private fun classWith(internalName: String, metadata: Metadata?): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null)
        if (metadata != null) {
            writer.visitAnnotation("Lkotlin/Metadata;", true).apply {
                visit("k", metadata.kind)
                visit("mv", metadata.metadataVersion)
                visitArray("d1").apply { metadata.data1.forEach { visit(null, it) }; visitEnd() }
                visitArray("d2").apply { metadata.data2.forEach { visit(null, it) }; visitEnd() }
                visit("xi", metadata.extraInt)
                visitEnd()
            }
        }
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun jarOf(vararg classes: Pair<String, ByteArray>): File {
        val jar = tempDir.resolve("ingot.jar").toFile()
        JarOutputStream(jar.outputStream()).use { out ->
            classes.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name.replace('.', '/') + ".class"))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return jar
    }
}
