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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

// Groups the findings of the japicmp comparison into promised and experimental and writes the Markdown report. The annotations are
// read from the jar of this build and from the baseline jar.
abstract class IngotPluginApiReportTask : DefaultTask() {

    // The XML report of the japicmp comparison.
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val comparison: RegularFileProperty

    @get:Classpath
    abstract val newArchives: ConfigurableFileCollection

    @get:Classpath
    abstract val oldArchives: ConfigurableFileCollection

    @get:Input
    abstract val experimentalPackages: ListProperty<String>

    // How the report names what it compares with, for example the baseline coordinate.
    @get:Input
    abstract val baseline: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun write() {
        val classes = comparison.get().asFile.inputStream().use(JapicmpComparison::parse)
        val required = IngotPluginApiReport.requiredClasses(classes)
        val markdown = IngotPluginApiReport.render(
            classes = classes,
            newMarkers = TypeMarkers.fromArchives(newArchives, required),
            oldMarkers = TypeMarkers.fromArchives(oldArchives, required),
            experimentalPackages = experimentalPackages.get(),
            baseline = baseline.get(),
        )
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(markdown)
        logger.lifecycle("Wrote the Ingot plugin API report to ${report.get().asFile}")
    }
}
