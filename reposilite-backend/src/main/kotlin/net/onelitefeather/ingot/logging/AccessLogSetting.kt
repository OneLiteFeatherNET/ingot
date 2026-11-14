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

/**
 * The opt-in access record, one INFO line per request. Off by default, as it is in every earlier version. [nanoTime] is the
 * monotonic source of the duration; tests inject a counter, so no test reads the clock of the machine.
 */
data class AccessLogSetting(
    val enabled: Boolean,
    val nanoTime: () -> Long = System::nanoTime,
    /** Receives each record instead of the console, when set. Tests use it to read the records without the console. */
    val sink: ((String) -> Unit)? = null,
) {

    companion object {

        /** Only the value `true` switches the record on. Any other value keeps it off without refusing the start. */
        fun read(environment: Map<String, String> = System.getenv()): AccessLogSetting =
            AccessLogSetting(enabled = LogSettings.logVariable(environment, "ACCESS_LOG")?.second.equals("true", ignoreCase = true))

    }

}
