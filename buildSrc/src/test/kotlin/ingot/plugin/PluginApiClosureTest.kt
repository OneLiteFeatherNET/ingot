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
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

// Every test compiles its own Java fixtures into a temporary directory. Only the listed classes that were compiled go into the
// artifact map, as the slim jar would hold them. The other fixtures exist so that javac can resolve the signatures.
class PluginApiClosureTest {

    @TempDir
    lateinit var tempDir: Path

    private val api = "net.onelitefeather.ingot.plugin.Api"
    private val helper = "net.onelitefeather.ingot.plugin.Helper"
    private val hidden = "net.onelitefeather.ingot.search.Hidden"
    private val legacy = "com.reposilite.plugin.api.ReposilitePlugin"
    private val journal = "com.reposilite.journalist.Journal"
    private val missing = "net.onelitefeather.ingot.plugin.Missing"

    @Test
    fun allowlistedClassWithClosedSignaturesPasses() {
        val sources = mapOf(
            helper to "package net.onelitefeather.ingot.plugin; public class Helper {}",
            api to """
                package net.onelitefeather.ingot.plugin;
                import java.util.List;
                public class Api {
                    public Helper helper(List<Helper> helpers) { return null; }
                    public String name() { return "api"; }
                }
            """,
        )

        assertThat(closureOf(sources, listOf(api, helper)))
            .describedAs("public signatures that name only listed classes and JDK types are closed")
            .isEmpty()
    }

    @Test
    fun publicSignatureReferenceToUnlistedIngotTypeFails() {
        val sources = mapOf(
            hidden to "package net.onelitefeather.ingot.search; public class Hidden {}",
            api to """
                package net.onelitefeather.ingot.plugin;
                import net.onelitefeather.ingot.search.Hidden;
                public class Api { public Hidden hidden() { return null; } }
            """,
        )

        val violations = closureOf(sources, listOf(api))

        assertThat(violations).describedAs("one unlisted public reference is one violation").hasSize(1)
        assertThat(violations.single())
            .describedAs("the violation names the referring class, the member and the missing target")
            .contains(api, "method hidden", hidden)
        assertThat(violations.single())
            .describedAs("the violation tells the author where the fix belongs")
            .contains("plugin-api-classes.txt")
    }

    @Test
    fun referenceInsideGenericSignatureFails() {
        val sources = mapOf(
            hidden to "package net.onelitefeather.ingot.search; public class Hidden {}",
            api to """
                package net.onelitefeather.ingot.plugin;
                import java.util.List;
                import net.onelitefeather.ingot.search.Hidden;
                public class Api { public List<Hidden> hidden() { return null; } }
            """,
        )

        val violations = closureOf(sources, listOf(api))

        assertThat(violations).describedAs("a type argument of a public signature is part of the signature").hasSize(1)
        assertThat(violations.single()).contains(hidden)
    }

    @Test
    fun referenceInPrivateMemberPasses() {
        val sources = mapOf(
            hidden to "package net.onelitefeather.ingot.search; public class Hidden {}",
            api to """
                package net.onelitefeather.ingot.plugin;
                import net.onelitefeather.ingot.search.Hidden;
                public class Api {
                    private Hidden cache;
                    private Hidden lookup() { return cache; }
                }
            """,
        )

        assertThat(closureOf(sources, listOf(api)))
            .describedAs("a private member is not part of the signature a plugin compiles against")
            .isEmpty()
    }

    @Test
    fun referenceToLegacyReposiliteTypePasses() {
        val sources = mapOf(
            legacy to "package com.reposilite.plugin.api; public abstract class ReposilitePlugin {}",
            api to """
                package net.onelitefeather.ingot.plugin;
                import com.reposilite.plugin.api.ReposilitePlugin;
                public class Api { public ReposilitePlugin plugin() { return null; } }
            """,
        )

        assertThat(closureOf(sources, listOf(api)))
            .describedAs("com.reposilite types are provided by the server, so the check does not require them")
            .isEmpty()
    }

    @Test
    fun referenceToJournalistTypePasses() {
        val sources = mapOf(
            journal to "package com.reposilite.journalist; public class Journal {}",
            api to """
                package net.onelitefeather.ingot.plugin;
                import com.reposilite.journalist.Journal;
                public class Api { public void log(Journal journal) {} }
            """,
        )

        assertThat(closureOf(sources, listOf(api)))
            .describedAs("the journalist library is provided by the server, so the check does not require it")
            .isEmpty()
    }

    @Test
    fun listedClassMissingFromBuildOutputFails() {
        val sources = mapOf(
            api to "package net.onelitefeather.ingot.plugin; public class Api {}",
        )

        val violations = closureOf(sources, listOf(api, missing))

        assertThat(violations).describedAs("a listed class that was not compiled is a violation").hasSize(1)
        assertThat(violations.single())
            .describedAs("the violation names the listed class")
            .contains(missing, "not in the build output")
    }

    // Compiles the fixtures and returns the violations of an artifact that holds the listed classes.
    private fun closureOf(sources: Map<String, String>, listed: List<String>): List<String> =
        PluginApiClosure.violations(artifactOf(sources, listed), listed)

    // Compiles the fixtures into a fresh directory and returns the class files of the listed classes that were compiled,
    // keyed by internal name, as the slim jar holds them.
    private fun artifactOf(sources: Map<String, String>, listed: List<String>): Map<String, ByteArray> {
        val out = tempDir.resolve("classes").createDirectories()
        val files = sources.map { (name, text) ->
            tempDir.resolve("src").resolve(name.replace('.', '/') + ".java").apply {
                parent.createDirectories()
                writeText(text.trimIndent())
            }
        }
        val status = ToolProvider.getSystemJavaCompiler().run(
            null, null, null,
            "-d", out.toString(), "-proc:none",
            *files.map(Path::toString).toTypedArray(),
        )
        check(status == 0) { "the fixture sources do not compile" }

        return listed
            .map { it.replace('.', '/') }
            .filter { out.resolve("$it.class").isRegularFile() }
            .associateWith { out.resolve("$it.class").readBytes() }
    }
}
