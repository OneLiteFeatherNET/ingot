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

import com.reposilite.console.CommandStatus
import com.reposilite.console.OutputLine

/** The version 1 frames the server sends. Each one is a JSON object whose `type` names it. */
internal data class WelcomeFrame(
    val protocol: Int,
    /** The boot id of this process. A client compares it with the boot it resumes from. */
    val boot: String? = null,
    /** The last log sequence number of this boot at the time of the welcome. */
    val seq: Long? = null,
    /** `resumed` when the server replays after the resumed number, `full` when it replays the whole cache, null without a resume. */
    val replay: String? = null,
    val type: String = "welcome",
)

/** One log line. [seq] is unique within the boot, [node] names the instance that logged it, and [dropped] counts the lines lost before it. */
internal data class LogFrame(
    val seq: Long,
    val node: String,
    val level: String,
    val text: String,
    val dropped: Int,
    val type: String = "log",
)

internal data class ErrorFrame(
    val code: String,
    val message: String,
    val id: String? = null,
    val retryable: Boolean? = null,
    val supported: List<Int>? = null,
    val type: String = "error",
)

internal data class ResultFrame(
    val id: String,
    val status: CommandStatus,
    val exitCode: Int,
    val output: List<OutputLine>,
    val data: Any?,
    val node: String,
    val confirmed: Boolean,
    val type: String = "result",
)

internal data class ConfirmFrame(val id: String, val command: String, val reason: String, val type: String = "confirm")
