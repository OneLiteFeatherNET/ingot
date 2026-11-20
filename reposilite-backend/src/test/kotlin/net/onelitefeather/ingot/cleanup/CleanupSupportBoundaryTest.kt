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

package net.onelitefeather.ingot.cleanup

import com.reposilite.maven.MavenFacade
import com.reposilite.scheduling.infrastructure.SqlScheduledJobClaims
import com.tngtech.archunit.core.importer.ClassFileImporter
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions

/**
 * The cleanup code reaches the mirror cache and the job claims only through [CleanupSupportFacade], and both core
 * internals stay internal, so that neither becomes plugin API by accident (extract-cleanup-plugin, design D14).
 */
internal class CleanupSupportBoundaryTest {

    private companion object {
        const val CLEANUP_SOURCES = "src/main/kotlin/com/reposilite/cleanup"
        const val PLUGIN_API_CLASSES = "plugin-api-classes.txt"
        val MAVEN_FACADE_MIRROR_CALL = Regex("""\b\w*[mM]aven\w*\.mirrorCacheEntries\(""")
    }

    private fun cleanupSources(): List<File> =
        File(CLEANUP_SOURCES).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `should mark the interface as experimental plugin api`() {
        // The marker has binary retention, so it is read from the class file rather than through reflection.
        val type = ClassFileImporter().importClass(CleanupSupportFacade::class.java)

        assertThat(type.isAnnotatedWith(ExperimentalIngotPluginApi::class.java))
            .`as`("CleanupSupportFacade carries @ExperimentalIngotPluginApi in its class file")
            .isTrue()
    }

    @Test
    fun `should list the interface in the plugin api classes`() {
        val lines = File(PLUGIN_API_CLASSES).readLines().map { it.trim() }

        assertThat(lines)
            .`as`("lines of $PLUGIN_API_CLASSES")
            .contains(CleanupSupportFacade::class.java.name)
    }

    @Test
    fun `should not call the mirror cache entries of the maven facade from the cleanup code`() {
        val sources = cleanupSources()
        assertThat(sources).`as`("kotlin sources under $CLEANUP_SOURCES").isNotEmpty()

        val callers = sources.filter { MAVEN_FACADE_MIRROR_CALL.containsMatchIn(it.readText()) }.map { it.name }

        assertThat(callers).`as`("cleanup sources that call MavenFacade.mirrorCacheEntries").isEmpty()
    }

    @Test
    fun `should not name the sql claim store in the cleanup code`() {
        val sources = cleanupSources()
        assertThat(sources).`as`("kotlin sources under $CLEANUP_SOURCES").isNotEmpty()

        val users = sources.filter { it.readText().contains("SqlScheduledJobClaims") }.map { it.name }

        assertThat(users).`as`("cleanup sources that name SqlScheduledJobClaims").isEmpty()
    }

    @Test
    fun `should keep the mirror cache entries of the maven facade internal`() {
        val function = MavenFacade::class.declaredMemberFunctions.single { it.name == "mirrorCacheEntries" }

        assertThat(function.visibility).`as`("visibility of MavenFacade.mirrorCacheEntries").isEqualTo(KVisibility.INTERNAL)
    }

    @Test
    fun `should keep the sql claim store internal`() {
        assertThat(SqlScheduledJobClaims::class.visibility).`as`("visibility of SqlScheduledJobClaims").isEqualTo(KVisibility.INTERNAL)
    }

}
