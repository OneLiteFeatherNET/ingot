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

import com.reposilite.maven.ResolutionCache.Origin
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

internal class ResolutionCacheMaxAgeTest {

    /** A clock the test moves by hand, so no test reads the system time. */
    private class HandMovedClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    private val start: Instant = Instant.parse("2026-11-08T10:00:00Z")
    private val remote = Origin.Remote("https://repo1.maven.org")

    @Test
    fun `an entry is served until its maximum age has not yet passed`() {
        // given: a cache that keeps entries for sixty seconds, and a remote entry recorded at the start
        val clock = HandMovedClock(start)
        val cache = ResolutionCache(maxEntries = 4, maxAge = Duration.ofSeconds(60), clock = clock)
        cache.record("org/example/foo".toLocation(), authenticated = false, origin = remote)

        // when: the entry is looked up one second before its maximum age
        clock.now = start.plusSeconds(59)

        // then: the recorded route is still served
        assertThat(cache.lookup("org/example/foo/1.0/foo.jar".toLocation(), authenticated = false)).isEqualTo(remote)
    }

    @Test
    fun `an entry is dropped once its maximum age has passed`() {
        // given: a cache that keeps entries for sixty seconds, and a remote entry recorded at the start
        val clock = HandMovedClock(start)
        val cache = ResolutionCache(maxEntries = 4, maxAge = Duration.ofSeconds(60), clock = clock)
        cache.record("org/example/foo".toLocation(), authenticated = false, origin = remote)

        // when: the entry is looked up at its maximum age
        clock.now = start.plusSeconds(60)

        // then: no route is served and the expired entry is gone
        assertThat(cache.lookup("org/example/foo/1.0/foo.jar".toLocation(), authenticated = false)).isNull()
        assertThat(cache.size()).isZero()
    }

    @Test
    fun `a lookup does not extend the lifetime of an entry`() {
        // given: a cache that keeps entries for sixty seconds, and an entry that is used halfway through its lifetime
        val clock = HandMovedClock(start)
        val cache = ResolutionCache(maxEntries = 4, maxAge = Duration.ofSeconds(60), clock = clock)
        cache.record("org/example/foo".toLocation(), authenticated = false, origin = remote)
        clock.now = start.plusSeconds(50)
        cache.lookup("org/example/foo/1.0/foo.jar".toLocation(), authenticated = false)

        // when: the entry is looked up sixty seconds after it was recorded
        clock.now = start.plusSeconds(60)

        // then: the use did not renew it, so it has expired
        assertThat(cache.lookup("org/example/foo/1.0/foo.jar".toLocation(), authenticated = false)).isNull()
    }

    @Test
    fun `an entry without a maximum age never expires`() {
        // given: a cache without a maximum age and a remote entry recorded at the start
        val clock = HandMovedClock(start)
        val cache = ResolutionCache(maxEntries = 4, maxAge = null, clock = clock)
        cache.record("org/example/foo".toLocation(), authenticated = false, origin = remote)

        // when: the entry is looked up a year later
        clock.now = start.plus(Duration.ofDays(365))

        // then: the recorded route is still served
        assertThat(cache.lookup("org/example/foo/1.0/foo.jar".toLocation(), authenticated = false)).isEqualTo(remote)
    }

    @Test
    fun `without a bus the stored lifetime is unbounded`() {
        // given: no bus is configured, and the stored lifetime is not set
        // when: the maximum age is derived
        val maxAge = resolutionCacheMaxAge(storedSeconds = 0, clusterBusConfigured = false)

        // then: entries keep their current lifetime
        assertThat(maxAge).isNull()
    }

    @Test
    fun `without a bus a stored lifetime is still unbounded`() {
        // given: no bus is configured, and a lifetime of sixty seconds is stored
        // when: the maximum age is derived
        val maxAge = resolutionCacheMaxAge(storedSeconds = 60, clusterBusConfigured = false)

        // then: the stored value applies only in cluster mode, so entries keep their current lifetime
        assertThat(maxAge).isNull()
    }

    @Test
    fun `with a bus an unset lifetime is bounded to the cluster default of 300 seconds`() {
        // given: a bus is configured and the stored lifetime is 0
        // when: the maximum age is derived
        val maxAge = resolutionCacheMaxAge(storedSeconds = 0, clusterBusConfigured = true)

        // then: entries expire after 300 seconds
        assertThat(maxAge).isEqualTo(Duration.ofSeconds(300))
    }

    @Test
    fun `with a bus a stored lifetime replaces the cluster default`() {
        // given: a bus is configured and a lifetime of sixty seconds is stored
        // when: the maximum age is derived
        val maxAge = resolutionCacheMaxAge(storedSeconds = 60, clusterBusConfigured = true)

        // then: entries expire after the stored sixty seconds
        assertThat(maxAge).isEqualTo(Duration.ofSeconds(60))
    }
}
