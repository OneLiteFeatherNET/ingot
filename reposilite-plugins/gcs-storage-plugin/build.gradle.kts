import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("com.coditory.integration-test") version "2.2.5"
    id("com.gradleup.shadow") version "9.4.2"
}

dependencies {
    compileOnly(project(":reposilite-backend"))
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

// Not part of the image. The jar is installed by the operator into the plugins directory, the same as the other optional plugins.
tasks.withType<ShadowJar> {
    archiveFileName.set("gcs-storage-plugin.jar")
    destinationDirectory.set(file("$rootDir/reposilite-test/workspace/plugins"))
    mergeServiceFiles()

    // The size is printed so that the figure in the documentation can be checked on each SDK bump.
    doLast {
        logger.lifecycle("gcs-storage-plugin.jar: ${archiveFile.get().asFile.length()} bytes")
    }
}
