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

package com.reposilite.status

import com.reposilite.journalist.backend.InMemoryLogger
import net.onelitefeather.ingot.readonly.ReadOnlyState
import net.onelitefeather.ingot.readonly.ReadOnlySource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference

internal class StatusReadOnlyTest {

    private fun status(readOnly: () -> ReadOnlyState = { ReadOnlyState(enabled = false, source = ReadOnlySource.STORED) }) =
        StatusFacade(
            testEnv = true,
            maxThreads = Reference.reference(40),
            status = { true },
            remoteVersionUrl = "",
            failureFacade = FailureFacade(InMemoryLogger()),
            readOnly = readOnly,
        ).fetchInstanceStatus()

    @Test
    fun `should report the read-only state and the environment as its source`() {
        // when: the environment forces read-only
        val status = status { ReadOnlyState(enabled = true, source = ReadOnlySource.ENVIRONMENT) }

        // then: the status says so and names the environment
        assertThat(status.readOnly).`as`("readOnly in the instance status").isTrue()
        assertThat(status.readOnlySource).`as`("source in the instance status").isEqualTo("environment")
    }

    @Test
    fun `should report the stored state as off when nothing else is configured`() {
        // when: the status is read with no read-only state supplied
        val status = status()

        // then: the instance is writable and the source is the store
        assertThat(status.readOnly).`as`("readOnly in the instance status").isFalse()
        assertThat(status.readOnlySource).`as`("source in the instance status").isEqualTo("stored")
    }

}
