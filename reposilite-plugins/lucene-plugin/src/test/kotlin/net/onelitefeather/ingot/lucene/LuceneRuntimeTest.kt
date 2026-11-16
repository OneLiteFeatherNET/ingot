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

package net.onelitefeather.ingot.lucene

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class LuceneRuntimeTest {

    @Test
    fun `a server on java 17 gets a problem message that names both versions`() {
        val problem = luceneRuntimeProblem(17)

        assertThat(problem)
            .`as`("problem reported on java 17")
            .isNotNull()
            .contains("needs Java 21", "this server runs Java 17", "not active")
    }

    @Test
    fun `a server on java 21 has no problem to report`() {
        assertThat(luceneRuntimeProblem(21)).`as`("problem reported on java 21").isNull()
    }

    @Test
    fun `a server on a newer java than 21 has no problem to report`() {
        assertThat(luceneRuntimeProblem(25)).`as`("problem reported on java 25").isNull()
    }
}
