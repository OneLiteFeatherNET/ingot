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

package net.onelitefeather.ingot.readonly

import com.reposilite.configuration.ConfigurationFacade
import com.reposilite.configuration.infrastructure.InMemoryConfigurationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ConfigurationReadOnlyStoreTest {

    /** Each test builds its own configuration store, so no state is shared between tests. */
    private fun newStore(): ConfigurationReadOnlyStore =
        ConfigurationReadOnlyStore(ConfigurationFacade(InMemoryConfigurationRepository()))

    @Test
    fun `should report nothing stored before the first change`() {
        // when / then: the store holds no read-only entry yet
        assertThat(newStore().read()).`as`("stored state").isNull()
    }

    @Test
    fun `should read back the state that was saved`() {
        // given: a store with no entry
        val store = newStore()

        // when: the state is stored as on against the absent revision
        val saved = store.compareAndSave(expectedRevision = null, enabled = true)

        // then: the save succeeded and the stored state is on
        assertThat(saved).`as`("save against no revision").isTrue()
        assertThat(store.read()?.enabled).`as`("stored enabled").isTrue()
    }

    @Test
    fun `should let exactly one of two saves against one revision win`() {
        // given: two toggles that both read the store before either writes
        val repository = InMemoryConfigurationRepository()
        val first = ConfigurationReadOnlyStore(ConfigurationFacade(repository))
        val second = ConfigurationReadOnlyStore(ConfigurationFacade(repository))
        val revisionSeenByFirst = first.read()?.revision
        val revisionSeenBySecond = second.read()?.revision

        // when: both save against the same revision
        val firstWon = first.compareAndSave(revisionSeenByFirst, enabled = true)
        val secondWon = second.compareAndSave(revisionSeenBySecond, enabled = false)

        // then: exactly one save is stored, the other is refused
        assertThat(listOf(firstWon, secondWon)).`as`("outcomes of the two saves").containsExactly(true, false)
    }

}
