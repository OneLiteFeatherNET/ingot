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

// The command line client for a running Ingot. It shares no code with the server: the JSON of the console API is the contract.
plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.4.2"
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jline:jline:3.30.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.7")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.7")
}

application {
    mainClass = "net.onelitefeather.ingot.cli.MainKt"
}

// One runnable jar, ingot-cli-<version>.jar, with its dependencies. The release attaches it next to the image.
tasks.shadowJar {
    archiveBaseName = "ingot-cli"
    archiveClassifier = ""
    archiveVersion = project.version.toString()
    mergeServiceFiles()
}

tasks.jar {
    enabled = false
}

// The unit tests run in process. The smoke tests tagged "jar" start the shaded jar as a process of its own, so they run
// in a task of their own that builds the jar first, and `check` runs both.
tasks.test {
    useJUnitPlatform {
        excludeTags("jar")
    }
}

val jarSmokeTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the shaded jar with java -jar against a stub server."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("jar")
    }
    val shadedJar = tasks.shadowJar.flatMap { it.archiveFile }
    inputs.file(shadedJar)
    doFirst {
        systemProperty("ingot.cli.jar", shadedJar.get().asFile.absolutePath)
    }
}

tasks.check {
    dependsOn(jarSmokeTest)
}

// The root build gives every subproject a Maven publication of its plain jar, which this project does not build. The
// cli ships as the jar above and as an image, and it was never published to a Maven repository, so it is left out.
tasks.withType<AbstractPublishToMaven>().configureEach {
    enabled = false
}


// The start scripts and the distributions copy the shaded jar, so they must run after it. Without these edges Gradle
// reports an implicit dependency and the build fails.
tasks.startScripts {
    dependsOn(tasks.shadowJar)
}

tasks.distZip {
    dependsOn(tasks.shadowJar)
}

tasks.distTar {
    dependsOn(tasks.shadowJar)
}
