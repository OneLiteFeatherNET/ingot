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
    `java-test-fixtures`
}

dependencies {
    compileOnly(project(":reposilite-backend"))

    testImplementation(project(":reposilite-backend"))
    testImplementation(testFixtures(project(":reposilite-backend")))
    testImplementation("org.testcontainers:testcontainers:2.0.5")
}

tasks.withType<ShadowJar> {
    archiveFileName.set("opensearch-plugin.jar")
    destinationDirectory.set(file("$rootDir/reposilite-test/workspace/plugins"))
    mergeServiceFiles()
}
