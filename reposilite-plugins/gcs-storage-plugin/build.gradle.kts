import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("com.coditory.integration-test") version "2.2.5"
    id("com.gradleup.shadow") version "9.4.2"
}

dependencies {
    compileOnly(project(":reposilite-backend"))

    // The Cloud Storage client comes from the BOM, so every Google Cloud artifact moves together. The BOM is the newest release at the
    // time of writing. The client is used over HTTP only, see the storage options of the plugin.
    implementation(platform("com.google.cloud:libraries-bom:26.93.0"))
    implementation("com.google.cloud:google-cloud-storage")
    // The server's Jackson: compile against the version the server ships, the plugin loader resolves it to the server copy at run time.
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.21.7")

    testImplementation(project(":reposilite-backend"))
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.7")

    // The container tests run against fake-gcs-server in the integration source set. They need the server classes at run time, and the
    // container and assertion libraries that the unit tests already have.
    "integrationImplementation"(project(":reposilite-backend"))
    "integrationImplementation"("org.testcontainers:testcontainers:2.0.5")
    "integrationImplementation"("org.junit.jupiter:junit-jupiter-api:6.1.0")
    "integrationImplementation"("org.assertj:assertj-core:4.0.0-M1")
    "integrationRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:6.1.0")
    "integrationRuntimeOnly"("org.junit.platform:junit-platform-launcher:6.1.0")
}

// The Cloud Storage client declares Cloud Monitoring, the native TLS provider, the gRPC generated clients and the XML stack as runtime
// dependencies. The plugin uses the HTTP transport only, so those are left out. The gRPC transport classes stay in the jar: the tracing
// of the HTTP path reads the gRPC context, and excluding the whole gRPC group leaves that class missing at run time. The jar is
// therefore large, and pruning it further is a follow-up that the documentation names.
configurations.configureEach {
    exclude(group = "com.google.cloud.opentelemetry")
    exclude(group = "org.conscrypt")
    exclude(group = "com.google.cloud", module = "google-cloud-monitoring")
    exclude(group = "com.google.cloud", module = "google-cloud-core-grpc")
    exclude(group = "com.google.api.grpc", module = "proto-google-cloud-monitoring-v3")
    exclude(group = "com.google.api", module = "gax-grpc")
    exclude(group = "com.fasterxml.jackson.dataformat")
    exclude(group = "com.fasterxml.woodstox")
    exclude(group = "org.codehaus.woodstox")
}

// Not part of the image. The jar is installed by the operator into the plugins directory, the same as the other optional plugins.
// The libraries of the SDK that can clash with the server, or with another plugin, live under a package of their own. The server's
// Jackson is not in this jar at all (it is compileOnly), so relocating the copies of the SDK does not touch it.
val shadedPackage = "net.onelitefeather.ingot.storage.gcs.shaded"

tasks.withType<ShadowJar> {
    archiveFileName.set("gcs-storage-plugin.jar")
    destinationDirectory.set(file("$rootDir/reposilite-test/workspace/plugins"))
    mergeServiceFiles()
    relocate("com.google.common", "$shadedPackage.com.google.common")
    relocate("com.google.protobuf", "$shadedPackage.com.google.protobuf")
    relocate("com.google.api.gax", "$shadedPackage.com.google.api.gax")

    // The size is printed so that the figure in the documentation can be checked on each SDK bump.
    doLast {
        logger.lifecycle("gcs-storage-plugin.jar: ${archiveFile.get().asFile.length()} bytes")
    }
}

// The release unit is the shaded jar, which is self-contained, so the publication carries that jar and none of the SDK's dependencies.
// The coordinates are net.onelitefeather.ingot:gcs-storage-plugin, the group of the Ingot libraries rather than of the plugin API.
group = "net.onelitefeather.ingot"

publishing {
    publications {
        named<MavenPublication>("library") {
            setArtifacts(emptyList<Any>())
            artifact(tasks.named("shadowJar")) {
                classifier = null
                extension = "jar"
            }
            pom {
                name.set("gcs-storage-plugin")
                description.set("Ingot storage plugin that keeps repository files in a Google Cloud Storage bucket")
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

// The module metadata of the java component lists the SDK, which the shaded jar already holds. Only the POM is published.
tasks.matching { it.name.startsWith("generateMetadataFileFor") }.configureEach { enabled = false }

// The integration suite reads the shaded jar as an artifact, so the jar is built first and its path is handed to the tests.
tasks.named<Test>("integration") {
    val shadedJar = tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile }
    dependsOn("shadowJar")
    doFirst {
        systemProperty("ingot.gcs.jar", shadedJar.get().asFile.absolutePath)
    }
}
