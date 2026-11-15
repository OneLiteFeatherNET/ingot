import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.4.2"
}

dependencies {
    compileOnly(project(":reposilite-backend"))
    // The variable rules shared with the other search plugins (INGOT_ before REPOSILITE_, blank is unset). The shadow jar bundles it.
    implementation(project(":reposilite-search-http"))
    // Apache Lucene 10.6.0, released 2026-10-29: at least two weeks old when this change was written. The shadow jar bundles it.
    implementation("org.apache.lucene:lucene-core:10.6.0")
    // The server's Jackson: compile against the version the server ships, the plugin loader resolves it to the server copy at run time.
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.21.7")

    testImplementation(project(":reposilite-backend"))
    testImplementation(testFixtures(project(":reposilite-backend")))
}

// Not part of the image. The jar is installed by the operator, and its presence in the plugins directory is its own switch.
tasks.withType<ShadowJar> {
    archiveFileName.set("lucene-plugin.jar")
    destinationDirectory.set(file("$rootDir/reposilite-test/workspace/plugins"))
    mergeServiceFiles()
}
