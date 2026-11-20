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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.4.2"
}

// The javadoc viewer is a release unit of its own, so the module has its own version and does not follow the server version.
// The server jar embeds the shadow jar as embedded-plugins/javadoc-plugin.jar, see the embedded plugin jars of reposilite-backend.
group = "net.onelitefeather.ingot"
version = "1.0.0" // x-release-please-version

dependencies {
    compileOnly(project(":reposilite-backend"))

    testImplementation(project(":reposilite-backend"))
    testImplementation(testFixtures(project(":reposilite-backend")))
}

// The settings schema of JavadocSettings is generated at build time, as the server generates its own, and the api document
// fragment goes to META-INF/ingot/openapi/javadoc.json. The processor indexes are left out, so the server's stay the only ones.
ingot.plugin.PluginApiFragments.apply(project, "javadoc")

tasks.withType<ShadowJar> {
    archiveFileName.set("javadoc-plugin.jar")
    mergeServiceFiles()
    // The loader of embedded and plugins-directory jars reads this attribute to decide which copy of the plugin wins.
    manifest {
        attributes("Implementation-Version" to project.version.toString())
    }
}

publishing {
    publications {
        named<MavenPublication>("library") {
            setArtifacts(emptyList<Any>())
            artifact(tasks.named("shadowJar")) {
                classifier = null
                extension = "jar"
            }
            pom {
                name.set("javadoc-plugin")
                description.set("Ingot plugin that serves the javadoc, groovydoc and other documentation archives of artifacts")
            }
            pom.withXml {
                val root = asNode()
                root.children()
                    .filterIsInstance<groovy.util.Node>()
                    .filter { it.name().toString().substringAfterLast("}") in setOf("dependencies", "dependencyManagement") }
                    .forEach { root.remove(it) }
            }
        }
    }
}

// The module metadata lists the server as a dependency, which the plugin loader already provides. Only the POM is published.
tasks.matching { it.name.startsWith("generateMetadataFileFor") }.configureEach { enabled = false }
