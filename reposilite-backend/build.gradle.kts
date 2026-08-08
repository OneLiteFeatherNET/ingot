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

//import io.gitlab.arturbosch.detekt.Detekt
//import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.ReportRenderer
import com.github.jk1.license.render.JsonReportRenderer
import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs

plugins {
    jacoco
    kotlin("jvm")
    kotlin("kapt")
    id("com.coditory.integration-test") version "2.2.5"
    id("com.gradleup.shadow") version "9.4.2"
    id("com.github.jk1.dependency-license-report") version "3.1.4"
//    id("io.gitlab.arturbosch.detekt").version("1.22.0")
}

application {
    mainClass.set("com.reposilite.ReposiliteLauncherKt")
}

dependencies {
    implementation(project(":reposilite-frontend"))

    // Transitive dependencies that ship in the fat jar and reach the network, pinned to
    // versions without known advisories. Neither is declared directly: netty arrives with
    // the AWS SDK's netty-nio-client and jetty with javalin-ssl, so a platform is the only
    // way to raise them without waiting for those projects to bump.
    //
    // Both stay on the line their consumers expect. netty 4.2.x and jetty 12.1.x are not
    // interchangeable with what the AWS SDK and Javalin compile against.
    implementation(platform("io.netty:netty-bom:4.1.136.Final"))
    implementation(platform("org.eclipse.jetty:jetty-bom:12.1.12"))

//    val detekt = "1.23.5"
//    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:$detekt")

    val kotlin = "2.4.0"
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin")

    val javalin = "7.2.2"
    api("io.javalin:javalin:$javalin") {
        exclude(group = "org.eclipse.jetty", module = "jetty-server")
        exclude(group = "org.eclipse.jetty", module = "jetty-http")
    }
    api("io.javalin.community.ssl:javalin-ssl:$javalin")

    val javalinOpenApi = "7.2.2"
    api("io.javalin.community.openapi:javalin-openapi-plugin:$javalinOpenApi")
    kapt("io.javalin.community.openapi:openapi-annotation-processor:$javalinOpenApi")

    val javalinRouting = "7.2.2"
    api("io.javalin.community.routing:routing-dsl:$javalinRouting")

    val bcrypt = "0.10.2"
    implementation("at.favre.lib:bcrypt:$bcrypt")

    val expressible = "1.3.6"
    api("org.panda-lang:expressible:$expressible")
    api("org.panda-lang:expressible-kt:$expressible")
    testImplementation("org.panda-lang:expressible-junit:$expressible")

    val cdn = "1.14.9"
    api("net.dzikoysk:cdn:$cdn")
    api("net.dzikoysk:cdn-kt:$cdn")

    val picocli = "4.7.7"
    kapt("info.picocli:picocli-codegen:$picocli")
    api("info.picocli:picocli:$picocli")

    val awssdk = "2.46.3"
    implementation(platform("software.amazon.awssdk:bom:$awssdk"))
    implementation("software.amazon.awssdk:s3:$awssdk")
    // STS is needed so it Web Identity Tokens can be used
    // See https://docs.aws.amazon.com/eks/latest/userguide/iam-roles-for-service-accounts-minimum-sdk.html
    implementation("software.amazon.awssdk:sts:$awssdk")

    val awsSdkV1 = "1.12.797"
    testImplementation("com.amazonaws:aws-java-sdk-s3:$awsSdkV1")

    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")

    val exposed = "1.3.0"
    api("org.jetbrains.exposed:exposed-core:$exposed")
    api("org.jetbrains.exposed:exposed-dao:$exposed")
    api("org.jetbrains.exposed:exposed-jdbc:$exposed")
    api("org.jetbrains.exposed:exposed-java-time:$exposed")

    // Drivers
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0") // note: 3.50.3.0 is broken
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.8")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("com.h2database:h2:2.3.232")
    implementation("com.mysql:mysql-connector-j:9.7.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    implementation("com.google.protobuf:protobuf-java:4.35.0")

    val jackson = "2.21.5"
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jackson")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jackson")

    // Pinned at 4.x: jsonschema-generator 5.x requires Jackson 3.x (full tools.jackson.* package rename); we're on Jackson 2.21.x.
    val jsonSchema = "4.38.0"
    implementation("com.github.victools:jsonschema-generator:$jsonSchema")

    val httpClient = "2.1.0"
    implementation("com.google.http-client:google-http-client:$httpClient") {
        exclude(group = "commons-codec", module = "commons-codec")
        exclude(group = "com.google.guava", module = "guava")
    }
    api("commons-codec:commons-codec:1.22.0")
    api("com.google.guava:guava:33.6.0-android")
    testImplementation("com.google.http-client:google-http-client-jackson2:$httpClient")

    val jansi = "2.4.3"
    implementation("org.fusesource.jansi:jansi:$jansi")

    val journalist = "1.0.12"
    api("com.reposilite:journalist:$journalist")
    implementation("com.reposilite:journalist-slf4j:$journalist")
    implementation("com.reposilite:journalist-tinylog:$journalist")

    val tinylog = "2.7.0"
    implementation("org.tinylog:slf4j-tinylog:$tinylog")
    implementation("org.tinylog:tinylog-api:$tinylog")
    implementation("org.tinylog:tinylog-impl:$tinylog")

    val testcontainers = "2.0.5"
    testImplementation("org.testcontainers:testcontainers-mariadb:$testcontainers")
    testImplementation("org.testcontainers:testcontainers:$testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:$testcontainers")
    testImplementation("org.testcontainers:testcontainers-mysql:$testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainers") {
        exclude(group = "org.apache.commons", module = "commons-compress")
    }
    testImplementation("org.apache.commons:commons-compress:1.28.0")

    val ldap = "7.0.4"
    testImplementation("com.unboundid:unboundid-ldapsdk:$ldap")
}

