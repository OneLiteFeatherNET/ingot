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

package com.reposilite.maven

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class UpstreamSourceHostTest {

    @Test
    fun `should keep only the host name of an upstream URL`() {
        // when: an upstream with credentials, a path and a query is reduced
        val host = upstreamSourceHost("https://deploy:secret@repo.example.org/maven2/?token=abc")

        // then: only the host name is kept
        assertThat(host).isEqualTo("repo.example.org")
    }

    @Test
    fun `should keep a port that is not the default of the scheme`() {
        // when: an upstream on a non-default port is reduced
        val host = upstreamSourceHost("https://repo.example.org:8443/maven2")

        // then: the port stays with the host name
        assertThat(host).isEqualTo("repo.example.org:8443")
    }

    @Test
    fun `should drop the port when it is the default of the scheme`() {
        // when: upstreams on the default ports of http and https are reduced
        val secure = upstreamSourceHost("https://repo.example.org:443/maven2")
        val plain = upstreamSourceHost("http://repo.example.org:80/maven2")

        // then: the default ports are dropped
        assertThat(secure).`as`("https on 443").isEqualTo("repo.example.org")
        assertThat(plain).`as`("http on 80").isEqualTo("repo.example.org")
    }

    @Test
    fun `should return no host for an upstream without a host name`() {
        // when: an upstream that names no host is reduced
        val host = upstreamSourceHost("file:/var/lib/mirror")

        // then: there is nothing to record
        assertThat(host).isNull()
    }

    @Test
    fun `should return no host for an upstream that is not a valid URI`() {
        // when: an upstream with a space in the host is reduced
        val host = upstreamSourceHost("https://repo example.org/maven2")

        // then: there is nothing to record and no exception escapes
        assertThat(host).isNull()
    }

    @Test
    fun `should cut a host name to the length of the ledger column`() {
        // given: a host name longer than the column
        val longHost = "a".repeat(MAX_PROVENANCE_HOST_LENGTH + 10) + ".example.org"

        // when: an upstream on that host is reduced
        val host = upstreamSourceHost("https://$longHost/maven2")

        // then: the host is cut to the column length
        assertThat(host).`as`("host length").hasSize(MAX_PROVENANCE_HOST_LENGTH)
    }

}
