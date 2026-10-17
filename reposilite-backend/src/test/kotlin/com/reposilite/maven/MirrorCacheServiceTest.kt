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

package com.reposilite.maven

import com.reposilite.maven.infrastructure.InMemoryMirrorCacheLedger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

internal class MirrorCacheServiceTest {

    private val start: Instant = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = MovableClock(start)
    private val ledger = CountingLedger(InMemoryMirrorCacheLedger())
    private val cache = MirrorCacheService(ledger, clock)

    @Test
    fun `should record a directory that held no artifacts before the mirror stored into it`() {
        // when: a mirror stores the first file of a version directory
        cache.recordStored("central", "org/mirror/lib/2.0", holdsHostedArtifacts = false)

        // then: the version directory is a cached copy
        assertThat(cache.cachedVersions("central")).containsOnlyKeys("org/mirror/lib/2.0")
    }

    @Test
    fun `should report a dropped record when a deployment forgets a cached directory`() {
        // given: a cached copy of a version directory
        cache.recordStored("central", "org/mirror/lib/2.0", holdsHostedArtifacts = false)

        // when: the directory is forgotten, as a deployment into it does
        val dropped = cache.forgetVersion("central", "org/mirror/lib/2.0")

        // then: the drop is reported, so the index reorigins the directory
        assertThat(dropped).`as`("a record was dropped").isTrue()
    }

    @Test
    fun `should report no dropped record when the directory was not cached`() {
        // when: a directory without a record is forgotten
        val dropped = cache.forgetVersion("central", "org/mirror/lib/2.0")

        // then: nothing was dropped
        assertThat(dropped).`as`("a record was dropped").isFalse()
    }

    @Test
    fun `should not record a directory that already holds hosted artifacts`() {
        // when: a mirror stores a file into a directory that holds deployed artifacts
        cache.recordStored("mixed", "com/example/app/1.0", holdsHostedArtifacts = true)

        // then: the directory is not marked, so cleanup never considers it
        assertThat(cache.cachedVersions("mixed")).isEmpty()
    }

    @Test
    fun `should start the last access at the moment the copy was stored`() {
        // when: a copy is stored
        cache.recordStored("central", "org/mirror/lib/2.0", holdsHostedArtifacts = false)

        // then: the last access equals the store time
        assertThat(cache.cachedVersions("central")).containsEntry("org/mirror/lib/2.0", start)
    }

    @Test
    fun `should write the last access at most once within an hour`() {
        // given: a cached copy stored at the start
        cache.recordStored("central", "org/mirror/lib/2.0", holdsHostedArtifacts = false)

        // when: the copy is served after ten minutes and again after fifty minutes
        clock.advance(Duration.ofMinutes(10))
        cache.touch("central", "org/mirror/lib/2.0")
        clock.advance(Duration.ofMinutes(40))
        cache.touch("central", "org/mirror/lib/2.0")

        // then: only the first serve was written, the second one fell inside the hour
        assertThat(ledger.accessWrites).`as`("writes of the last access").isEqualTo(1)
        assertThat(cache.cachedVersions("central")).containsEntry("org/mirror/lib/2.0", start.plus(Duration.ofMinutes(10)))
    }

    @Test
    fun `should write the last access again once an hour has passed`() {
        // given: a cached copy whose last access was written at ten minutes
        cache.recordStored("central", "org/mirror/lib/2.0", holdsHostedArtifacts = false)
        clock.advance(Duration.ofMinutes(10))
        cache.touch("central", "org/mirror/lib/2.0")

        // when: the copy is served again after the hour has passed
        clock.advance(Duration.ofMinutes(61))
        cache.touch("central", "org/mirror/lib/2.0")

        // then: the last access moves forward
        assertThat(ledger.accessWrites).`as`("writes of the last access").isEqualTo(2)
        assertThat(cache.cachedVersions("central")).containsEntry("org/mirror/lib/2.0", start.plus(Duration.ofMinutes(71)))
    }

    @Test
    fun `should not write an access for a directory without a mirror record`() {
        // when: a hosted version is served
        cache.touch("mixed", "com/example/app/1.0")

        // then: nothing is recorded for it
        assertThat(cache.cachedVersions("mixed")).isEmpty()
    }

