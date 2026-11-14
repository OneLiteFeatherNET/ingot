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

package net.onelitefeather.ingot.meilisearch

import net.onelitefeather.ingot.search.outbox.OutboxPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

/** The outbox claim lease must outlast the longest task wait of `apply` (design D4 and precondition 0.3). */
internal class MeilisearchTaskWaitLeaseTest {

    @Test
    fun `should keep the outbox claim lease longer than the longest task wait`() {
        // given: the longest task wait the settings accept
        val longestWait = Duration.ofSeconds(MeilisearchSettings.MAX_TASK_WAIT_SECONDS.toLong())

        // when / then: a second replica must not take a batch that is still being waited for
        assertThat(OutboxPolicy.LEASE)
            .`as`("outbox claim lease compared with the longest Meilisearch task wait")
            .isGreaterThan(longestWait)
    }

}
