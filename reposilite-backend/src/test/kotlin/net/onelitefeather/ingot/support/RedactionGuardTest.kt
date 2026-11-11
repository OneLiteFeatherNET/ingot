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

package net.onelitefeather.ingot.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RedactionGuardTest {

    @Test
    fun `should report a leak when a known secret is still present in an entry`() {
        // given: a guard that knows one secret and an entry that still carries it
        val guard = RedactionGuard(listOf("planted-secret-value"))
        val files = mapOf("logs/node.log" to "line with planted-secret-value".toByteArray())

        // when: the entries are checked
        val leaks = guard.leaks(files)

        // then: the entry is reported
        assertThat(leaks).`as`("reported leaks").containsExactly("logs/node.log")
    }

    @Test
    fun `should not name the secret value in the report`() {
        // given: a leaking entry
        val guard = RedactionGuard(listOf("planted-secret-value"))

        // when: the entries are checked
        val report = guard.leaks(mapOf("system-info.json" to "planted-secret-value".toByteArray())).toString()

        // then: the report names the entry and never the value
        assertThat(report).`as`("report of the guard").doesNotContain("planted-secret-value")
    }

    @Test
    fun `should report nothing when no known secret is present`() {
        val guard = RedactionGuard(listOf("planted-secret-value"))

        val leaks = guard.leaks(mapOf("manifest.json" to "{\"redactions\":3}".toByteArray()))

        assertThat(leaks).`as`("reported leaks of a clean bundle").isEmpty()
    }

    @Test
    fun `should report a short secret only when it appears as a whole word`() {
        // given: a guard that knows a secret of three characters
        val guard = RedactionGuard(listOf("abc"))

        // when: the short value appears inside a longer word, and then as a word of its own
        val inside = guard.leaks(mapOf("a.json" to "abcdef".toByteArray()))
        val whole = guard.leaks(mapOf("b.json" to "value abc end".toByteArray()))

        // then: only the whole word is a leak
        assertThat(inside).`as`("short value inside a word").isEmpty()
        assertThat(whole).`as`("short value as a word").containsExactly("b.json")
    }
}
