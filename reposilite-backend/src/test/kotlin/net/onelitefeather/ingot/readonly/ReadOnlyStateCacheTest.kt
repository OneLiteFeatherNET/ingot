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

package net.onelitefeather.ingot.readonly

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

internal class ReadOnlyStateCacheTest {

    /** A clock whose instant the test moves by hand, so that no test waits for the wall clock. */
    private class MovableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    /** A store that counts its reads and holds one stored state. */
    private class CountingStore(var stored: StoredReadOnly?) : ReadOnlyStore {
        var reads = 0
        override fun read(): StoredReadOnly? {
            reads++
            return stored
        }
        override fun compareAndSave(expectedRevision: Long?, enabled: Boolean): Boolean = error("not used here")
    }

    private val start = Instant.parse("2026-11-10T10:00:00Z")

    @Test
    fun `should not re-read the stored state within five seconds`() {
        // given: a cache that has read the stored state once
        val clock = MovableClock(start)
        val store = CountingStore(StoredReadOnly(enabled = false, revision = 1))
        val cache = ReadOnlyStateCache(clock, store, ReadOnlySetting(forced = false))
        cache.current()

        // when: the clock moves on by four and a half seconds and the state is asked again
        clock.now = start.plusMillis(4_500)
        cache.current()

        // then: the store was read only once
        assertThat(store.reads).`as`("reads of the store").isEqualTo(1)
    }

    @Test
    fun `should re-read the stored state after five seconds`() {
        // given: a cache that has read the stored state once
        val clock = MovableClock(start)
        val store = CountingStore(StoredReadOnly(enabled = false, revision = 1))
        val cache = ReadOnlyStateCache(clock, store, ReadOnlySetting(forced = false), maxAge = Duration.ofSeconds(5))
        cache.current()

        // when: the clock moves on by five seconds and the state is asked again
        clock.now = start.plusSeconds(5)
        cache.current()

        // then: the store was read a second time
        assertThat(store.reads).`as`("reads of the store").isEqualTo(2)
    }

    @Test
    fun `should pick up a change of the stored state once the cache has expired`() {
        // given: a cache that reported the state as off
        val clock = MovableClock(start)
        val store = CountingStore(StoredReadOnly(enabled = false, revision = 1))
        val cache = ReadOnlyStateCache(clock, store, ReadOnlySetting(forced = false))
        assertThat(cache.current().enabled).`as`("state before the change").isFalse()

        // when: another instance stores the state as on and five seconds pass
        store.stored = StoredReadOnly(enabled = true, revision = 2)
        clock.now = start.plusSeconds(5)

        // then: the instance reports read-only
        assertThat(cache.current().enabled).`as`("state after the change").isTrue()
    }

    @Test
    fun `should re-read at once when a change is announced`() {
        // given: a cache that reported the state as off and has not expired yet
        val clock = MovableClock(start)
        val store = CountingStore(StoredReadOnly(enabled = false, revision = 1))
        val cache = ReadOnlyStateCache(clock, store, ReadOnlySetting(forced = false))
        cache.current()

        // when: another instance stores the state as on and the change is announced, without any time passing
        store.stored = StoredReadOnly(enabled = true, revision = 2)
        cache.refresh()

        // then: the instance reports read-only without waiting for the five seconds
        assertThat(cache.current().enabled).`as`("state after the announcement").isTrue()
    }

    @Test
    fun `should report the forced state without reading the store`() {
        // given: the environment forces read-only
        val store = CountingStore(stored = null)
        val cache = ReadOnlyStateCache(MovableClock(start), store, ReadOnlySetting(forced = true))

        // when: the state is asked
        val state = cache.current()

        // then: the state is on, its source is the environment, and the store was not read
        assertThat(state.enabled).`as`("enabled").isTrue()
        assertThat(state.source).`as`("source").isEqualTo(ReadOnlySource.ENVIRONMENT)
        assertThat(store.reads).`as`("reads of the store").isZero()
    }

    @Test
    fun `should default to off when no state is stored`() {
        // given: nothing is stored yet
        val cache = ReadOnlyStateCache(MovableClock(start), CountingStore(stored = null), ReadOnlySetting(forced = false))

        // when / then: the state is off
        assertThat(cache.current().enabled).`as`("enabled").isFalse()
    }

}
