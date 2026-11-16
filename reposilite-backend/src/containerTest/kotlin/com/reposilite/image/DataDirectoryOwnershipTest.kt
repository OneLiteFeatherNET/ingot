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

/** The decision of which ids a data directory is handed back with. Pure functions: no Docker, no environment, no clock. */
class DataDirectoryOwnershipTest {

    @Test
    @DisplayName("a rootless Podman socket under the user's runtime directory is recognised")
    fun `rootless podman socket is recognised`() {
        assertThat(isRootlessPodman("unix:///run/user/1000/podman/podman.sock"))
            .describedAs("a rootless Podman socket under /run/user")
            .isTrue()
    }

    @Test
    @DisplayName("a Docker socket is not taken for rootless Podman")
    fun `docker socket is not rootless podman`() {
        assertThat(isRootlessPodman("unix:///var/run/docker.sock"))
            .describedAs("the Docker socket of a runner")
            .isFalse()
    }

    @Test
    @DisplayName("a rootful Podman socket is not taken for rootless Podman, because its ids are the host's")
    fun `rootful podman socket is not rootless podman`() {
        assertThat(isRootlessPodman("unix:///run/podman/podman.sock"))
            .describedAs("the rootful Podman socket")
            .isFalse()
    }

    @Test
    @DisplayName("an unset DOCKER_HOST is not taken for rootless Podman")
    fun `unset docker host is not rootless podman`() {
        assertThat(isRootlessPodman(null))
            .describedAs("no DOCKER_HOST, so the default Docker socket")
            .isFalse()
    }

    @Test
    @DisplayName("under rootless Podman the directory is handed back to root inside the container")
    fun `rootless podman hands the directory back to container root`() {
        assertThat(containerOwner(hostUid = 1000, hostGid = 1000, rootless = true))
            .describedAs("the ids to chown to inside the container")
            .isEqualTo(0 to 0)
    }

    @Test
    @DisplayName("under Docker the directory is handed back with the host's own ids")
    fun `docker hands the directory back with the host ids`() {
        assertThat(containerOwner(hostUid = 1001, hostGid = 121, rootless = false))
            .describedAs("the ids to chown to inside the container")
            .isEqualTo(1001 to 121)
    }
}
