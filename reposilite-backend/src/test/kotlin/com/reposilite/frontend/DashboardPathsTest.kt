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

package com.reposilite.frontend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DashboardPathsTest {

    @Test
    fun `should reserve the first segment of every dashboard route and the server segments`() {
        // when: the reserved segments are read
        val reserved = DashboardPaths.RESERVED_SEGMENTS

        // then: the dashboard routes and the API and asset segments are all covered
        assertThat(reserved).containsExactlyInAnyOrder("browse", "dashboard", "tokens", "console", "settings", "api", "assets")
    }

    @Test
    fun `should flag repository ids that equal a reserved segment`() {
        // when: repositories with and without reserved ids are checked
        val flagged = DashboardPaths.reservedRepositoryWarnings(listOf("releases", "settings", "api", "assets", "snapshots"))

        // then: exactly the reserved ids are reported
        assertThat(flagged)
            .describedAs("warnings for reserved repository ids")
            .hasSize(3)
        assertThat(flagged[0]).contains("'settings'")
        assertThat(flagged[1]).contains("'api'")
        assertThat(flagged[2]).contains("'assets'")
    }

    @Test
    fun `should not flag repository ids that only start with a reserved segment`() {
        // when: repository ids that merely contain a reserved word are checked
        val flagged = DashboardPaths.reservedRepositoryWarnings(listOf("settings-archive", "my-api", "browser", "releases"))

        // then: none of them collide, because the browser routes match whole segments only
        assertThat(flagged).describedAs("warnings for ids that are not reserved").isEmpty()
    }

    @Test
    fun `should explain in the warning that maven clients are affected too`() {
        // when: the warning for a colliding repository is built
        val warning = DashboardPaths.reservedRepositoryWarnings(listOf("settings")).single()

        // then: it names the path that is shadowed and says that Maven GET requests are affected
        assertThat(warning)
            .describedAs("warning text")
            .contains("/settings/")
            .contains("Maven")
            .contains("not rejected")
    }

}
