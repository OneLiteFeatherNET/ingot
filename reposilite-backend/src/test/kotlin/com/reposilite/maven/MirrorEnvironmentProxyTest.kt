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
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * The environment proxy of a mirror client. The environment is passed in as a map, so no test reads the variables of
 * the machine it runs on.
 */
internal class MirrorEnvironmentProxyTest {

    private val httpsMirror = "https://releases.example/maven"
    private val httpMirror = "http://mirror.example/maven"

    @Test
    fun `should not select an environment proxy when the switch is off`() {
        // given: a proxy variable, but the switch is off
        val environment = MirrorEnvironment(useEnvironmentProxy = false, variables = mapOf("HTTPS_PROXY" to "http://proxy.example:3128"))

        // when: a mirror without httpProxy is requested
        val result = selectEnvironmentProxy(configuredProxy = "", targetUrl = httpsMirror, environment = environment)

        // then: no environment proxy applies
        assertThat(assertOk(result)).describedAs("proxy selected while the switch is off").isNull()
    }

    @Test
    fun `should select the HTTPS_PROXY address for an https mirror when the switch is on`() {
        // given: the switch is on and HTTPS_PROXY names a proxy
        val environment = MirrorEnvironment(useEnvironmentProxy = true, variables = mapOf("HTTPS_PROXY" to "http://proxy.example:3128"))

        // when: an https mirror without httpProxy is requested
        val proxy = assertOk(selectEnvironmentProxy(configuredProxy = "", targetUrl = httpsMirror, environment = environment))

        // then: the request goes to the proxy named by HTTPS_PROXY
        assertThat(proxy).describedAs("proxy for an https mirror").isNotNull
        assertThat(proxy!!.type()).describedAs("proxy type").isEqualTo(Proxy.Type.HTTP)
        assertThat(proxy.address()).describedAs("proxy address").isEqualTo(InetSocketAddress.createUnresolved("proxy.example", 3128))
    }

    @Test
    fun `should select the HTTP_PROXY address for an http mirror and ignore HTTPS_PROXY`() {
        // given: the switch is on, and both variables name different proxies
        val environment = MirrorEnvironment(
            useEnvironmentProxy = true,
            variables = mapOf("HTTP_PROXY" to "http://plain.example:8080", "HTTPS_PROXY" to "http://secure.example:3128"),
        )

        // when: an http mirror without httpProxy is requested
        val proxy = assertOk(selectEnvironmentProxy(configuredProxy = "", targetUrl = httpMirror, environment = environment))

        // then: the request goes to the proxy named by HTTP_PROXY
        assertThat(proxy).describedAs("proxy for an http mirror").isNotNull
        assertThat(proxy!!.address()).describedAs("proxy address").isEqualTo(InetSocketAddress.createUnresolved("plain.example", 8080))
    }

    @Test
    fun `should not select a proxy for a host listed in NO_PROXY`() {
        // given: the switch is on, a proxy is set and NO_PROXY lists the mirror host
        val environment = MirrorEnvironment(
            useEnvironmentProxy = true,
            variables = mapOf("HTTPS_PROXY" to "http://proxy.example:3128", "NO_PROXY" to "internal.example, releases.example"),
        )

        // when: the mirror host is requested
        val result = selectEnvironmentProxy(configuredProxy = "", targetUrl = httpsMirror, environment = environment)

        // then: the request goes directly to the mirror
        assertThat(assertOk(result)).describedAs("proxy for a host listed in NO_PROXY").isNull()
    }

    @Test
    fun `should not select a proxy for a subdomain of a NO_PROXY entry with a leading dot`() {
        // given: NO_PROXY names a domain with a leading dot
        val environment = MirrorEnvironment(
            useEnvironmentProxy = true,
            variables = mapOf("HTTPS_PROXY" to "http://proxy.example:3128", "NO_PROXY" to ".example"),
        )

        // when: a host below that domain is requested
        val result = selectEnvironmentProxy(configuredProxy = "", targetUrl = httpsMirror, environment = environment)

        // then: the request goes directly
        assertThat(assertOk(result)).describedAs("proxy for a host below a NO_PROXY domain").isNull()
    }

