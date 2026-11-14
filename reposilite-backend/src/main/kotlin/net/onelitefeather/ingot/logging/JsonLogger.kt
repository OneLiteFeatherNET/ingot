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

package net.onelitefeather.ingot.logging

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.journalist.Channel
import com.reposilite.journalist.ChannelIntention
import com.reposilite.journalist.backend.DefaultLogger
import java.io.PrintStream
import java.time.Clock
import java.time.format.DateTimeFormatter

/**
 * The console journalist of `INGOT_LOG_FORMAT=json`. It writes one JSON object per line with `time` (ISO 8601 in UTC), `level`
 * and `message`, plus `trace_id` and `span_id` while a span is visible. Colour codes are removed from the message, because a
 * JSON line is read by a collector and not by a terminal. Negative channels go to [err], as the text console does.
 */
class JsonLogger(
    private val out: PrintStream,
    private val err: PrintStream,
    threshold: Channel,
    private val clock: Clock,
    private val traceContext: TraceContext,
) : DefaultLogger(threshold) {

    override fun internalLog(channel: Channel, message: String) {
        val stream = if (channel.intention == ChannelIntention.NEGATIVE) err else out
        stream.println(line(channel, message))
    }

    /** The console writer ends every rendered message with the line separator, which the JSON line does not carry. */
    private fun line(channel: Channel, message: String): String {
        val fields = linkedMapOf(
            "time" to DateTimeFormatter.ISO_INSTANT.format(clock.instant()),
            "level" to channel.name,
            "message" to ANSI_SEQUENCE.replace(message, "").removeSuffix(System.lineSeparator()),
        )
        traceContext.current()?.let { ids ->
            fields["trace_id"] = ids.traceId
            fields["span_id"] = ids.spanId
        }
        return MAPPER.writeValueAsString(fields)
    }

    private companion object {
        val MAPPER = ObjectMapper()

        /** A control sequence introduced by ESC, such as the colour codes of the console. */
        val ANSI_SEQUENCE = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
    }

}
