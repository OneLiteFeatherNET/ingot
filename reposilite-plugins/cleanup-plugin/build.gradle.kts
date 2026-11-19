import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.4.2"
}

// The cleanup policies are a release unit of their own, so the module has its own version and does not follow the server version.
// The server jar embeds the shadow jar as embedded-plugins/cleanup-plugin.jar, see the embedded plugin jars of reposilite-backend.
group = "net.onelitefeather.ingot"
version = "1.0.0" // x-release-please-version

dependencies {
    compileOnly(project(":reposilite-backend"))

    testImplementation(project(":reposilite-backend"))
    testImplementation(testFixtures(project(":reposilite-backend")))
}

tasks.withType<ShadowJar> {
    archiveFileName.set("cleanup-plugin.jar")
    mergeServiceFiles()
    // The loader of embedded and plugins-directory jars reads this attribute to decide which copy of the plugin wins.
    manifest {
        attributes("Implementation-Version" to project.version.toString())
    }
}

publishing {
    publications {
        named<MavenPublication>("library") {
            setArtifacts(emptyList<Any>())
            artifact(tasks.named("shadowJar")) {
                classifier = null
                extension = "jar"
            }
            pom {
                name.set("cleanup-plugin")
                description.set("Ingot plugin that applies the cleanup policies to repository versions")
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

// The module metadata lists the server as a dependency, which the shaded jar already holds. Only the POM is published.
tasks.matching { it.name.startsWith("generateMetadataFileFor") }.configureEach { enabled = false }
