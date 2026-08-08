/*
 * Copyright (c) 2023 dzikoysk
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

import com.github.dockerjava.api.model.Capability
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

/**
 * The dashboard image, run the way the compose file runs it.
 *
 * Both bugs this image has actually had were invisible to a build: an nginx that rendered
 * no server block and answered nothing, and asset URLs that never resolved. Starting it
 * under the full hardening also means a change that quietly needs a writable filesystem or
 * a capability fails here rather than in someone's cluster.
 */
class DashboardImageTest {

    @Test
    @DisplayName("it serves the dashboard read only, unprivileged and with no capabilities")
    fun `serves the dashboard under full hardening`() {
        dashboardContainer().use { dashboard ->
            dashboard.start()

            // nginx-unprivileged already runs as 101. The assertion is here so a base image
            // swap cannot take that away unnoticed.
            val user = dashboard.execInContainer("id", "-u").stdout.trim()
            assertThat(user).describedAs("the dashboard must not run as root").isEqualTo("101")

            val page = Http.get(dashboard.baseUrl())
            assertThat(page.isSuccessful).isTrue()

            // An unsubstituted placeholder means the script tag points at a path that does
            // not exist, and the app never starts.
            assertThat(page.body)
                .describedAs("placeholders left in index.html break every asset URL")
                .doesNotContain("{{REPOSILITE.")

            // Follow the bundle the page asks for rather than assuming its name.
            val bundle = BUNDLE_REFERENCE.find(page.body)?.groupValues?.get(1)
            assertThat(bundle).describedAs("the page references no bundle").isNotNull()
            assertThat(Http.get(dashboard.baseUrl() + bundle).isSuccessful).isTrue()
        }
    }

    /**
     * The tmpfs mounts need the image's own uid. Without it they land owned by root, nginx
     * cannot render its configuration into conf.d, and it comes up serving nothing at all
     * instead of failing outright, which is the confusing failure this guards against.
     */
    private fun dashboardContainer(): GenericContainer<*> =
        GenericContainer(ImageUnderTest.dashboard)
            .withExposedPorts(DEFAULT_SERVER_PORT)
            .withCreateContainerCmdModifier { cmd ->
                cmd.hostConfig
                    ?.withReadonlyRootfs(true)
                    ?.withCapDrop(Capability.ALL)
                    ?.withSecurityOpts(listOf("no-new-privileges:true"))
            }
            .withTmpFs(
                mapOf(
                    "/tmp" to "uid=101,gid=101",
                    "/var/cache/nginx" to "uid=101,gid=101",
                    "/var/run" to "uid=101,gid=101",
                    "/etc/nginx/conf.d" to "uid=101,gid=101",
                )
            )
            .waitingFor(Wait.forHttp("/").forPort(DEFAULT_SERVER_PORT).forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(2))

    private companion object {
        val BUNDLE_REFERENCE = Regex("""src="([^"]*index-[^"]*\.js)"""")
    }
}
