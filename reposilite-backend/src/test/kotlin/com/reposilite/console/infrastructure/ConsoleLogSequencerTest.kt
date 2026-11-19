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
package com.reposilite.console.infrastructure

import com.reposilite.journalist.Channel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The sequencer stamps every line the console shows with a number unique within the boot, and keeps the last lines so a
 * resumed connection can replay them with the numbers they were stamped with.
 */
internal class ConsoleLogSequencerTest {

    @Test
    fun `stamps each line with a sequence number that is unique within the boot`() {
        // given: a sequencer with a listener
        val sequencer = ConsoleLogSequencer(capacity = 10)
        val seen = mutableListOf<ConsoleLogSequencer.StampedLine>()
        sequencer.subscribe(from = null) { seen += it }

        // when: three lines are logged
        sequencer.log(Channel.INFO, "first")
        sequencer.log(Channel.INFO, "second")
        sequencer.log(Channel.WARN, "third")

        // then: the numbers are one, two and three, all distinct
        assertThat(seen.map { it.seq }).`as`("sequence numbers").containsExactly(1L, 2L, 3L)
        assertThat(sequencer.lastSequence()).`as`("last sequence number").isEqualTo(3L)
    }

    @Test
    fun `a cached line keeps the sequence number it was stamped with when later lines follow`() {
        // given: three lines logged before anybody replays them
        val sequencer = ConsoleLogSequencer(capacity = 10)
        sequencer.log(Channel.INFO, "first")
        sequencer.log(Channel.INFO, "second")
        sequencer.log(Channel.INFO, "third")

        // when: a fourth line is logged and the whole cache is replayed
        sequencer.log(Channel.INFO, "fourth")
        val replayed = mutableListOf<ConsoleLogSequencer.StampedLine>()
        sequencer.subscribe(from = 0L) { replayed += it }

        // then: the first line still carries number one, and the replay matches the numbers stamped at logging time
        assertThat(replayed.map { it.seq to it.text }).`as`("replayed lines")
            .containsExactly(1L to "first", 2L to "second", 3L to "third", 4L to "fourth")
    }

    @Test
    fun `a replay after a sequence number holds only the lines after it`() {
        // given: four lines in the cache
        val sequencer = ConsoleLogSequencer(capacity = 10)
        repeat(4) { sequencer.log(Channel.INFO, "line-$it") }

        // when: a subscriber resumes after the second line
        val replayed = mutableListOf<ConsoleLogSequencer.StampedLine>()
        sequencer.subscribe(from = 2L) { replayed += it }

        // then: only the lines three and four are replayed
        assertThat(replayed.map { it.seq }).`as`("replayed sequence numbers").containsExactly(3L, 4L)
    }

    @Test
    fun `a subscriber without a replay point gets only the lines logged after it subscribed`() {
        // given: a line already in the cache
        val sequencer = ConsoleLogSequencer(capacity = 10)
        sequencer.log(Channel.INFO, "before")

        // when: a subscriber joins without a replay point, and a line is logged afterwards
        val seen = mutableListOf<ConsoleLogSequencer.StampedLine>()
        sequencer.subscribe(from = null) { seen += it }
        sequencer.log(Channel.INFO, "after")

        // then: only the line logged after the subscription arrives
        assertThat(seen.map { it.text }).`as`("live lines").containsExactly("after")
    }

    @Test
    fun `labels a line from another replica with the node that logged it`() {
        // given: a sequencer with a listener
        val sequencer = ConsoleLogSequencer(capacity = 10)
        val seen = mutableListOf<ConsoleLogSequencer.StampedLine>()
        sequencer.subscribe(from = null) { seen += it }

        // when: a line of another replica arrives and a line is logged here
        sequencer.appendRemote(Channel.INFO, origin = "ingot-1", message = "from the other replica")
        sequencer.log(Channel.INFO, "from this replica")

        // then: the remote line names its node, and the local line names none
        assertThat(seen.map { it.node to it.text }).`as`("node and text of each line")
            .containsExactly("ingot-1" to "from the other replica", null to "from this replica")
    }

    @Test
    fun `keeps only the newest lines beyond the capacity`() {
        // given: a sequencer of capacity two with three lines logged
        val sequencer = ConsoleLogSequencer(capacity = 2)
        repeat(3) { sequencer.log(Channel.INFO, "line-$it") }

        // when: the whole cache is replayed
        val replayed = mutableListOf<ConsoleLogSequencer.StampedLine>()
        sequencer.subscribe(from = 0L) { replayed += it }

        // then: the oldest line is gone and the numbers of the rest did not change
        assertThat(replayed.map { it.seq }).`as`("cached sequence numbers").containsExactly(2L, 3L)
    }

    @Test
    fun `a subscriber that unsubscribed receives no further lines`() {
        // given: a subscriber that is removed
        val sequencer = ConsoleLogSequencer(capacity = 10)
        val seen = mutableListOf<ConsoleLogSequencer.StampedLine>()
        val id = sequencer.subscribe(from = null) { seen += it }
        sequencer.unsubscribe(id)

        // when: a line is logged
        sequencer.log(Channel.INFO, "after the unsubscribe")

        // then: nothing reaches the removed subscriber
        assertThat(seen).`as`("lines after unsubscribe").isEmpty()
    }

}
