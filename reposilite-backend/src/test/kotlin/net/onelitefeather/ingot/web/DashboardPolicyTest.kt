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
package net.onelitefeather.ingot.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The dashboard policy is part of the contract of plugin pages: a plugin page is data, so no source is added for it.
 * The text is recorded here, and the split dashboard image asserts the same text plus `form-action 'self'`
 * (reposilite-frontend/server/lib/security-headers.test.js). Changing the policy means changing both tests.
 */
internal class DashboardPolicyTest {

    @Test
    fun `should keep the dashboard policy byte for byte`() {
        // when: the policy the dashboard document is sent with is read
        val policy = SecurityHeaders.DASHBOARD_POLICY

        // then: it is the recorded text, with no source added for plugin pages
        assertThat(policy).isEqualTo(
            "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https: http:; font-src 'self' data:; connect-src 'self'; " +
                "object-src 'none'; base-uri 'self'; frame-ancestors 'none'"
        )
    }

}
