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

dependencies {
    compileOnly(project(":reposilite-backend"))
    // The server's Jackson: compile against the version the server ships, the plugin loader resolves it to the server copy at run time.
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.21.7")

    testImplementation(project(":reposilite-backend"))
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.7")
    testImplementation(testFixtures(project(":reposilite-backend")))
}

// Not part of the image. The jar is installed by the operator, and its presence in the plugins directory is its own switch.
tasks.withType<ShadowJar> {
    archiveFileName.set("elasticsearch-plugin.jar")
    destinationDirectory.set(file("$rootDir/reposilite-test/workspace/plugins"))
    mergeServiceFiles()
}
