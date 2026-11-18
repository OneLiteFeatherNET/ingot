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

package com.reposilite.image

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The MariaDB image of the container suite is pinned by tag and digest, the same reference the Helm dependency manifest of
 * the two-replica smoke test uses. It needs no Docker, no clock and no shared state, so it runs on every build of the suite.
 */
class MariaDbContractTest {

    @Test
    @DisplayName("the MariaDB image of the container suite is pinned by tag and digest, the same reference as the smoke test")
    fun `mariadb image is pinned by tag and digest`() {
        assertThat(MARIADB_IMAGE)
            .describedAs("the MariaDB image of the upgrade rehearsal, the cluster bus test and the mixed-version test")
            .isEqualTo("mariadb:11.4@sha256:d1891ede5a559f1086f55053bb8cc1569b0298338c4d72d71725a7f74f6ba620")
    }
}
