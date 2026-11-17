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

package com.reposilite.auth

import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

internal class DirectoryTokenMatchTest {

    private fun owner(principalId: Int?, kind: PrincipalKind = PrincipalKind.USER) = AccessSubject(
        principalId = principalId,
        principalName = "owner",
        kind = kind,
        disabled = false,
        levels = emptyList(),
        tokenRules = null,
    )

    private fun directoryUser(id: Int?, kind: PrincipalKind = PrincipalKind.USER) = id?.let {
        PrincipalRecord(
            id = it,
            kind = kind,
            name = "alice",
            displayName = "Alice",
            disabled = false,
            createdAt = Instant.parse("2026-10-15T10:00:00Z"),
            lastSeenAt = null,
        )
    }

    @Test
    fun `refuses a directory password for a token owned by a user with a second factor`() {
        val refused = DirectoryTokenMatch.refused(owner(principalId = 7), directoryUser = null) { id -> id == 7 }

        assertThat(refused).`as`("token of a user with a factor").isTrue()
    }

    @Test
    fun `refuses a directory password of a user with a second factor whoever owns the token`() {
        val refused = DirectoryTokenMatch.refused(owner(principalId = 9), directoryUser(id = 7)) { id -> id == 7 }

        assertThat(refused).`as`("directory user with a factor, token owned by a user without one").isTrue()
    }

    @Test
    fun `accepts a directory password for a token owned by a user without a second factor`() {
        val refused = DirectoryTokenMatch.refused(owner(principalId = 7), directoryUser = null) { false }

        assertThat(refused).`as`("token of a user without a factor").isFalse()
    }

    @Test
    fun `accepts a directory password of a user without a second factor for a token owned by another user without one`() {
        val refused = DirectoryTokenMatch.refused(owner(principalId = 9), directoryUser(id = 7)) { false }

        assertThat(refused).`as`("neither the directory user nor the owner has a factor").isFalse()
    }

    @Test
    fun `accepts a directory password for a token that no principal owns`() {
        val refused = DirectoryTokenMatch.refused(owner(principalId = null), directoryUser = null) { true }

        assertThat(refused).`as`("token without an owner").isFalse()
    }

    @Test
    fun `accepts a directory password for a machine principal`() {
        val refused = DirectoryTokenMatch.refused(owner(principalId = 7, kind = PrincipalKind.MACHINE), directoryUser = null) { true }

        assertThat(refused).`as`("machine principal").isFalse()
    }

    @Test
    fun `accepts a directory password of a machine principal named like a user with a factor`() {
        val refused = DirectoryTokenMatch.refused(
            owner(principalId = 9),
            directoryUser(id = 7, kind = PrincipalKind.MACHINE),
        ) { id -> id == 7 }

        assertThat(refused).`as`("machine principal with the directory name").isFalse()
    }

}
