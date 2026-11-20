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
import java.nio.file.Path
import javax.tools.ToolProvider
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

class IngotPluginApiReportTest {

    @TempDir
    lateinit var tempDir: Path

    private val experimentalPackages = listOf("net.onelitefeather.ingot.search.backend")

    // The shape japicmp 0.23.1 writes with onlyBinaryIncompatibleModified, trimmed to the elements the report reads.
    private val comparison = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <japicmp>
            <classes>
                <class binaryCompatible="false" changeStatus="MODIFIED" fullyQualifiedName="com.reposilite.frontend.api.PluginUiRegistrar">
                    <annotations/>
                    <compatibilityChanges/>
                    <constructors/>
                    <fields>
                        <field binaryCompatible="false" changeStatus="REMOVED" name="LIMIT">
                            <compatibilityChanges>
                                <compatibilityChange binaryCompatible="false" sourceCompatible="false" type="FIELD_REMOVED"/>
                            </compatibilityChanges>
                        </field>
                    </fields>
                    <interfaces/>
                    <methods>
                        <method binaryCompatible="false" changeStatus="REMOVED" name="register">
                            <compatibilityChanges>
                                <compatibilityChange binaryCompatible="false" sourceCompatible="false" type="METHOD_REMOVED"/>
                            </compatibilityChanges>
                            <parameters>
                                <parameter binaryCompatible="true" changeStatus="UNCHANGED" type="java.lang.String"><compatibilityChanges/></parameter>
                                <parameter binaryCompatible="true" changeStatus="UNCHANGED" type="int"><compatibilityChanges/></parameter>
                            </parameters>
                            <returnType binaryCompatible="true" changeStatus="REMOVED"><compatibilityChanges/></returnType>
                        </method>
                        <method binaryCompatible="false" changeStatus="MODIFIED" name="preview">
                            <compatibilityChanges>
                                <compatibilityChange binaryCompatible="false" sourceCompatible="false" type="METHOD_RETURN_TYPE_CHANGED"/>
                                <compatibilityChange binaryCompatible="true" sourceCompatible="true" type="ANNOTATION_ADDED"/>
                            </compatibilityChanges>
                            <parameters/>
                        </method>
                    </methods>
                    <superclass binaryCompatible="false" changeStatus="MODIFIED">
                        <compatibilityChanges>
                            <compatibilityChange binaryCompatible="false" sourceCompatible="false" type="SUPERCLASS_REMOVED"/>
                        </compatibilityChanges>
                    </superclass>
                </class>
                <class binaryCompatible="false" changeStatus="REMOVED" fullyQualifiedName="net.onelitefeather.ingot.repository.Gone${'$'}Nested">
                    <compatibilityChanges>
                        <compatibilityChange binaryCompatible="false" sourceCompatible="false" type="CLASS_REMOVED"/>
                    </compatibilityChanges>
                    <constructors>
                        <constructor binaryCompatible="false" changeStatus="REMOVED" name="Gone${'$'}Nested">
                            <compatibilityChanges>
                                <compatibilityChange binaryCompatible="false" sourceCompatible="false" type="CONSTRUCTOR_REMOVED"/>
                            </compatibilityChanges>
                            <parameters/>
                        </constructor>
                    </constructors>
                    <methods/>
                </class>
            </classes>
        </japicmp>
    """.trimIndent()

    @Test
    fun comparisonReadsTypeAndMemberChanges() {
        val classes = JapicmpComparison.parse(comparison.byteInputStream())

        assertThat(classes.map { it.className })
            .describedAs("every class japicmp lists is read, nested classes keep their binary name")
            .containsExactly("com.reposilite.frontend.api.PluginUiRegistrar", "net.onelitefeather.ingot.repository.Gone\$Nested")
        val registrar = classes.first()
        assertThat(registrar.typeChanges)
            .describedAs("a change of the superclass belongs to the type, not to a member")
            .containsExactly("SUPERCLASS_REMOVED")
        assertThat(registrar.members.map { "${it.label}: ${it.changes}" })
            .describedAs("each member with an incompatible change is read with its signature, binary compatible changes are dropped")
            .containsExactlyInAnyOrder("LIMIT: [FIELD_REMOVED]", "register(java.lang.String, int): [METHOD_REMOVED]", "preview(): [METHOD_RETURN_TYPE_CHANGED]")
    }

    @Test
    fun removedClassIsReportedWithoutItsMembers() {
        val removed = JapicmpComparison.parse(comparison.byteInputStream()).last()

        assertThat(removed.typeChanges).describedAs("the removal of the class is the finding").containsExactly("CLASS_REMOVED")
        assertThat(removed.members).describedAs("the members of a removed class add nothing to its removal").isEmpty()
    }

    @Test
    fun memberWithAMangledInternalNameIsDropped() {
        val xml = comparison.replace("name=\"preview\"", "name=\"preview\$reposilite_backend\"")

        val registrar = JapicmpComparison.parse(xml.byteInputStream()).first()

        assertThat(registrar.members.map { it.name })
            .describedAs("Kotlin gives an internal member a name with a '$', and an internal member is no plugin API")
            .containsExactlyInAnyOrder("LIMIT", "register")
    }

    @Test
    fun unmarkedFindingsArePromisedAndCounted() {
        val report = render(newMarkers = emptyMap(), oldMarkers = emptyMap())

        assertThat(sectionOf(report, "Promised"))
            .describedAs("both classes are promised, each one top-level bullet with its changes nested")
            .contains("2 findings.")
            .contains("- `com.reposilite.frontend.api.PluginUiRegistrar`")
            .contains("  - `register(java.lang.String, int)`: METHOD_REMOVED")
            .contains("  - type: SUPERCLASS_REMOVED")
            .contains("- `net.onelitefeather.ingot.repository.Gone\$Nested`")
        assertThat(sectionOf(report, "Experimental")).describedAs("nothing is marked").contains("No findings.")
    }

    @Test
    fun annotatedMemberIsExperimentalWhileTheRestOfItsTypeStaysPromised() {
        val markers = mapOf(
            "com.reposilite.frontend.api.PluginUiRegistrar" to TypeMarkers(
                annotations = emptySet(),
                memberAnnotations = mapOf("preview" to setOf("ExperimentalIngotPluginApi")),
            ),
        )

        val report = render(newMarkers = markers, oldMarkers = markers)

        assertThat(sectionOf(report, "Experimental"))
            .describedAs("the member marked ExperimentalIngot* is listed under its type in the experimental section")
            .contains("1 finding.")
            .contains("- `com.reposilite.frontend.api.PluginUiRegistrar`")
            .contains("  - `preview()`: METHOD_RETURN_TYPE_CHANGED")
            .doesNotContain("register")
        assertThat(sectionOf(report, "Promised"))
            .describedAs("the unmarked members and the type change stay promised")
            .contains("`register(java.lang.String, int)`", "type: SUPERCLASS_REMOVED")
            .doesNotContain("preview")
    }

    @Test
    fun typeMarkedOnlyInTheNewJarIsReportedOnceWithANote() {
        val marked = TypeMarkers(annotations = setOf("ExperimentalIngotPluginApi"), memberAnnotations = emptyMap())
        val unmarked = TypeMarkers(annotations = emptySet(), memberAnnotations = emptyMap())
        val name = "com.reposilite.frontend.api.PluginUiRegistrar"

        val report = render(newMarkers = mapOf(name to marked), oldMarkers = mapOf(name to unmarked))

        assertThat(sectionOf(report, "Experimental"))
            .describedAs("a type that gains the marker is experimental as a whole and says so")
            .contains("- `$name` (marked experimental in this build)")
        assertThat(sectionOf(report, "Promised"))
            .describedAs("the type is reported once, so none of its changes stay promised")
            .doesNotContain(name)
    }

    @Test
    fun annotationOfTheEnclosingTypeCoversANestedType() {
        val markers = mapOf(
            "net.onelitefeather.ingot.repository.Gone" to TypeMarkers(setOf("ExperimentalIngotFormatApi"), emptyMap()),
        )

        val report = render(newMarkers = emptyMap(), oldMarkers = markers)

        assertThat(sectionOf(report, "Experimental"))
            .describedAs("a nested type of a marked type is experimental, and the baseline marker counts for a removed type")
            .contains("- `net.onelitefeather.ingot.repository.Gone\$Nested`")
            .doesNotContain("marked experimental in this build")
    }

    @Test
    fun experimentalPackageMakesItsTypesExperimental() {
        val xml = comparison.replace("net.onelitefeather.ingot.repository.", "net.onelitefeather.ingot.search.backend.")

        val report = IngotPluginApiReport.render(
            JapicmpComparison.parse(xml.byteInputStream()), emptyMap(), emptyMap(), experimentalPackages, "baseline 1.1.1",
        )

        assertThat(sectionOf(report, "Experimental"))
            .describedAs("a type of a listed experimental package is experimental without an annotation")
            .contains("- `net.onelitefeather.ingot.search.backend.Gone\$Nested`")
    }

    @Test
    fun markersAreReadFromInvisibleAnnotationsOfClassFiles() {
        val bytes = compile(
            "net.onelitefeather.ingot.plugin.Api",
            """
                package net.onelitefeather.ingot.plugin;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.CLASS) @interface ExperimentalIngotPluginApi {}
                @Deprecated @ExperimentalIngotPluginApi
                public class Api {
                    @ExperimentalIngotPluginApi public void preview() {}
                    public void stable() {}
                }
            """,
        )

        val markers = TypeMarkers.read(bytes)

        assertThat(markers.annotations)
            .describedAs("binary retention annotations, as Kotlin writes ExperimentalIngot*, are read by simple name")
            .containsExactlyInAnyOrder("Deprecated", "ExperimentalIngotPluginApi")
        assertThat(markers.memberAnnotations)
            .describedAs("member annotations are keyed by member name")
            .containsEntry("preview", setOf("ExperimentalIngotPluginApi"))
            .doesNotContainKey("stable")
    }

    private fun render(newMarkers: Map<String, TypeMarkers>, oldMarkers: Map<String, TypeMarkers>): String =
        IngotPluginApiReport.render(
            JapicmpComparison.parse(comparison.byteInputStream()), newMarkers, oldMarkers, experimentalPackages, "baseline 1.1.1",
        )

    private fun sectionOf(report: String, heading: String): String =
        report.substringAfter("## $heading\n").substringBefore("\n## ")

    private fun compile(name: String, source: String): ByteArray {
        val file = tempDir.resolve("src").resolve(name.replace('.', '/') + ".java").apply {
            parent.createDirectories()
            writeText(source.trimIndent())
        }
        val out = tempDir.resolve("classes").createDirectories()
        val status = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", out.toString(), "-proc:none", file.toString())
        check(status == 0) { "the fixture source does not compile" }
        return out.resolve(name.replace('.', '/') + ".class").readBytes()
    }
}
