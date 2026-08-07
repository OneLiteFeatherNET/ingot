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

package com.reposilite.plugin

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.api.Event
import com.reposilite.plugin.api.EventListener
import com.reposilite.plugin.api.Priorities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ExtensionsTest {

    private class RecordingEvent : Event {
        val calls = mutableListOf<String>()
    }

    private class RecordingListener(
        private val name: String,
        private val priority: Double
    ) : EventListener<RecordingEvent> {
        override fun onCall(event: RecordingEvent) {
            event.calls.add(name)
        }

        override fun priority(): Double = priority
    }

    private val journalist = object : Journalist {
        override fun getLogger(): Logger = PrintStreamLogger(System.out, System.err)
    }

    @Test
    fun `should call listeners in priority order regardless of registration order`() {
        // given: listeners registered in the opposite order of their priority
        val extensions = Extensions(journalist)
        extensions.registerEvent(RecordingEvent::class.java, RecordingListener("low", Priorities.LOW))
        extensions.registerEvent(RecordingEvent::class.java, RecordingListener("default", Priorities.DEFAULT))
        extensions.registerEvent(RecordingEvent::class.java, RecordingListener("high", Priorities.HIGH))

        // when: the event is emitted
        val event = extensions.emitEvent(RecordingEvent())

        // then: the highest priority listener ran first
        assertThat(event.calls).containsExactly("high", "default", "low")
    }

    @Test
    fun `should keep the order stable for listeners sharing a priority`() {
        // given: several listeners registered with the same priority
        val extensions = Extensions(journalist)
        extensions.registerEvent(RecordingEvent::class.java, RecordingListener("first", Priorities.DEFAULT))
        extensions.registerEvent(RecordingEvent::class.java, RecordingListener("second", Priorities.DEFAULT))
        extensions.registerEvent(RecordingEvent::class.java, RecordingListener("third", Priorities.DEFAULT))

        // when: the event is emitted
        val event = extensions.emitEvent(RecordingEvent())

        // then: they run in registration order, so a plugin can rely on it
        assertThat(event.calls).containsExactly("first", "second", "third")
    }

    @Test
    fun `should sort a listener registered after the first event was emitted`() {
        // given: an event that was already emitted once, so the listener list exists
        val extensions = Extensions(journalist)
        extensions.registerEvent(RecordingEvent::class.java, RecordingListener("low", Priorities.LOW))
        extensions.emitEvent(RecordingEvent())

        // when: a higher priority listener is registered afterwards and the event is emitted again
        extensions.registerEvent(RecordingEvent::class.java, RecordingListener("high", Priorities.HIGH))
        val event = extensions.emitEvent(RecordingEvent())

        // then: the late arrival still runs first
        assertThat(event.calls).containsExactly("high", "low")
    }

}
