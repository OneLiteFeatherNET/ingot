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

package net.onelitefeather.ingot.storage.capability

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** A configuration whose namespace is the given key, with the message it refuses with. */
private data class FakeNamespace(
    private val key: String?,
    private val message: String = "fake namespace overlap",
) : SharedNamespaceAware {

    override fun namespaceKey(repositoryName: String): String? = key

    override fun overlapMessage(): String = message

}

internal class SharedNamespaceCheckTest {

    @Test
    fun `repositories with the same namespace key are refused with the message of their settings`() {
        // given: two repositories whose settings name the same namespace
        val repositories = listOf(
            "alpha" to FakeNamespace("shared"),
            "beta" to FakeNamespace("shared"),
        )

        // when: the namespaces are checked
        val conflicts = findSharedNamespaceConflicts(repositories)

        // then: both are refused with the message of their settings
        assertThat(conflicts).`as`("conflicts of equal namespaces").isEqualTo(
            mapOf("alpha" to "fake namespace overlap", "beta" to "fake namespace overlap")
        )
    }

    @Test
    fun `a repository without a namespace key takes no part in the check`() {
        // given: two repositories without a namespace, and one with a namespace of its own
        val repositories = listOf(
            "alpha" to FakeNamespace(null),
            "beta" to FakeNamespace(null),
            "gamma" to FakeNamespace("own"),
        )

        // when: the namespaces are checked
        val conflicts = findSharedNamespaceConflicts(repositories)

        // then: no repository is in conflict
        assertThat(conflicts).`as`("conflicts of repositories without a namespace").isEmpty()
    }

    @Test
    fun `repositories with distinct namespace keys are all loaded`() {
        // given: two repositories with distinct namespaces
        val repositories = listOf(
            "alpha" to FakeNamespace("first"),
            "beta" to FakeNamespace("second"),
        )

        // when: the namespaces are checked
        val conflicts = findSharedNamespaceConflicts(repositories)

        // then: no repository is in conflict
        assertThat(conflicts).`as`("conflicts of distinct namespaces").isEmpty()
    }

}
