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
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

// The server reads Implementation-Version from an embedded plugin jar, so every plugin module stamps its jar and its
// shadow jar. Other archives of the module keep the manifest they had.
class PluginJarManifestTest {

    @TempDir
    lateinit var tempDir: File

    private fun pluginModule(): Project {
        val project = ProjectBuilder.builder().withName("fixture-plugin").withProjectDir(tempDir).build()
        project.version = "1.2.3"
        project.pluginManager.apply("java")
        // Stands in for the task of the shadow plugin, which every module applies with its own version.
        project.tasks.register("shadowJar", Jar::class.java)
        project.tasks.register("sourcesJar", Jar::class.java)
        PluginJarManifest.apply(project)
        return project
    }

    private fun attributes(project: Project, task: String): Map<String, Any?> =
        (project.tasks.getByName(task) as Jar).manifest.attributes

    @Test
    fun jarCarriesImplementationVersionAndTitle() {
        val attributes = attributes(pluginModule(), "jar")

        assertThat(attributes)
            .describedAs("the plain jar names the module and its version")
            .containsEntry("Implementation-Version", "1.2.3")
            .containsEntry("Implementation-Title", "fixture-plugin")
    }

    @Test
    fun shadowJarCarriesImplementationVersionAndTitle() {
        val attributes = attributes(pluginModule(), "shadowJar")

        assertThat(attributes)
            .describedAs("the shadow jar is the one that gets embedded and loaded, so it carries the version too")
            .containsEntry("Implementation-Version", "1.2.3")
            .containsEntry("Implementation-Title", "fixture-plugin")
    }

    @Test
    fun otherArchivesAreLeftAlone() {
        val attributes = attributes(pluginModule(), "sourcesJar")

        assertThat(attributes)
            .describedAs("only the jar and the shadow jar are stamped")
            .doesNotContainKeys("Implementation-Version", "Implementation-Title")
    }
}
