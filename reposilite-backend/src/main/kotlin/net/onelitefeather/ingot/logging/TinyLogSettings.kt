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

/** The tinylog configuration without the rolling file writer, selected when `INGOT_LOG_FILE=false`. */
internal const val TINYLOG_CONSOLE_CONFIGURATION = "tinylog-console.properties"

/**
 * Passes the file writer switch and the package levels to tinylog as system properties. tinylog reads its configuration once,
 * when it is first used, so this runs before the first logger is created. With the variables unset it changes nothing.
 *
 * The file writer is removed by selecting a configuration without it, because an empty writer name would make tinylog print
 * an internal error line on the console. The package levels are tinylog's own `level@` entries.
 */
fun LogSettings.applyToTinyLog() {
    if (!file) {
        System.setProperty("tinylog.configuration", TINYLOG_CONSOLE_CONFIGURATION)
    }
    if (format == LogFormat.JSON) {
        // The console writer renders the message with its time and level; the JSON line carries those in fields of its own
        System.setProperty("tinylog.writerJournalist.format", "{message}")
    }
    packageLevels.forEach { (pkg, level) -> System.setProperty("tinylog.level@$pkg", level) }
}
