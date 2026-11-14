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

package com.reposilite.scheduling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

internal class ScheduledJobClaimsUnsharedTest {

    @Test
    fun `grants every lease when no shared database exists`() {
        val claims = ScheduledJobClaims.Unshared

        assertThat(claims.lease("cleanup:nightly:active", Duration.ofSeconds(60))).`as`("lease without a shared database").isTrue()
        assertThat(claims.lease("cleanup:nightly:active", Duration.ofSeconds(60))).`as`("second lease of the same key without a shared database").isTrue()
        assertThat(claims.extend("cleanup:nightly:active", Duration.ofSeconds(60))).`as`("extend without a shared database").isTrue()
    }

    @Test
    fun `grants an unfenced lease with epoch zero when no shared database exists`() {
        val lease = ScheduledJobClaims.Unshared.acquire("cleanup:nightly:active", Duration.ofSeconds(60))

        assertThat(lease?.epoch).`as`("epoch without a shared database").isEqualTo(0L)
        assertThat(ScheduledJobClaims.Unshared.renew(lease!!, Duration.ofSeconds(60))).`as`("renew without a shared database").isTrue()
    }

}
