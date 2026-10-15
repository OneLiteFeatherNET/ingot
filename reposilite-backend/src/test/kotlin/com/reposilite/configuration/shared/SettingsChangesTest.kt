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

package com.reposilite.configuration.shared

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SettingsChangesTest {

    private fun changed(before: String, after: String): List<String> =
        changedSettingPaths(DEFAULT_OBJECT_MAPPER.readTree(before), DEFAULT_OBJECT_MAPPER.readTree(after))

    @Test
    fun `reports the path of each changed field, nested ones included`() {
        val paths = changed(
            """{"enabled":true,"ldap":{"host":"a","port":389}}""",
            """{"enabled":false,"ldap":{"host":"b","port":389}}"""
        )

        assertThat(paths).containsExactly("enabled", "ldap.host")
    }

    @Test
    fun `reports nothing when no field changed`() {
        assertThat(changed("""{"enabled":true}""", """{"enabled":true}""")).isEmpty()
    }

    @Test
    fun `reports a field that only one side has`() {
        assertThat(changed("""{"a":1}""", """{"a":1,"b":2}""")).containsExactly("b")
        assertThat(changed("""{"a":1,"b":2}""", """{"a":1}""")).containsExactly("b")
    }

    @Test
    fun `reports a changed list as a whole`() {
        assertThat(changed("""{"routes":["a"]}""", """{"routes":["a","b"]}""")).containsExactly("routes")
    }

    @Test
    fun `never returns the value of a field`() {
        val paths = changed("""{"secret":"old-secret-value"}""", """{"secret":"new-secret-value"}""")

        assertThat(paths).containsExactly("secret")
        assertThat(paths.joinToString()).doesNotContain("old-secret-value", "new-secret-value")
    }

}
