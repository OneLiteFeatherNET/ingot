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

package net.onelitefeather.ingot.cluster

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class ClusterBusUrlTest {

    @Test
    fun `every accepted scheme is accepted`() {
        ClusterBusUrl.ACCEPTED_SCHEMES.forEach { scheme ->
            assertThat(ClusterBusUrl.parse("$scheme://bus.example:6379").scheme)
                .describedAs("scheme $scheme")
                .isEqualTo(scheme)
        }
    }

    @Test
    fun `an unknown scheme is refused and the message names the accepted schemes`() {
        assertThatThrownBy { ClusterBusUrl.parse("http://ops:s3cret@bus.example.com:8080") }
            .isInstanceOf(ClusterConfigurationException::class.java)
            .hasMessageContaining("nats")
            .hasMessageContaining("valkeys")
            .hasMessageNotContaining("s3cret")
            .hasMessageNotContaining("bus.example.com")
            .hasMessageNotContaining("http://")
    }

    @Test
    fun `a url that cannot be parsed is refused without echoing it`() {
        assertThatThrownBy { ClusterBusUrl.parse("not a url s3cret") }
            .isInstanceOf(ClusterConfigurationException::class.java)
            .hasMessageNotContaining("s3cret")
    }

    @Test
    fun `the redacted url keeps the endpoint and replaces the credentials`() {
        val url = ClusterBusUrl.parse("nats://ops:s3cret@nats.example:4222")

        assertThat(url.redacted()).isEqualTo("nats://REDACTED@nats.example:4222")
    }

    @Test
    fun `a url without credentials is shown unchanged by redaction`() {
        assertThat(ClusterBusUrl.parse("valkey://cache.example:6379").redacted()).isEqualTo("valkey://cache.example:6379")
    }

    @Test
    fun `the string form of a url never reveals its password`() {
        assertThat(ClusterBusUrl.parse("nats://ops:s3cret@nats.example:4222").toString()).doesNotContain("s3cret")
    }

    @Test
    fun `tls schemes are encrypted and the plain schemes are not`() {
        assertThat(ClusterBusUrl.parse("tls://a.example:4222").encrypted).isTrue()
        assertThat(ClusterBusUrl.parse("rediss://a.example:6379").encrypted).isTrue()
        assertThat(ClusterBusUrl.parse("valkeys://a.example:6379").encrypted).isTrue()
        assertThat(ClusterBusUrl.parse("nats://a.example:4222").encrypted).isFalse()
        assertThat(ClusterBusUrl.parse("redis://a.example:6379").encrypted).isFalse()
        assertThat(ClusterBusUrl.parse("valkey://a.example:6379").encrypted).isFalse()
    }
}
