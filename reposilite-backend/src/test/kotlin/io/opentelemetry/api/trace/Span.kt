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

package io.opentelemetry.api.trace

/**
 * A stand-in with the shape of the OpenTelemetry API, only for the reflective trace source under test. The real API is not a
 * compile dependency of Ingot. It always reports the same valid span, so it holds no state between tests.
 */
class Span private constructor() {

    fun getSpanContext(): SpanContext = SpanContext

    companion object {
        @JvmStatic
        fun current(): Span = Span()
    }

}

object SpanContext {

    fun isValid(): Boolean = true

    fun getTraceId(): String = "4bf92f3577b34da6a3ce929d0e0e4736"

    fun getSpanId(): String = "00f067aa0ba902b7"

}
