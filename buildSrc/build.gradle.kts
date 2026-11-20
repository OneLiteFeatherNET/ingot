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

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Reads the class files of the slim plugin API jar, see ingot.plugin.PluginApiClosure.
    implementation("org.ow2.asm:asm:9.9.1")
    implementation("org.ow2.asm:asm-tree:9.9.1")
    // Reads the Kotlin visibility of the Ingot plugin API classes, see ingot.plugin.InternalClassFilter. It matches the Kotlin
    // version Gradle embeds and reads newer metadata leniently.
    implementation("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.20")

    // The versions match the test dependencies of the project modules.
    testImplementation("org.assertj:assertj-core:4.0.0-M1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.0")
}

tasks.test {
    useJUnitPlatform()
}

// No plugin dependencies. The axion-release-plugin was removed together with the git
// tag based versioning; Release Please owns the version now.
