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

import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar

/**
 * Stamps the jar and the shadow jar of a plugin module with `Implementation-Title` and `Implementation-Version`.
 *
 * A plugin jar embedded into the server has to say which version it is, so every plugin module gets the attributes from
 * one place instead of each build script writing its own. Sources, javadoc and test fixture jars keep their manifest.
 */
object PluginJarManifest {

    private val STAMPED_TASKS = setOf("jar", "shadowJar")

    fun apply(project: Project) {
        project.tasks.withType(Jar::class.java)
            .matching { it.name in STAMPED_TASKS }
            .configureEach {
                manifest.attributes(
                    mapOf(
                        "Implementation-Title" to project.name,
                        "Implementation-Version" to project.version.toString(),
                    ),
                )
            }
    }
}
