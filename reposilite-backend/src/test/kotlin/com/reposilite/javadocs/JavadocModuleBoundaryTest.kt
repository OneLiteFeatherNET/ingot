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

package com.reposilite.javadocs

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The javadoc viewer lives in its own module, javadoc-plugin, and reaches the server only as an embedded jar. The module's
 * classes are on this test class path (design D7 of extract-javadoc-plugin), so `Class.forName` cannot prove the boundary.
 * These tests read the backend's own compiled output and the module's sources instead.
 */
internal class JavadocModuleBoundaryTest {

    @Test
    fun `the compiled main output of the backend holds no javadoc class`() {
        // given: the class directories of the backend's main source set, as the build hands them to the test
        val property = System.getProperty(MAIN_CLASSES_PROPERTY)
        assertThat(property)
            .describedAs("system property $MAIN_CLASSES_PROPERTY with the backend's main class directories, set by the test task")
            .isNotBlank
        val classDirectories = property.split(File.pathSeparator).map(::File)

        // when: every class file under com/reposilite/javadocs is collected
        val javadocClasses = classDirectories
            .map { it.resolve(JAVADOC_PACKAGE_PATH) }
            .filter { it.isDirectory }
            .flatMap { directory -> directory.walk().filter { it.isFile && it.extension == "class" }.toList() }
            .map { it.path }

        // then: none is left, they are compiled by the javadoc-plugin module
        assertThat(javadocClasses)
            .describedAs("javadoc classes that the backend's main output in $classDirectories still contains")
            .isEmpty()
    }

    @Test
    fun `the javadoc module registers its plugin in its own service file`() {
        // given: the service file of the module's sources, read from the source tree so no built jar is needed
        val serviceFile = File("../reposilite-plugins/javadoc-plugin/src/main/resources/META-INF/services/com.reposilite.plugin.api.ReposilitePlugin")

        // then: it exists and names the viewer's plugin class
        assertThat(serviceFile.isFile)
            .describedAs("service file of the javadoc module at ${serviceFile.path}")
            .isTrue()
        assertThat(serviceFile.readLines().map { it.trim() })
            .describedAs("entries of the javadoc module service file")
            .contains("com.reposilite.javadocs.application.JavadocPlugin")
    }

    private companion object {
        const val MAIN_CLASSES_PROPERTY = "ingot.backend.mainClasses"
        const val JAVADOC_PACKAGE_PATH = "com/reposilite/javadocs"
    }
}
