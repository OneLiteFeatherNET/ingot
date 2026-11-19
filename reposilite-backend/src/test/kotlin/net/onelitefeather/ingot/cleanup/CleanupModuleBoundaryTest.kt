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
package net.onelitefeather.ingot.cleanup

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The cleanup policies live in their own module, cleanup-plugin, and reach the server only as an embedded jar. These tests
 * pin that boundary: no cleanup class on the server class path, and the plugin registered by the module's service file.
 */
class CleanupModuleBoundaryTest {

    private val cleanupClasses = listOf(
        "com.reposilite.cleanup.CleanupService",
        "com.reposilite.cleanup.CleanupPlanner",
        "com.reposilite.cleanup.application.CleanupPlugin",
        "com.reposilite.cleanup.application.CleanupSettings",
    )

    @Test
    fun `no cleanup class is loadable from the server class loader`() {
        val loader = CleanupModuleBoundaryTest::class.java.classLoader

        val loadable = cleanupClasses.filter { name ->
            try {
                Class.forName(name, false, loader)
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        }

        assertThat(loadable)
            .describedAs("cleanup classes that the server class path still contains")
            .isEmpty()
    }

    @Test
    fun `the cleanup module registers its plugin in its own service file`() {
        val serviceFile = File("../reposilite-plugins/cleanup-plugin/src/main/resources/META-INF/services/com.reposilite.plugin.api.ReposilitePlugin")

        assertThat(serviceFile.isFile)
            .describedAs("service file of the cleanup module at ${serviceFile.path}")
            .isTrue()
        assertThat(serviceFile.readLines())
            .describedAs("entries of the cleanup module service file")
            .contains("com.reposilite.cleanup.application.CleanupPlugin")
    }

    @Test
    fun `the server service file does not register the cleanup plugin`() {
        val serviceFile = File("src/main/resources/META-INF/services/com.reposilite.plugin.api.ReposilitePlugin")

        assertThat(serviceFile.readLines())
            .describedAs("entries of the server service file")
            .noneMatch { it.contains("com.reposilite.cleanup.") }
    }

}
