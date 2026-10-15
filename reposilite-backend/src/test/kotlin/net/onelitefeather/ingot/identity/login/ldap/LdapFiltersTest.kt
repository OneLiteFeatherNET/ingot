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

package net.onelitefeather.ingot.identity.login.ldap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class LdapFiltersTest {

    @Test
    fun `the special characters of rfc 4515 are escaped as hex pairs`() {
        assertThat(LdapFilters.escape("a*b(c)d\\e")).isEqualTo("a\\2ab\\28c\\29d\\5ce")
    }

    @Test
    fun `a nul byte is escaped`() {
        assertThat(LdapFilters.escape("a\u0000b")).isEqualTo("a\\00b")
    }

    @Test
    fun `non ascii characters are escaped byte by byte in utf-8`() {
        assertThat(LdapFilters.escape("é")).isEqualTo("\\c3\\a9")
    }

    @Test
    fun `an injection payload stays a literal value`() {
        // when: a user name tries to close the filter and add a clause of its own
        val filter = LdapFilters.fill("(uid={username})", mapOf("username" to "*)(uid=*"))

        // then: the payload is one value, and the filter has exactly one clause
        assertThat(filter).isEqualTo("(uid=\\2a\\29\\28uid=\\2a)")
        assertThat(filter.count { it == '(' }).isEqualTo(1)
    }

    @Test
    fun `placeholders are replaced in one pass, so a value is never expanded again`() {
        // given: the user name itself looks like the placeholder of another value
        val filter = LdapFilters.fill("(uid={username})(member={dn})", mapOf("username" to "{dn}", "dn" to "cn=x"))

        assertThat(filter).isEqualTo("(uid={dn})(member=cn=x)")
    }

    @Test
    fun `a placeholder without a value stays as it is`() {
        assertThat(LdapFilters.fill("(cn={name})", emptyMap())).isEqualTo("(cn={name})")
    }
}
