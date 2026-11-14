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

package net.onelitefeather.ingot.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Properties

/**
 * The configuration that `INGOT_LOG_FILE=false` selects must be the default one without the rolling file writer, so the
 * console and the muted packages cannot drift apart from the default configuration.
 */
internal class TinyLogConsoleConfigurationTest {

    private val default = load("/tinylog.properties")

    private val console = load("/$TINYLOG_CONSOLE_CONFIGURATION")

    @Test
    fun `the console configuration has no rolling file writer`() {
        assertThat(console.keys.map { it.toString() }.filter { it.startsWith("writerFile") })
            .describedAs("writerFile keys of the console configuration")
            .isEmpty()
    }

    @Test
    fun `the console configuration keeps the console writer as the default one has it`() {
        assertThat(console.filterKeys { it.toString().startsWith("writerJournalist") })
            .describedAs("writerJournalist entries of the console configuration")
            .isEqualTo(default.filterKeys { it.toString().startsWith("writerJournalist") })
    }

    @Test
    fun `the console configuration mutes the same packages as the default one`() {
        assertThat(console.filterKeys { it.toString().startsWith("level@") })
            .describedAs("level@ entries of the console configuration")
            .isEqualTo(default.filterKeys { it.toString().startsWith("level@") })
    }

    private fun load(resource: String): Properties =
        Properties().apply {
            load(requireNotNull(TinyLogConsoleConfigurationTest::class.java.getResourceAsStream(resource)) { "$resource is on the classpath" })
        }

}
