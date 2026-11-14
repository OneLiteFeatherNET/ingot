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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DirectoryTokenMatchTest {

    private fun owner(principalId: Int?, kind: PrincipalKind = PrincipalKind.USER) = AccessSubject(
        principalId = principalId,
        principalName = "owner",
        kind = kind,
        disabled = false,
        levels = emptyList(),
        tokenRules = null,
    )

    @Test
    fun `refuses a directory password for a token owned by a user with a second factor`() {
        val refused = DirectoryTokenMatch.refused(owner(principalId = 7)) { id -> id == 7 }

        assertThat(refused).`as`("token of a user with a factor").isTrue()
    }

    @Test
    fun `accepts a directory password for a token owned by a user without a second factor`() {
        val refused = DirectoryTokenMatch.refused(owner(principalId = 7)) { false }

        assertThat(refused).`as`("token of a user without a factor").isFalse()
    }

    @Test
    fun `accepts a directory password for a token that no principal owns`() {
        val refused = DirectoryTokenMatch.refused(owner(principalId = null)) { true }

        assertThat(refused).`as`("token without an owner").isFalse()
    }

    @Test
    fun `accepts a directory password for a machine principal`() {
        val refused = DirectoryTokenMatch.refused(owner(principalId = 7, kind = PrincipalKind.MACHINE)) { true }

        assertThat(refused).`as`("machine principal").isFalse()
    }

}
