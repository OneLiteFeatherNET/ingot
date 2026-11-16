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
    id("com.coditory.integration-test") version "2.2.5"
    id("com.gradleup.shadow") version "9.4.2"
}

// The Azure SDK versions come from one BOM, so every Azure artifact moves together. The BOM is the newest GA release at the time of
// writing, and its storage, identity and JDK HTTP client versions are all GA. A beta is never pinned.
val azureSdkBom = "1.3.10"

dependencies {
    compileOnly(project(":reposilite-backend"))
    // The server's Jackson: compile against the version the server ships, the plugin loader resolves it to the server copy at run time.
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.21.7")

    implementation(platform("com.azure:azure-sdk-bom:$azureSdkBom"))
    implementation("com.azure:azure-storage-blob")
    implementation("com.azure:azure-identity")
    // The JDK HTTP client is GA, so the Netty client that the Azure SDK declares is left out, see the configuration below.
    implementation("com.azure:azure-core-http-jdk-httpclient")

    testImplementation(project(":reposilite-backend"))
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.7")

    // The container tests run against Azurite in the integration source set. They need the server classes at run time, and the
    // container and assertion libraries that the unit tests already have.
    "integrationImplementation"(project(":reposilite-backend"))
    "integrationImplementation"("org.testcontainers:testcontainers:2.0.5")
    "integrationImplementation"("org.junit.jupiter:junit-jupiter-api:6.1.0")
    "integrationImplementation"("org.assertj:assertj-core:4.0.0-M1")
    "integrationRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:6.1.0")
    "integrationRuntimeOnly"("org.junit.platform:junit-platform-launcher:6.1.0")
}

// The Azure SDK declares Netty as its default HTTP client. The JDK client is used instead, so no second HTTP stack reaches the jar.
configurations.configureEach {
    exclude(group = "com.azure", module = "azure-core-http-netty")
}

// The libraries of the Azure SDK that can clash with the server, or with another plugin, live under a package of their own. The
// server's Jackson is not in this jar at all (it is compileOnly), so relocating the copy of the SDK does not touch it.
val shadedPackage = "net.onelitefeather.ingot.storage.azure.shaded"

// Not part of the image. The jar is installed by the operator into the plugins directory, the same as the other optional plugins.
tasks.withType<ShadowJar> {
    archiveFileName.set("azure-storage-plugin.jar")
    destinationDirectory.set(file("$rootDir/reposilite-test/workspace/plugins"))
    mergeServiceFiles()
    relocate("reactor", "$shadedPackage.reactor")
    relocate("org.reactivestreams", "$shadedPackage.reactivestreams")
    relocate("com.fasterxml.jackson", "$shadedPackage.jackson")

    // The size is printed so that the figure in the documentation can be checked on each SDK bump.
    doLast {
        logger.lifecycle("azure-storage-plugin.jar: ${archiveFile.get().asFile.length()} bytes")
    }
}

// The integration suite reads the shaded jar as an artifact, so the jar is built first and its path is handed to the tests.
tasks.named<Test>("integration") {
    val shadedJar = tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile }
    dependsOn("shadowJar")
    doFirst {
        systemProperty("ingot.azure.jar", shadedJar.get().asFile.absolutePath)
    }
}
