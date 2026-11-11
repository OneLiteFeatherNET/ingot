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

package net.onelitefeather.ingot.support

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

internal class SupportBundleServiceTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC)
    private val actor = AuditActor.named("operator", "192.0.2.10")

    private fun sources(cachedLog: () -> List<String> = { emptyList() }) = SupportSources(
        instance = "ingot-test",
        clock = clock,
        version = "1.2.3",
        jvm = { JvmFacts(vendor = "vendor", version = "21", uptimeMillis = 1, heapUsedMb = 1.0, heapMaxMb = 2, processors = 1, threads = 1) },
        operatingSystem = { OperatingSystemFacts(name = "Linux", version = "6", architecture = "amd64", container = false) },
        cachedLog = cachedLog,
    )

    private fun service(
        audit: MutableList<AuditEvent>,
        sources: SupportSources = sources(),
        waitBound: Duration = Duration.ZERO,
        checkLeaks: ((SupportSnapshot) -> List<String>)? = null,
        maxBytes: Long = SupportBundleWriter.MAX_BYTES,
    ): SupportBundleService = SupportBundleService(
        collector = SupportInformationCollector(sources),
        clock = clock,
        waitBound = waitBound,
        audit = { audit.add(it) },
        checkLeaks = checkLeaks ?: { it.guard.leaks(it.files) },
        maxBytes = maxBytes,
    )

    @Test
    fun `should audit one successful download with the actor and the file count`() {
        // given: an instance that can build a bundle
        val audit = CopyOnWriteArrayList<AuditEvent>()

        // when: an administrator downloads the bundle
        val bundle = service(audit).bundle(actor)

        // then: one event records the actor, the file count and success
        assertThat(audit).`as`("audit events of the download").hasSize(1)
        val event = audit.single()
        assertThat(event.action).`as`("audited action").isEqualTo(AuditAction.SUPPORT_BUNDLE_DOWNLOADED)
        assertThat(event.actor.name).`as`("actor of the download").isEqualTo("operator")
        assertThat(event.success).`as`("success of the download").isTrue()
        assertThat(event.details["files"]).`as`("file count in the event").isEqualTo(bundle.files)
    }

    @Test
    fun `should name the bundle after the instance and the generation time`() {
        val bundle = service(mutableListOf()).bundle(actor)

        assertThat(bundle.fileName).`as`("download file name").isEqualTo("ingot-support-ingot-test-20261110-100000.zip")
    }

    @Test
    fun `should not audit the system information view`() {
        // given: an audit list watched by the service
        val audit = mutableListOf<AuditEvent>()

        // when: the system information is read
        service(audit).systemInformation()

        // then: no event is written, as the page is as readable as the status page
        assertThat(audit).`as`("audit events of the page view").isEmpty()
    }

    @Test
    fun `should refuse to serve the bundle and audit an unsuccessful event when the guard finds a leak`() {
        // given: a guard that reports a planted secret that survived redaction
        val audit = mutableListOf<AuditEvent>()
        val service = service(audit, checkLeaks = { listOf("system-info.json") })

        // when: the bundle is requested
        assertThatThrownBy { service.bundle(actor) }
            .`as`("refusal of a leaking bundle")
            .isInstanceOf(RedactionRefusedException::class.java)

        // then: the event is unsuccessful and carries no value
        assertThat(audit).`as`("audit events of the refusal").hasSize(1)
        assertThat(audit.single().success).`as`("success of the refused download").isFalse()
        assertThat(audit.single().details.toString()).`as`("details of the refused download").doesNotContain("planted")
    }

    @Test
    fun `should refuse the system information too when the guard finds a leak`() {
        val service = service(mutableListOf(), checkLeaks = { listOf("system-info.json") })

        assertThatThrownBy { service.systemInformation() }
            .`as`("refusal of the page")
            .isInstanceOf(RedactionRefusedException::class.java)
    }

    @Test
    fun `should answer busy to a second build while the first one holds the permit`() {
        // given: a first build that blocks inside the collector until the test releases it
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val service = service(
            audit = mutableListOf(),
            sources = sources(cachedLog = {
                entered.countDown()
                release.await()
                emptyList()
            }),
        )
        val first = thread(name = "first-build") { service.bundle(actor) }
        entered.await()

        try {
            // when: a second build starts while the first one is still running
            // then: it is refused without waiting, because the bound is zero
            assertThatThrownBy { service.bundle(actor) }
                .`as`("second concurrent build")
                .isInstanceOf(BundleBusyException::class.java)
        } finally {
            release.countDown()
            first.join()
        }
    }

    @Test
    fun `should refuse a bundle above the size cap and serve no part of it`() {
        // given: a cap smaller than the bundle
        val service = service(mutableListOf(), maxBytes = 16)

        // when: the bundle is requested
        // then: the request is refused as too large
        assertThatThrownBy { service.bundle(actor) }
            .`as`("bundle above the cap")
            .isInstanceOf(BundleTooLargeException::class.java)
    }
}
