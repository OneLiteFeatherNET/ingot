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
package com.reposilite.console

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent

/** The longest client product name that an audit event keeps. */
private const val MAX_CLIENT_LENGTH = 64

/**
 * The audit event of a command that reached execution from a remote channel. [command] must be the redacted command line,
 * so that a sensitive command stores its name and the redaction marker only. The exit code 0 is a success.
 */
internal fun consoleCommandAudit(
    actor: AuditActor,
    command: String,
    channel: String,
    client: String?,
    confirmed: Boolean,
    exitCode: Int,
): AuditEvent =
    AuditEvent(
        action = AuditAction.CONSOLE_COMMAND_EXECUTED,
        actor = actor,
        details = mapOf(
            "command" to command,
            "channel" to channel,
            "client" to client?.take(MAX_CLIENT_LENGTH),
            "confirmed" to confirmed,
            "exitCode" to exitCode,
        ),
        success = exitCode == 0,
    )
