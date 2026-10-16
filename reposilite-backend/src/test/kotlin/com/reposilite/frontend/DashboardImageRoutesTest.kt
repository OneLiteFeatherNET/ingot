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

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Keeps the dashboard image's nginx route list in step with [DashboardPaths.APP_ROUTES].
 *
 * The split deployment serves the dashboard from nginx, which answers a reload of a dashboard
 * address with index.html only when the address matches the regex in the template. A route
 * added to the server and missing from that regex loads fine in the bundled server and breaks
 * on reload in the dashboard image, so this test reads the template and compares the two.
 */
internal class DashboardImageRoutesTest {

    @Test
    fun `should answer every dashboard route in the dashboard image template`() {
        // given: the nginx template and the dashboard's top-level segments
        val template = readTemplate()
        val nginxSegments = dashboardLocationSegments(template)
        val dashboardSegments = DashboardPaths.APP_ROUTES.map { it.removePrefix("/").substringBefore('/') }.toSet()

        // then: every top-level dashboard segment is answered with index.html by nginx
        assertThat(nginxSegments)
            .describedAs("top-level dashboard segments missing from the nginx location regex in default.conf.template")
            .containsAll(dashboardSegments)
    }

    @Test
    fun `should report no dashboard segments when the template has no dashboard location`() {
        // given: a template without the dashboard location
        val template = "server { location / { proxy_pass \$ingot_backend; } }"

        // then: the parser reports no segments instead of guessing
        assertThat(dashboardLocationSegments(template)).describedAs("segments of a template without the dashboard location").isEmpty()
    }

    private fun readTemplate(): String {
        // Gradle runs unit tests in the Gradle project directory, one level below the repository root.
        val template = Path.of("..", "reposilite-frontend", "docker", "default.conf.template").toAbsolutePath().normalize()
        assertThat(Files.exists(template))
            .describedAs("nginx template at $template; the test expects the Gradle project to sit one level below the repository root")
            .isTrue()
        return Files.readString(template)
    }

    private fun dashboardLocationSegments(template: String): Set<String> {
        val match = DASHBOARD_LOCATION.find(template) ?: return emptySet()
        return match.groupValues[1].split('|').toSet()
    }

    private companion object {
        // Matches `location ~ ^/(browse|dashboard|...)(/|$) {` and captures the alternation.
        val DASHBOARD_LOCATION = Regex("""location ~ \^/\(([a-z|]+)\)\(/\|\$\)""")
    }
}
