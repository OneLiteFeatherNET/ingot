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

package com.reposilite.status

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class ReadinessProbeTest {

    private val generousBound = Duration.ofSeconds(2)

    private fun probe(
        vararg checks: ReadinessCheck,
        bound: Duration = generousBound,
        onFailure: (String, Throwable?) -> Unit = { _, _ -> },
    ) = ReadinessProbe(checks.toList(), bound, onFailure)

    @Test
    fun `should report up with no failing check when every check passes`() {
        // given: every check answers true
        val probe = probe(
            ReadinessCheck("plugins") { true },
            ReadinessCheck("database") { true },
            ReadinessCheck("storage") { true },
        )

        // when: the probe is evaluated
        val response = probe.evaluate()

        // then: the instance is up and no check is listed as failing
        assertThat(response.status).`as`("every check passes").isEqualTo("UP")
        assertThat(response.failing).`as`("no check fails").isEmpty()
    }

    @Test
    fun `should name a check that answers false`() {
        // given: the database check answers false while the others pass
        val probe = probe(
            ReadinessCheck("plugins") { true },
            ReadinessCheck("database") { false },
            ReadinessCheck("storage") { true },
        )

        // when: the probe is evaluated
        val response = probe.evaluate()

        // then: the instance is down and the database check is the only failing one
        assertThat(response.status).`as`("one check fails").isEqualTo("DOWN")
        assertThat(response.failing).`as`("the failing check is named").containsExactly("database")
    }

    @Test
    fun `should name a check that throws`() {
        // given: the storage check raises an error
        val probe = probe(
            ReadinessCheck("plugins") { true },
            ReadinessCheck("storage") { error("storage root is gone") },
        )

        // when: the probe is evaluated
        val response = probe.evaluate()

        // then: the storage check is named as failing
        assertThat(response.failing).`as`("a thrown error fails its check").containsExactly("storage")
    }

    @Test
    fun `should name a check that does not answer within the bound`() {
        // given: the database check blocks until the test releases it, and the bound is short
        val release = CountDownLatch(1)
        try {
            val probe = probe(
                ReadinessCheck("database") { release.await(); true },
                ReadinessCheck("plugins") { true },
                bound = Duration.ofMillis(500),
            )

            // when: the probe is evaluated while the database check is still blocked
            val response = probe.evaluate()

            // then: the blocked check counts as failing and the answering check does not
            assertThat(response.status).`as`("a check that does not answer fails the probe").isEqualTo("DOWN")
            assertThat(response.failing).`as`("the blocked check is named").containsExactly("database")
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `should list failing checks in the declared order`() {
        // given: three checks fail and one passes, declared in the order plugins, database, storage, cluster
        val probe = probe(
            ReadinessCheck("plugins") { false },
            ReadinessCheck("database") { true },
            ReadinessCheck("storage") { false },
            ReadinessCheck("cluster") { false },
        )

        // when: the probe is evaluated
        val response = probe.evaluate()

        // then: the failing names keep the declared order
        assertThat(response.failing).`as`("failing names follow the declared order").containsExactly("plugins", "storage", "cluster")
    }

    @Test
    fun `should not evaluate a check that does not apply`() {
        // given: a cluster check that does not apply, and which records whether it ran
        val evaluated = AtomicBoolean(false)
        val probe = probe(
            ReadinessCheck("cluster", applies = { false }) {
                evaluated.set(true)
                false
            },
        )

        // when: the probe is evaluated
        val response = probe.evaluate()

        // then: the check did not run and does not change the answer
        assertThat(evaluated.get()).`as`("a check that does not apply is not run").isFalse
        assertThat(response.status).`as`("a check that does not apply keeps the instance up").isEqualTo("UP")
        assertThat(response.failing).`as`("a check that does not apply is not listed").isEmpty()
    }

    @Test
    fun `should count a check still running from an earlier evaluation as failing without starting it again`() {
        // given: a database check that blocks, and a latch that tells the test it has started
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val starts = AtomicInteger(0)
        try {
            val probe = probe(
                ReadinessCheck("database") {
                    starts.incrementAndGet()
                    started.countDown()
                    release.await()
                    true
                },
                bound = Duration.ofMillis(100),
            )

            // given: the first evaluation times out while the check is still blocked
            probe.evaluate()
            assertThat(started.await(5, TimeUnit.SECONDS)).`as`("the first check started").isTrue

            // when: the probe is evaluated again while the earlier check is still running
            val response = probe.evaluate()

            // then: the database check is failing and no second check was started
            assertThat(response.failing).`as`("the check still running fails").containsExactly("database")
            assertThat(starts.get()).`as`("the blocked check is not started a second time").isEqualTo(1)
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `should pass the cause of a failing check to the failure listener`() {
        // given: a storage check that fails with a detailed error, and a listener that records failures
        val causes = mutableListOf<Pair<String, Throwable?>>()
        val probe = probe(
            ReadinessCheck("storage") { throw IllegalStateException("private-builds at /srv/secret") },
            onFailure = { name, cause -> causes.add(name to cause) },
        )

        // when: the probe is evaluated
        probe.evaluate()

        // then: the listener receives the check name and the cause, so that the log carries the detail
        assertThat(causes.map { it.first }).`as`("the listener is told about the failing check").containsExactly("storage")
        assertThat(causes.single().second?.message).`as`("the listener keeps the cause").contains("private-builds")
    }

    @Test
    fun `should keep repository names and exception messages out of the response`() {
        // given: a storage check that fails and names a repository and a path in its error
        val probe = probe(
            ReadinessCheck("storage") { throw IllegalStateException("private-builds at /srv/secret") },
        )

        // when: the probe is evaluated
        val response = probe.evaluate()

        // then: the response carries the check name only
        assertThat(response.failing).`as`("the check is named").containsExactly("storage")
        assertThat(response.toString()).`as`("no repository name in the response").doesNotContain("private-builds")
        assertThat(response.toString()).`as`("no path in the response").doesNotContain("/srv/secret")
    }

    @Test
    fun `should use the replacement of an overridden check until it is restored`() {
        // given: a database check that passes, and an override that makes it fail
        val probe = probe(ReadinessCheck("database") { true })
        val restore = probe.override("database") { false }

        // when: the probe is evaluated while the override is in place
        val overridden = probe.evaluate()

        // then: the database check is failing
        assertThat(overridden.failing).`as`("the override replaces the check").containsExactly("database")

        // when: the override is restored
        restore()
        val restored = probe.evaluate()

        // then: the original check answers again
        assertThat(restored.status).`as`("the restored check passes").isEqualTo("UP")
    }

}
