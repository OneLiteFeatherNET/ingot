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
package net.onelitefeather.ingot.identity.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Whether a team write changes the stored external reference. Only a change is guarded and written. Pure rules, so no
 * database is needed and each test states one input.
 */
internal class ExternalReferenceChangeTest {

    @Test
    fun `an absent reference in the request changes nothing`() {
        assertThat(ExternalReferenceChange.changes(current = "oidc:corp:ops-admins", requested = null)).isFalse
    }

    @Test
    fun `an empty reference clears a stored reference`() {
        assertThat(ExternalReferenceChange.changes(current = "oidc:corp:ops-admins", requested = "")).isTrue
    }

    @Test
    fun `a blank reference on a team without a reference changes nothing`() {
        assertThat(ExternalReferenceChange.changes(current = null, requested = "")).describedAs("empty").isFalse
        assertThat(ExternalReferenceChange.changes(current = null, requested = "   ")).describedAs("blank").isFalse
    }

    @Test
    fun `a repeated reference changes nothing`() {
        assertThat(ExternalReferenceChange.changes(current = "oidc:corp:ops-admins", requested = "oidc:corp:ops-admins")).isFalse
    }

    @Test
    fun `a repeated reference with surrounding whitespace changes nothing`() {
        assertThat(ExternalReferenceChange.changes(current = "oidc:corp:ops-admins", requested = "  oidc:corp:ops-admins ")).isFalse
    }

    @Test
    fun `a reference that differs only in letter case is a change`() {
        assertThat(ExternalReferenceChange.changes(current = "oidc:corp:ops-admins", requested = "OIDC:corp:ops-admins")).isTrue
    }

    @Test
    fun `a new reference on a team without one is a change`() {
        assertThat(ExternalReferenceChange.changes(current = null, requested = "oidc:corp:ops-admins")).isTrue
    }
}
