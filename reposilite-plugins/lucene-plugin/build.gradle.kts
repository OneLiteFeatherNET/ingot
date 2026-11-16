import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.zip.ZipFile
import org.gradle.api.file.RelativePath

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

// Lucene 10 is compiled for Java 21. The plugin's own bytecode stays at 17 so that its entry class loads on a 17 server,
// and only the tests run on a Java 21 launcher.
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}

// Lucene's license and notice, kept under their own directory so that the files of another bundled dependency cannot replace them.
val luceneJars = configurations.getByName("runtimeClasspath").filter { it.name.startsWith("lucene-core-") }

// Not part of the image. The jar is installed by the operator, and its presence in the plugins directory is its own switch.
tasks.withType<ShadowJar> {
    archiveFileName.set("lucene-plugin.jar")
    destinationDirectory.set(file("$rootDir/reposilite-test/workspace/plugins"))
    mergeServiceFiles()
    from(luceneJars.files.map { zipTree(it) }) {
        include("META-INF/LICENSE.txt", "META-INF/NOTICE.txt")
        eachFile { relativePath = RelativePath(true, "META-INF", "licenses", "lucene", name) }
        includeEmptyDirs = false
    }
}

// The server must never carry Lucene: the plugin is installed beside it, and a Lucene class in the server would load a second copy.
val verifyServerJarHasNoLucene = tasks.register("verifyServerJarHasNoLucene") {
    val serverJar = project(":reposilite-backend").tasks.named("shadowJar")
    dependsOn(serverJar)
    val archive = serverJar.map { (it as org.gradle.api.tasks.bundling.AbstractArchiveTask).archiveFile.get().asFile }
    inputs.files(serverJar)
    doLast {
        ZipFile(archive.get()).use { zip ->
            val leaked = zip.entries().asSequence().map { it.name }.filter { it.startsWith("org/apache/lucene/") }.toList()
            check(leaked.isEmpty()) { "the server jar contains Lucene classes, the first are ${leaked.take(5)}" }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyServerJarHasNoLucene)
}
