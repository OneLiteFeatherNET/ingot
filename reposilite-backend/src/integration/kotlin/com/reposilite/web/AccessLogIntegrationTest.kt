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

package com.reposilite.web

import com.reposilite.ReposiliteRunner
import com.reposilite.ReposiliteSpecification
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.journalist.Channel
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.logging.AccessLogSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import panda.std.reactive.ReferenceUtils
import java.util.concurrent.atomic.AtomicLong

/**
 * The access record with the switch on and the external cache bypass off, the combination that the debug line never covers.
 * The clock is a counter: every read advances it by exactly five milliseconds, so the duration is known in advance.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class AccessLogIntegrationTest : ReposiliteSpecification() {

    private val ticks = AtomicLong()

    override fun accessLogSetting(): AccessLogSetting =
        AccessLogSetting(enabled = true, nanoTime = { ticks.addAndGet(FIVE_MILLISECONDS) })

    override fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) {
        ReferenceUtils.setValue(localConfiguration.bypassExternalCache, false)
    }

    @Test
    @Timeout(60)
    fun `should write one INFO access record for a request`() {
        // when: one request is made
        Unirest.get("$base/api/status/health").asString()

        // then: exactly one INFO record names that request
        val records = accessRecords("path=/api/status/health")
        assertThat(records).describedAs("access records of one request").hasSize(1)
        assertThat(records.single().first).describedAs("channel of the access record").isEqualTo(Channel.INFO)
    }

    @Test
    @Timeout(60)
    fun `should record the method, path, status, duration and user of a request`() {
        // when: a request is made with the instance admin credentials
        Unirest.get("$base/api/status/instance")
            .basicAuth(ReposiliteRunner.DEFAULT_TOKEN.first, ReposiliteRunner.DEFAULT_TOKEN.second)
            .asString()

        // then: the record carries each field, with the duration of the injected clock
        val line = accessRecords("path=/api/status/instance").single().second
        assertThat(line).describedAs("access record of the request")
            .contains("method=GET")
            .contains("status=200")
            .contains("duration_ms=5")
            .contains("bytes=")
            .contains("client=")
            .contains("user=${ReposiliteRunner.DEFAULT_TOKEN.first}")
    }

    @Test
    @Timeout(60)
    fun `should record a dash as the user of an anonymous request that is refused`() {
        // when: a protected endpoint is called without credentials
        Unirest.get("$base/api/status/instance").asString()

        // then: the record carries the refusal and no user
        val line = accessRecords("path=/api/status/instance").single().second
        assertThat(line).describedAs("access record of the refused request")
            .contains("status=401")
            .contains("user=-")
    }

    @Test
    @Timeout(60)
    fun `should keep the query string out of the access record`() {
        // when: the request carries a token in its query string
        Unirest.get("$base/api/status/health?token=secret-value").asString()

        // then: the path is recorded without the query, and the value is absent
        val line = accessRecords("path=/api/status/health").single().second
        assertThat(line).describedAs("access record with a query string").doesNotContain("secret-value")
    }

    @Test
    @Timeout(60)
    fun `should keep the authorization value out of the access record`() {
        // when: the request authenticates with the instance admin credentials
        Unirest.get("$base/api/status/instance")
            .basicAuth(ReposiliteRunner.DEFAULT_TOKEN.first, ReposiliteRunner.DEFAULT_TOKEN.second)
            .asString()

        // then: neither the secret nor the encoded credentials are in the record
        val line = accessRecords("path=/api/status/instance").single().second
        assertThat(line).describedAs("access record with credentials")
            .doesNotContain(ReposiliteRunner.DEFAULT_TOKEN.second)
            .doesNotContain(java.util.Base64.getEncoder().encodeToString("${ReposiliteRunner.DEFAULT_TOKEN.first}:${ReposiliteRunner.DEFAULT_TOKEN.second}".toByteArray()))
    }

    /** The records of the instance whose text contains [marker], as channel and line pairs, in the order they were written. */
    private fun accessRecords(marker: String): List<Pair<Channel, String>> =
        reposilite.journalist.cachedLogger.messages
            .map { it.key to it.value }
            .filter { (_, line) -> line.contains(marker) && line.startsWith("access ") }

    private companion object {
        const val FIVE_MILLISECONDS = 5_000_000L
    }

}

/** The default of every installation: no access record is written, whatever the cache bypass says. */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class AccessLogSwitchOffIntegrationTest : ReposiliteSpecification() {

    override fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) {
        ReferenceUtils.setValue(localConfiguration.bypassExternalCache, false)
    }

    @Test
    @Timeout(60)
    fun `should write no access record when the switch is off`() {
        // when: a request is made
        Unirest.get("$base/api/status/health").asString()

        // then: no access record is written for it
        val records = reposilite.journalist.cachedLogger.messages.filter { it.value.startsWith("access ") }
        assertThat(records).describedAs("access records with the switch off").isEmpty()
    }

}
