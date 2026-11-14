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
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.logging.AccessLogSetting
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import panda.std.reactive.ReferenceUtils
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The access record with the switch on and the external cache bypass off, the combination that the debug line never covers.
 * The clock is a counter: every read advances it by exactly five milliseconds, so the duration is known in advance.
 *
 * The record is written after the response is sent, so a test waits for it on a latch that a subscriber counts down, and
 * never on the time. Each test makes one request and expects one record.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class AccessLogIntegrationTest : ReposiliteSpecification() {

    private val ticks = AtomicLong()

    private val records = ConcurrentLinkedQueue<String>()

    private val recordWritten = CountDownLatch(1)

    override fun accessLogSetting(): AccessLogSetting =
        AccessLogSetting(
            enabled = true,
            nanoTime = { ticks.addAndGet(FIVE_MILLISECONDS) },
            sink = { line ->
                records += line
                recordWritten.countDown()
            },
        )

    override fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) {
        ReferenceUtils.setValue(localConfiguration.bypassExternalCache, false)
    }

    @Test
    @Timeout(60)
    fun `should write one INFO access record for a request`() {
        // when: one request is made
        Unirest.get("$base/api/status/health").asString()

        // then: exactly one record names that request
        val matching = awaitRecords().filter { it.contains("path=/api/status/health") }
        assertThat(matching).describedAs("access records of one request").hasSize(1)
    }

    @Test
    @Timeout(60)
    fun `should record the method, path, status, duration and user of a request`() {
        // when: a request is made with the instance admin credentials
        Unirest.get("$base/api/status/instance")
            .basicAuth(ReposiliteRunner.DEFAULT_TOKEN.first, ReposiliteRunner.DEFAULT_TOKEN.second)
            .asString()

        // then: the record carries each field, with the duration of the injected clock
        val line = awaitRecords().single { it.contains("path=/api/status/instance") }
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
        val line = awaitRecords().single { it.contains("path=/api/status/instance") }
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
        val line = awaitRecords().single { it.contains("path=/api/status/health") }
        assertThat(line).describedAs("access record with a query string")
            .contains("path=/api/status/health ")
            .doesNotContain("secret-value")
    }

    @Test
    @Timeout(60)
    fun `should keep the authorization value out of the access record`() {
        // when: the request authenticates with the instance admin credentials
        Unirest.get("$base/api/status/instance")
            .basicAuth(ReposiliteRunner.DEFAULT_TOKEN.first, ReposiliteRunner.DEFAULT_TOKEN.second)
            .asString()

        // then: neither the secret nor the encoded credentials are in the record
        val line = awaitRecords().single { it.contains("path=/api/status/instance") }
        val encoded = Base64.getEncoder().encodeToString("${ReposiliteRunner.DEFAULT_TOKEN.first}:${ReposiliteRunner.DEFAULT_TOKEN.second}".toByteArray())
        assertThat(line).describedAs("access record with credentials")
            .doesNotContain(ReposiliteRunner.DEFAULT_TOKEN.second)
            .doesNotContain(encoded)
    }

    /** Waits, with a bound, until the record of the request has been written, and returns the records written so far. */
    private fun awaitRecords(): List<String> {
        assertThat(recordWritten.await(30, TimeUnit.SECONDS)).describedAs("access record written within 30 seconds").isTrue()
        return records.toList()
    }

    private companion object {
        const val FIVE_MILLISECONDS = 5_000_000L

    }

}

/** The default of every installation: no access record is written, whatever the cache bypass says. */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class AccessLogSwitchOffIntegrationTest : ReposiliteSpecification() {

    private val lines = ConcurrentLinkedQueue<String>()

    override fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) {
        ReferenceUtils.setValue(localConfiguration.bypassExternalCache, false)
    }

    /** The switch is off, so no record reaches the sink. A sink is still given, to show that none arrives. */
    override fun accessLogSetting(): AccessLogSetting =
        AccessLogSetting(enabled = false, sink = { line -> lines += line })

    @Test
    @Timeout(60)
    fun `should write no access record when the switch is off`() {
        // when: a request is made
        Unirest.get("$base/api/status/health").asString()

        // then: no access record is written for it. The record of an enabled switch is written after the response, so this
        // check looks at what the server has written by the time the response has arrived; it is a negative check.
        assertThat(lines.toList()).describedAs("access records with the switch off").isEmpty()
    }

}
