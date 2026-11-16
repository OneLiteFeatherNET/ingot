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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.4.0"

    id("com.gradleup.shadow") version "9.4.2"
}

application {
    mainClass.set("com.reposilite.plugin.migration.MigrationPlugin")
}

dependencies {
    compileOnly(project(":reposilite-backend"))
    testImplementation(project(":reposilite-backend"))
    implementation("com.charleskorn.kaml:kaml-jvm:0.104.0")
}

tasks.withType<ShadowJar> {
    archiveFileName.set("migration-plugin.jar")
    destinationDirectory.set(file("$rootDir/reposilite-test/workspace/plugins"))
    mergeServiceFiles()
}

// The contract test boots a server with the shipped jar of this plugin and checks what the plugin promises. Its source set
// does not see the classes of this module, or the server would load the plugin twice: the jar is the only copy it gets.
val contractTest: SourceSet by sourceSets.creating
configurations.named(contractTest.implementationConfigurationName) {
    extendsFrom(configurations.getByName("testImplementation"))
}
configurations.named(contractTest.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.getByName("testRuntimeOnly"))
}
dependencies {
    // The fixture that boots the server, shared with the other plugin contract tests.
    add(contractTest.implementationConfigurationName, testFixtures(project(":reposilite-backend")))
}

val shadowJar = tasks.named<ShadowJar>("shadowJar")

tasks.register<Test>("contractTest") {
    group = "verification"
    description = "Boots a fresh server with the shipped migration-plugin jar and checks the behaviour the plugin promises."
    testClassesDirs = contractTest.output.classesDirs
    classpath = contractTest.runtimeClasspath
    useJUnitPlatform()
    dependsOn(shadowJar)
    systemProperty("ingot.contract.plugin", shadowJar.get().archiveFile.get().asFile.absolutePath)
    shouldRunAfter(tasks.named("test"))
}