    @Test
    fun `should not select a proxy for any host when NO_PROXY is a star`() {
        // given: NO_PROXY is the star
        val environment = MirrorEnvironment(
            useEnvironmentProxy = true,
            variables = mapOf("HTTPS_PROXY" to "http://proxy.example:3128", "NO_PROXY" to "*"),
        )

        // when: any mirror is requested
        val result = selectEnvironmentProxy(configuredProxy = "", targetUrl = httpsMirror, environment = environment)

        // then: the request goes directly
        assertThat(assertOk(result)).describedAs("proxy with NO_PROXY set to the star").isNull()
    }

    @Test
    fun `should leave the choice to the per-mirror httpProxy when it is set`() {
        // given: the switch is on and HTTPS_PROXY names a proxy, but the mirror has its own httpProxy
        val environment = MirrorEnvironment(useEnvironmentProxy = true, variables = mapOf("HTTPS_PROXY" to "http://proxy.example:3128"))

        // when: the mirror is requested
        val result = selectEnvironmentProxy(configuredProxy = "HTTP 127.0.0.1:1081", targetUrl = httpsMirror, environment = environment)

        // then: the environment does not decide, so the per-mirror proxy stays in use
        assertThat(assertOk(result)).describedAs("environment proxy next to a per-mirror httpProxy").isNull()
    }

    @Test
    fun `should refuse a proxy value with credentials and name the variable without the value`() {
        // given: the switch is on and HTTPS_PROXY carries a user name and a password
        val environment = MirrorEnvironment(
            useEnvironmentProxy = true,
            variables = mapOf("HTTPS_PROXY" to "http://panda:secret@proxy.example:3128"),
        )

        // when: an https mirror without httpProxy is requested
        val message = assertError(selectEnvironmentProxy(configuredProxy = "", targetUrl = httpsMirror, environment = environment))

        // then: the refusal names the variable, and holds neither the credentials nor the address
        assertThat(message).describedAs("refusal message").contains("HTTPS_PROXY")
        assertThat(message).describedAs("refusal message").doesNotContain("panda", "secret", "proxy.example")
    }

    @Test
    fun `should read the switch as off when neither variable is set`() {
        // when: the environment has no switch
        val environment = MirrorEnvironment.read(variables = emptyMap())

        // then: the switch is off
        assertThat(environment.useEnvironmentProxy).describedAs("switch without variables").isFalse
    }

    @Test
    fun `should read the switch as on when the INGOT variable is true`() {
        // when: the INGOT variable turns the switch on
        val environment = MirrorEnvironment.read(variables = mapOf("INGOT_HTTP_USE_ENV_PROXY" to "true"))

        // then: the switch is on
        assertThat(environment.useEnvironmentProxy).describedAs("switch with INGOT_HTTP_USE_ENV_PROXY set to true").isTrue
    }

    @Test
    fun `should read the switch as on when only the REPOSILITE fallback is true`() {
        // when: only the fallback variable is set
        val environment = MirrorEnvironment.read(variables = mapOf("REPOSILITE_HTTP_USE_ENV_PROXY" to "true"))

        // then: the switch is on
        assertThat(environment.useEnvironmentProxy).describedAs("switch with REPOSILITE_HTTP_USE_ENV_PROXY set to true").isTrue
    }

    @Test
    fun `should read the INGOT switch before the REPOSILITE fallback`() {
        // given: the INGOT variable turns the switch off, the REPOSILITE variable turns it on
        val environment = MirrorEnvironment.read(
            variables = mapOf("INGOT_HTTP_USE_ENV_PROXY" to "false", "REPOSILITE_HTTP_USE_ENV_PROXY" to "true"),
        )

        // then: the INGOT variable wins
        assertThat(environment.useEnvironmentProxy).describedAs("switch with both variables").isFalse
    }

}
