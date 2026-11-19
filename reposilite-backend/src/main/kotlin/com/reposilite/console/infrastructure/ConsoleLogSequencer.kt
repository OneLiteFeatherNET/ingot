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
import com.reposilite.journalist.backend.DefaultLogger

/**
 * Stamps every line the console shows with a sequence number that is unique within the boot, keeps the newest lines with
 * the numbers they were stamped with, and hands each new line to its subscribers. It is one of the loggers of the visible
 * logger, so only lines at the visible threshold reach it.
 */
internal class ConsoleLogSequencer(private val capacity: Int) : DefaultLogger(Channel.ALL) {

    /** A line with the number it was stamped with. [node] is null for a line this instance logged itself. */
    internal data class StampedLine(val seq: Long, val node: String?, val level: Channel, val text: String)

    /** Guards the cache, the last number and the subscribers, so a line reaches every subscriber in the order of its number. */
    private val lock = Any()
    private val cache = ArrayDeque<StampedLine>()
    private val subscribers = LinkedHashMap<Int, (StampedLine) -> Unit>()
    private var last = 0L
    private var nextSubscriberId = 0

    override fun internalLog(channel: Channel, message: String) {
        publish(channel, message, node = null)
    }

    /** Stamps a line of another replica, which carries [origin] as its node. */
    fun appendRemote(channel: Channel, origin: String, message: String) {
        publish(channel, message, node = origin)
    }

    private fun publish(channel: Channel, text: String, node: String?) {
        synchronized(lock) {
            val line = StampedLine(++last, node, channel, text)
            cache.addLast(line)
            while (cache.size > capacity) {
                cache.removeFirst()
            }
            subscribers.values.forEach { it(line) }
        }
    }

    /**
     * Subscribes [listener] to the lines stamped from now on. With [from], the cached lines after that number are replayed
     * first, under the same lock, so no line is missed or delivered twice. Returns the id for [unsubscribe].
     */
    fun subscribe(from: Long?, listener: (StampedLine) -> Unit): Int = synchronized(lock) {
        if (from != null) {
            cache.filter { it.seq > from }.forEach(listener)
        }
        val id = nextSubscriberId++
        subscribers[id] = listener
        id
    }

    fun unsubscribe(id: Int): Boolean = synchronized(lock) {
        subscribers.remove(id) != null
    }

    /** The number of the last line stamped in this boot, or 0 before the first line. */
    fun lastSequence(): Long = synchronized(lock) { last }

}
