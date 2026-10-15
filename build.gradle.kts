import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    `java-library`
    application
    `maven-publish`

    val kotlinVersion = "2.4.0"
    kotlin("jvm") version kotlinVersion
    kotlin("kapt") version kotlinVersion
}

// The version is owned by Release Please, which rewrites the line below on every
// release. It replaced the axion-release-plugin, which derived the version from git
// tags: both wanted to own the same value, and the tag based approach also broke any
// build without full history, such as a shallow CI checkout or a source tarball.
//
// Ingot versions independently of Reposilite and started over at 1.0.0. This is not a
// downgrade from the 3.5.x the sources came from; see VERSIONING.md.
//
// Do not edit this by hand. The marker comment is what Release Please looks for.
val projectVersion = "1.1.0" // x-release-please-version

// Test JVM settings. A local run stays light; CI keeps what it had before, and the difference is
// deliberate and documented on each setting below.
//
// - `-PtestOutput=true` prints the standard streams of the test JVMs. CI always does.
// - `-Pcoverage=true` attaches the JaCoCo agent and writes the reports. CI always does, and so does
//   `testCoverage`, the aggregate that needs the execution data.
// - `integration` forks: `-PtestForks=N` or env `INGOT_TEST_FORKS` win, otherwise 4 locally and half the
//   cores in CI, as before. Every fork starts its own containers, so more forks is not free.
// - `test` forks: half the cores, capped at 8. Unit tests are cheap to run in parallel.
// - Test JVMs get a 1 GiB heap. Nothing here needs more, and it bounds the total when forks run side by side.
val isCi: Boolean = !System.getenv("CI").isNullOrEmpty()
val testOutputRequested: Boolean = findProperty("testOutput")?.toString()?.toBoolean() == true
val coverageRequested: Boolean =
    findProperty("coverage")?.toString()?.toBoolean() == true ||
        gradle.startParameter.taskNames.any { it == "testCoverage" || it.endsWith(":testCoverage") }
val coverageEnabled: Boolean = isCi || coverageRequested
val halfCores: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

fun integrationForks(): Int {
    val raw = findProperty("testForks")?.toString() ?: System.getenv("INGOT_TEST_FORKS")
    if (raw.isNullOrBlank()) return if (isCi) halfCores else 4
    return raw.trim().toIntOrNull()?.takeIf { it > 0 }
        ?: throw GradleException("testForks / INGOT_TEST_FORKS must be a positive integer, was '$raw'")
}

// Read by the reposilite-backend build, which owns the JaCoCo agent and report tasks.
extra["ingotTestCoverageEnabled"] = coverageEnabled

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "application")

    // Only the Maven coordinate moves to our namespace. The Kotlin and Java packages stay
    // com.reposilite.* on purpose: existing Reposilite plugins keep compiling against
    // Ingot, and migrating is a changed dependency line rather than a port. Code that gets
    // rewritten moves into net.onelitefeather.ingot.* at that point, not before.
    group = "net.onelitefeather.ingot"
    version = projectVersion

    repositories {
        mavenCentral()

        // Every repository beyond Maven Central is restricted to the groups it actually
        // serves. Without a content filter, Gradle asks each repository in turn for every
        // coordinate, so any one of them could answer for a dependency it has no business
        // serving: a compromised host, or an expired namespace someone re-registers, gets
        // to substitute an artifact that Central would otherwise have provided.
        //
        // com.reposilite:journalist is the one dependency that is not on Maven Central. It
        // is served only from the upstream maintainer's own infrastructure, which is a
        // single point of failure for our builds. Narrowing the repository does not remove
        // that dependency, it only stops the host from reaching any further.
        maven("https://maven.reposilite.com/releases") {
            mavenContent {
                releasesOnly()
                includeGroup("com.reposilite")
            }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            languageVersion = KotlinVersion.KOTLIN_2_3
            freeCompilerArgs = listOf("-jvm-default=no-compatibility") // For generating default methods in interfaces
        }
    }
}

subprojects {
    apply(plugin = "maven-publish")

    dependencies {
        // Capped at 4.6.0: unirest-modules-jackson 4.7.0+ migrated to Jackson 3 (tools.jackson.*); we're pinned to Jackson 2.x.
        val unirest = "4.6.0"
        testImplementation("com.konghq:unirest-java-core:$unirest")
        testImplementation("com.konghq:unirest-modules-jackson:$unirest")

        val assertJ = "4.0.0-M1"
        testImplementation("org.assertj:assertj-core:$assertJ")

        val junit = "6.1.0"
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junit")
        testImplementation("org.junit.jupiter:junit-jupiter-api:$junit")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:$junit")

        val junitPlatform = "6.1.0"
        testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitPlatform")
    }

    sourceSets.main {
        java.srcDirs("src/main/kotlin")
    }

    publishing {
        repositories {
            maven {
                name = "OneLiteFeatherRepository"
                url = if (version.toString().contains("SNAPSHOT")) {
                    uri("https://repo.onelitefeather.dev/onelitefeather-snapshots")
                } else {
                    uri("https://repo.onelitefeather.dev/onelitefeather-releases")
                }
                credentials {
                    username = System.getenv("ONELITEFEATHER_MAVEN_USERNAME") ?: providers.gradleProperty("mavenUser").orNull
                    password = System.getenv("ONELITEFEATHER_MAVEN_PASSWORD") ?: providers.gradleProperty("mavenPassword").orNull
                }
            }
        }

        publications {
            create<MavenPublication>("library") {
                from(components.getByName("java"))
                // Gradle generator does not support <repositories> section from Maven specification.
                // ~ https://github.com/gradle/gradle/issues/15932
                pom.withXml {
                    val repositories = asNode().appendNode("repositories")
                    project.repositories
                        .filterIsInstance<MavenArtifactRepository>()
                        .filter { it.url.toString().startsWith("https") }
                        .forEach { repo ->
                            val repository = repositories.appendNode("repository")
                            repository.appendNode("id", repo.url.toString().replace("https://", "").replace(".", "-").replace("/", "-"))
                            repository.appendNode("url", repo.url.toString())
                        }
                }
            }
        }
    }

    tasks.withType<Test> {
        testLogging {
            events(
                TestLogEvent.STARTED,
                TestLogEvent.PASSED,
                TestLogEvent.FAILED,
                TestLogEvent.SKIPPED
            )
            exceptionFormat = TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
            showStandardStreams = testOutputRequested || isCi
        }

        maxParallelForks = if (name == "integration") integrationForks() else halfCores.coerceAtMost(8)
        maxHeapSize = "1g"

        useJUnitPlatform()
    }
}
