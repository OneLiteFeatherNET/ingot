/*
 * Copyright (c) 2023 dzikoysk
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

import com.reposilite.journalist.Channel
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.plugin.api.Facade
import java.time.Clock
import java.time.Instant

/**
 * One aggregated failure. Occurrences that render identically are folded into the same record:
 * [timestamp] is the latest occurrence and [count] the number of occurrences.
 */
data class FailureRecord(
    val id: Long,
    val timestamp: Instant,
    val source: String,
    val type: String,
    val message: String?,
    val stacktrace: String,
    val count: Int
)

class FailureFacade(
    private val journalist: Journalist,
    private val clock: Clock = Clock.systemUTC()
) : Journalist, Facade {

    private val lock = Any()

    // key: the rendered failure as the console shows it
    private val failures = LinkedHashMap<String, FailureRecord>()
    private var lastId = 0L

    fun throwException(identifier: String, throwable: Throwable) =
        throwException(identifier, Channel.ERROR, throwable)

    private fun throwException(identifier: String, channel: Channel, throwable: Throwable) {
        logger.log(channel, identifier)
        logger.exception(channel, throwable)

        val rendered = arrayOf("failure $identifier", exceptionToString(throwable))
            .joinToString(separator = System.lineSeparator())
            .trim()
        val now = clock.instant()

        synchronized(lock) {
            val existing = failures[rendered]
            failures[rendered] = existing?.copy(timestamp = now, count = existing.count + 1)
                ?: FailureRecord(
                    id = ++lastId,
                    timestamp = now,
                    source = identifier,
                    type = throwable.javaClass.simpleName,
                    message = throwable.message,
                    stacktrace = exceptionToString(throwable),
                    count = 1
                )
        }
    }

    private fun exceptionToString(throwable: Throwable?): String =
        throwable?.let {
            arrayOf(
                "  by ${throwable.javaClass.simpleName}: ${throwable.message}",
                stacktraceToList(throwable),
                exceptionToString(throwable.cause)
            ).joinToString(separator = System.lineSeparator())
        } ?: ""

    private fun stacktraceToList(throwable: Throwable): String =
        throwable.stackTrace
            .take(5)
            .joinToString(prefix = "  at ", separator = System.lineSeparator())
            .takeIf { it.isNotEmpty() }
            ?: "<unknown stacktrace>"

    fun hasFailures() =
        synchronized(lock) { failures.isNotEmpty() }

    fun getFailures(): Collection<String> =
        synchronized(lock) { failures.keys.toList() }

    /**
     * Recorded failures, newest first.
     */
    fun getFailureRecords(): List<FailureRecord> =
        synchronized(lock) {
            failures.values.sortedWith(compareByDescending<FailureRecord> { it.timestamp }.thenByDescending { it.id })
        }

    fun clearFailures() =
        synchronized(lock) { failures.clear() }

    override fun getLogger(): Logger =
        journalist.logger

}
