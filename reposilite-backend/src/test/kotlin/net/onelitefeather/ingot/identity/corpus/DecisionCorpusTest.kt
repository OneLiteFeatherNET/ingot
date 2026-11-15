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
package net.onelitefeather.ingot.identity.corpus

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * The equality check: every recorded row is decided again and compared with its expected outcome. A failing dynamic test is
 * named after its row, and its message names the row, the fixture, the request and the note.
 */
internal class DecisionCorpusTest {

    @TestFactory
    fun `every recorded decision equals the code on main`(): List<DynamicTest> =
        DecisionCorpus.rows.map { row ->
            dynamicTest(row.id) {
                val actual = row.evaluate()
                assertThat(actual).describedAs("${row.describe()} decides '$actual', recorded '${row.expected}'").isEqualTo(row.expected)
            }
        }

    @Test
    fun `reverse evaluation order gives the same outcome for every row`() {
        val forward = DecisionCorpus.rows.associate { it.id to it.evaluate() }
        val reverse = DecisionCorpus.rows.asReversed().associate { it.id to it.evaluate() }

        assertThat(reverse).describedAs("outcomes depend on the order of evaluation").isEqualTo(forward)
    }
}
