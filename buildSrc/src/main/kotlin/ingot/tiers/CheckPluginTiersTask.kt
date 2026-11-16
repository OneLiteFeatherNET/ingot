/*
 * Copyright (c) 2023 dzikoysk
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

package ingot.tiers

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

// Fails when a plugin module declares a tier its sources do not back up. The tier file is written first, in every case,
// so a failed run still leaves the declarations it read.
abstract class CheckPluginTiersTask : DefaultTask() {

    @get:Internal
    abstract val pluginsDirectory: DirectoryProperty

    @get:OutputFile
    abstract val tierFile: RegularFileProperty

    init {
        // The check reads source files that Gradle does not track as inputs, so it always runs. It takes well under a second.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun check() {
        val checks = PluginTierCheck.scan(pluginsDirectory.get().asFile.toPath())

        tierFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(PluginTierCheck.tierJson(checks))
        }

        checks.filter { it.declared == null }.forEach { check ->
            logger.lifecycle("${check.module}: no @Plugin class, so no tier is declared")
        }

        val violations = PluginTierCheck.violations(checks)
        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString(separator = "\n", prefix = "Plugin tier check failed:\n") { "  - $it" })
        }
    }
}
