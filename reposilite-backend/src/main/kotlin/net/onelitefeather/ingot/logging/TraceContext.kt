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

import io.opentelemetry.api.trace.Span

/** The identifiers of the active span, as lowercase hexadecimal strings. */
data class TraceIds(val traceId: String, val spanId: String)

/** The source of the span that is active on the calling thread, or null when there is none. */
fun interface TraceContext {

    fun current(): TraceIds?

}

/**
 * Reads the span that is current on the calling thread through the OpenTelemetry API. Under the Java agent the agent's spans
 * are visible here, because the agent bridges them to the API that the server ships. Without an active span it reports none.
 */
object OpenTelemetryTraceContext : TraceContext {

    override fun current(): TraceIds? {
        val context = Span.current().spanContext
        return if (context.isValid) TraceIds(traceId = context.traceId, spanId = context.spanId) else null
    }

}
