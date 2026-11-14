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

package net.onelitefeather.ingot.search.http

/** One step of a write plan: a bulk body, or one item that must be sent on its own. */
sealed interface BulkStep<out T> {

    /** A bulk body of [actions] actions, in the order they were planned. */
    data class Bulk(val body: String, val actions: Int) : BulkStep<Nothing>

    /** An item that cannot be a bulk action, sent on its own so that the order of the items is kept. */
    data class Single<out T>(val item: T) : BulkStep<T>

}

/**
 * Groups the items of a batch into NDJSON bodies of the `_bulk` API (design D4 of the search plugins). A body holds at most
 * [maxActions] actions and at most [maxBytes] bytes, and the order of the items is kept. An item that [lines] maps to null ends the
 * body before it and becomes a [BulkStep.Single], so that the actions are applied in the order they were given.
 */
object NdjsonBulk {

    /** The largest body of one bulk request (design D4). */
    const val MAX_BYTES: Int = 5 * 1024 * 1024

    /**
     * [lines] returns the lines of one bulk action: the action line and, for an upsert, the document line. Each line is written
     * with a line feed after it. A null result marks an item that is not a bulk action.
     */
    fun <T> plan(items: List<T>, maxActions: Int, maxBytes: Int = MAX_BYTES, lines: (T) -> List<String>?): List<BulkStep<T>> {
        val steps = mutableListOf<BulkStep<T>>()
        val body = StringBuilder()
        var count = 0
        var bytes = 0

        fun flush() {
            if (count > 0) steps.add(BulkStep.Bulk(body.toString(), count))
            body.setLength(0)
            count = 0
            bytes = 0
        }

        for (item in items) {
            val itemLines = lines(item)
            if (itemLines == null) {
                flush()
                steps.add(BulkStep.Single(item))
                continue
            }
            val size = itemLines.sumOf { line -> line.toByteArray(Charsets.UTF_8).size + 1 }
            if (count > 0 && (count == maxActions || bytes + size > maxBytes)) {
                flush()
            }
            itemLines.forEach { line -> body.append(line).append('\n') }
            count += 1
            bytes += size
        }
        flush()
        return steps
    }

}