    @Test
    fun `should forget a deleted version directory and keep its siblings`() {
        // given: two cached versions of one artifact
        cache.recordStored("central", "org/mirror/lib/1.0", holdsHostedArtifacts = false)
        cache.recordStored("central", "org/mirror/lib/2.0", holdsHostedArtifacts = false)

        // when: version 1.0 is deleted
        cache.forgetVersion("central", "org/mirror/lib/1.0")

        // then: only 2.0 keeps its record
        assertThat(cache.cachedVersions("central")).containsOnlyKeys("org/mirror/lib/2.0")
    }

    @Test
    fun `should forget every version below a deleted artifact directory and no sibling with a similar name`() {
        // given: versions of two artifacts whose names share a prefix
        cache.recordStored("central", "org/mirror/lib/1.0", holdsHostedArtifacts = false)
        cache.recordStored("central", "org/mirror/lib-extra/1.0", holdsHostedArtifacts = false)
        cache.recordStored("central", "org/other/1.0", holdsHostedArtifacts = false)

        // when: the directory of the artifact lib is deleted
        cache.forgetSubtree("central", "org/mirror/lib")

        // then: the versions of lib are gone, lib-extra and org/other keep their records
        assertThat(cache.cachedVersions("central"))
            .containsOnlyKeys("org/mirror/lib-extra/1.0", "org/other/1.0")
    }

    @Test
    fun `should keep the records of each repository apart`() {
        // when: a copy is stored in one repository
        cache.recordStored("central", "org/mirror/lib/2.0", holdsHostedArtifacts = false)

        // then: the other repository has no records
        assertThat(cache.cachedVersions("releases")).isEmpty()
    }

    @Test
    fun `should record the upstream host the copy was fetched from`() {
        // when: a copy is stored from a mirror host
        cache.recordStored("central", "org/mirror/lib/2.0", holdsHostedArtifacts = false, sourceHost = "mirror.example.org")

        // then: the directory keeps that host
        assertThat(ledger.sourceHost("central", "org/mirror/lib/2.0")).isEqualTo("mirror.example.org")
    }

    @Test
    fun `should keep the upstream host of the first copy when a later copy comes from another host`() {
        // given: a directory stored from the first host
        cache.recordStored("central", "org/mirror/lib/2.0", holdsHostedArtifacts = false, sourceHost = "first.example.org")

        // when: a later file of the same directory is fetched from a second host
        cache.recordStored("central", "org/mirror/lib/2.0", holdsHostedArtifacts = false, sourceHost = "second.example.org")

        // then: the directory keeps the first host
        assertThat(ledger.sourceHost("central", "org/mirror/lib/2.0")).isEqualTo("first.example.org")
    }

    @Test
    fun `should record a copy without a host when the upstream host is unknown`() {
        // when: a copy is stored without a host
        cache.recordStored("central", "org/mirror/lib/2.0", holdsHostedArtifacts = false)

        // then: the copy is recorded and carries no host
        assertThat(cache.cachedVersions("central")).containsOnlyKeys("org/mirror/lib/2.0")
        assertThat(ledger.sourceHost("central", "org/mirror/lib/2.0")).`as`("source host").isNull()
    }

    @Test
    fun `should not record a path that is too long for the ledger`() {
        // when: a copy with a path beyond the ledger limit is stored
        cache.recordStored("central", "a".repeat(MAX_PROVENANCE_PATH_LENGTH + 1), holdsHostedArtifacts = false)

        // then: it is not recorded, so it is never touched by cleanup
        assertThat(cache.cachedVersions("central")).isEmpty()
    }

}

/** A clock that only moves when the test says so. */
private class MovableClock(private var current: Instant) : Clock() {

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current

}

/** Counts the writes of the last access, so that the throttle can be observed. */
private class CountingLedger(private val delegate: MirrorCacheLedger) : MirrorCacheLedger by delegate {

    var accessWrites = 0
        private set

    override fun markAccessed(repository: String, path: String, now: Instant): Boolean {
        accessWrites++
        return delegate.markAccessed(repository, path, now)
    }

}
