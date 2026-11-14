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

/** The identifiers of the active span, as lowercase hexadecimal strings. */
data class TraceIds(val traceId: String, val spanId: String)

/** The source of the span that is active on the calling thread, or null when there is none. */
fun interface TraceContext {

    fun current(): TraceIds?

}

/**
 * Reads the active span through the OpenTelemetry API by reflection, so Ingot has no compile dependency on it. The probe
 * runs once, when the source is created. When the API is not visible to the class loader, the source reports no span
 * for the lifetime of the process, and the JSON lines carry no trace fields.
 */
class ReflectiveTraceContext private constructor(
    private val currentSpan: () -> Any?,
    private val spanContext: (Any) -> Any?,
    private val isValid: (Any) -> Boolean,
    private val traceId: (Any) -> String,
    private val spanId: (Any) -> String,
) : TraceContext {

    override fun current(): TraceIds? =
        try {
            val span = currentSpan() ?: return null
            val context = spanContext(span) ?: return null
            if (!isValid(context)) null else TraceIds(traceId = traceId(context), spanId = spanId(context))
        } catch (exception: RuntimeException) {
            // A span source that fails must not fail the log line that asked for it
            null
        }

    companion object {

        private const val SPAN = "io.opentelemetry.api.trace.Span"

        /** The source of the class loader, or the one that reports no span when the API cannot be reached. */
        fun create(classLoader: ClassLoader = ReflectiveTraceContext::class.java.classLoader): TraceContext =
            try {
                val spanClass = Class.forName(SPAN, false, classLoader)
                val current = spanClass.getMethod("current")
                val contextOf = spanClass.getMethod("getSpanContext")
                val contextClass = contextOf.returnType
                val valid = contextClass.getMethod("isValid")
                val trace = contextClass.getMethod("getTraceId")
                val span = contextClass.getMethod("getSpanId")
                ReflectiveTraceContext(
                    currentSpan = { current.invoke(null) },
                    spanContext = { contextOf.invoke(it) },
                    isValid = { valid.invoke(it) as Boolean },
                    traceId = { trace.invoke(it) as String },
                    spanId = { span.invoke(it) as String },
                )
            } catch (exception: ReflectiveOperationException) {
                NONE
            } catch (exception: LinkageError) {
                NONE
            }

        private val NONE = TraceContext { null }

    }

}
