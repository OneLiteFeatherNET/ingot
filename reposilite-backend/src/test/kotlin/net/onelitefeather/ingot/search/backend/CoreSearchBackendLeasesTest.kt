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

package net.onelitefeather.ingot.search.backend

import com.reposilite.audit.MutableClock
import com.reposilite.scheduling.ScheduledJobClaims
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The lease store that a backend takes its single-writer lease from is handed in by the core, and is absent when none is given. */
internal class CoreSearchBackendLeasesTest {

    @Test
    fun `a context built without a lease store has none`() {
        val context = CoreSearchBackendContext(MutableClock(), "instance", "fake", { null }) { _, _ -> }

        assertThat(context.leases).`as`("lease store of a context built without one").isNull()
    }

    @Test
    fun `a context hands out the lease store it was built with`() {
        val store = object : ScheduledJobClaims {
            override fun claim(key: String): Boolean = true
        }
        val context = CoreSearchBackendContext(MutableClock(), "instance", "fake", { null }, store) { _, _ -> }

        assertThat(context.leases).`as`("lease store of the context").isSameAs(store)
    }

}