tasks.withType<ShadowJar> {
    archiveFileName.set("ingot-${archiveVersion.get()}.jar")
    // Shadow 9 changed the default duplicates strategy to EXCLUDE, under which
    // mergeServiceFiles() stops merging and silently keeps only the first
    // META-INF/services file it encounters. That is not a cosmetic difference: the fat jar
    // then declared journalist's own tinylog writer and none of tinylog's, so the server
    // came up logging "Service implementation 'rolling file' not found" and wrote nothing
    // to /var/log/reposilite at all. Shadow 8, which produced the last upstream release,
    // defaulted to INCLUDE. See https://github.com/GradleUp/shadow/releases/tag/9.0.0
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    minimize {
        exclude(dependency("org.eclipse.jetty:.*"))
        exclude(dependency("org.eclipse.jetty.http2:.*"))
        exclude(dependency("org.eclipse.jetty.websocket:.*"))
        exclude(dependency("org.bouncycastle:.*"))
        exclude(dependency("com.fasterxml.woodstox:woodstox-core:.*"))
        exclude(dependency("commons-logging:commons-logging:.*"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-reflect:.*"))
        exclude(dependency("org.jetbrains.exposed:.*"))
        exclude(dependency("org.xerial:sqlite-jdbc.*"))
        exclude(dependency("org.sqlite:.*"))
        exclude(dependency("mysql:.*"))
        exclude(dependency("com.mysql:.*"))
        exclude(dependency("org.mariadb.jdbc:.*"))
        exclude(dependency("org.postgresql:.*"))
        exclude(dependency("org.h2:.*"))
        exclude(dependency("com.h2database:.*"))
        exclude(dependency("org.tinylog:.*"))
        exclude(dependency("org.slf4j:.*"))
        exclude(dependency("software.amazon.awssdk:.*"))
        // this is need otherwise the class com.github.benmanes.caffeine.cache.SSMSAW is not found at runtime
        // see also https://github.com/ben-manes/caffeine/discussions/762
        exclude(dependency("com.github.ben-manes.caffeine:.*"))
    }
}

publishing {
    publications {
        create<MavenPublication>("bundle") {
            from(components.getByName("java"))
            artifactId = "ingot"
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

tasks {
    register<Copy>("generateKotlin") {
        inputs.property("version", version)
        from("$projectDir/src/template/kotlin")
        into("$projectDir/src/generated/kotlin")
        filter(ReplaceTokens::class, "tokens" to mapOf("version" to version))
    }
    compileKotlin { dependsOn("generateKotlin") }
    sourcesJar { dependsOn("generateKotlin") }
    kotlinSourcesJar { dependsOn("generateKotlin") }
    withType<KaptGenerateStubs> { dependsOn("generateKotlin") }
}

kotlin.sourceSets.main {
    kotlin.srcDir("$projectDir/src/generated/kotlin")
}

kapt {
    arguments {
        arg("project", "${project.group}/${project.name}") // picocli requirement
    }
}

jacoco {
    toolVersion = "0.8.14"
}

// Trivy cannot see Gradle dependencies. It reads lockfiles, and this build has none; the
// shadow jar is no help either, because merging the dependencies flattens away the nested
// jars it would identify them by. So the resolved runtime classpath is copied out and the
// scanner is pointed at the jars themselves, which it identifies by checksum.
val collectRuntimeDependencies by tasks.registering(Copy::class) {
    group = "verification"
    description = "Copies the resolved runtime classpath so a vulnerability scanner can read it"

    from(configurations.named("runtimeClasspath"))
    into(layout.buildDirectory.dir("dependency-jars"))
}

// Ingot ships as FOSS, so every bundled dependency has to stay compatible with that.
// ApexCharts is what this guards against: same package id, new licence, one dependency
// bump, and nobody notices until a distribution packages the product. `checkLicense`
// fails the build on any licence that is not on the list, which forces the decision to
// be made deliberately instead of silently.
licenseReport {
    configurations = arrayOf("runtimeClasspath")
    filters = arrayOf(LicenseBundleNormalizer())
    renderers = arrayOf<ReportRenderer>(InventoryHtmlReportRenderer(), JsonReportRenderer())
    allowedLicensesFile = file("allowed-licenses.json")
}

tasks.test {
    extensions.configure(JacocoTaskExtension::class) {
        setDestinationFile(file("${project.layout.buildDirectory.get()}/jacoco/jacoco.exec"))
    }

    finalizedBy("jacocoTestReport")
}

tasks.named<Test>("integration") {
    // Floci (used in S3 integration tests) does not auto-rewrite virtual-host requests
    // to the container's random port, so the AWS SDK must use path-style addressing.
    systemProperty("reposilite.s3.pathStyleAccessEnabled", "true")
}

// Tests that run the published container images rather than the code inside them.
//
// They are a suite of their own, and deliberately not wired into `check`, because they
// need an image that already exists. Building it is not something this build can do for
// them: the image build runs Gradle, so a test that produced it would be this build
// invoking itself. CI builds the image and then points these tests at the tag:
//
//     ./gradlew :reposilite-backend:containerTest -Dingot.image=ingot:ci
//
// They also depend on nothing from the main source set. What they exercise is the image as
// an operator receives it, over HTTP and through Docker, so importing the server's own
// classes would only let them assert against the wrong thing.
testing {
    suites {
        register<JvmTestSuite>("containerTest") {
            useJUnitJupiter("6.1.0")

            targets.configureEach {
                testTask.configure {
                    // Both are passed through so a developer can check a local build, and so
                    // the upstream reference can be raised without touching the tests.
                    systemProperty("ingot.image", providers.systemProperty("ingot.image").getOrElse("ingot:ci"))
                    systemProperty("ingot.dashboard.image", providers.systemProperty("ingot.dashboard.image").getOrElse("ingot-dashboard:ci"))
                    systemProperty("ingot.upstream.image", providers.systemProperty("ingot.upstream.image").getOrElse("dzikoysk/reposilite:3.5.28"))

                    // One container at a time, and several of them per test. Running the
                    // classes in parallel would just contend for the same Docker daemon.
                    maxParallelForks = 1
                    testLogging {
                        events("passed", "failed", "skipped")
                        showStandardStreams = false
                    }
                }
            }
        }
    }
}

// Declared next to the suite rather than inside it: a suite's own dependencies block cannot
// resolve `kotlin(...)`, and naming the standard library by coordinate would mean carrying
// the Kotlin version a second time and keeping the two in step by hand.
dependencies {
    "containerTestImplementation"(kotlin("stdlib"))
    "containerTestImplementation"("org.testcontainers:testcontainers:2.0.5")
    "containerTestImplementation"("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    "containerTestImplementation"("org.assertj:assertj-core:4.0.0-M1")
}

tasks.jacocoTestReport {
    reports {
        html.required.set(false)
        csv.required.set(false)
        xml.required.set(true)
        xml.outputLocation.set(file("./build/reports/jacoco/reposilite-backend-report.xml"))
    }

    executionData(fileTree(project.layout.buildDirectory.get()).include("jacoco/*.exec"))
    finalizedBy("jacocoTestCoverageVerification")
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.0".toBigDecimal()
            }
        }
        rule {
            enabled = true
            element = "CLASS"
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.0".toBigDecimal()
            }
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.0".toBigDecimal()
            }
            excludes = listOf()
        }
    }
}

val testCoverage by tasks.registering {
    group = "verification"
    description = "Runs the unit tests with coverage"

    dependsOn(
        ":reposilite-backend:test",
        ":reposilite-backend:integration",
        ":reposilite-backend:jacocoTestReport",
        ":reposilite-backend:jacocoTestCoverageVerification"
    )
}

tasks["integration"].mustRunAfter(tasks["test"])
tasks["jacocoTestReport"].mustRunAfter(tasks["integration"])
tasks["jacocoTestCoverageVerification"].mustRunAfter(tasks["jacocoTestReport"])

// The coditory plugin contributes `integration` and the JVM test suite plugin contributes
// `integrationTest`, and both write coverage into the build directory that jacocoTestReport
// reads. Only the first was ordered, so a run that reaches both, such as `gradle build test`,
// fails validation: the report would consume execution data from a task Gradle is free to
// schedule after it.
tasks["jacocoTestReport"].mustRunAfter(tasks["integrationTest"])

//detekt {
//    buildUponDefaultConfig = true
//    allRules = false
//    config = files("$projectDir/detekt.yml")
//    autoCorrect = true
//}
//
//tasks.withType<Detekt>().configureEach {
//    jvmTarget = "11"
//}
//
//tasks.withType<DetektCreateBaselineTask>().configureEach {
//    jvmTarget = "11"
//}
