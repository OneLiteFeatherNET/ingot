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

import com.reposilite.console.api.ExecutionResponse
import kotlin.concurrent.withLock

internal class PreparedCommand(
    val redactedCommand: String,
    private val command: String,
    private val commandExecutor: CommandExecutor
) {

    fun execute(): ExecutionResponse =
        commandExecutor.execute(command)

    internal fun executePlain(): PlainExecution =
        commandExecutor.executePlain(command)

    fun isDangerous(): Boolean =
        commandExecutor.isDangerous(command)

    /**
     * Runs the command in structured mode. A dangerous command is held, and nothing runs, unless [confirmed] is set.
     */
    fun executeStructured(confirmed: Boolean): StructuredAnswer =
        // The check and the run parse the same line, so both happen under the lock that keeps another call from parsing in between.
        commandExecutor.commandLock.withLock {
            val held = !confirmed && isDangerous()
            onClassified()
            if (held) {
                ConfirmationNeeded(command = command.trim().substringBefore(' '), reason = commandExecutor.reasonOf(command))
            } else {
                StructuredRun(commandExecutor.executeStructured(command))
            }
        }

    /**
     * Called between the check and the run of a structured call, while the command lock is held. Internal, so that a
     * test can park a call in that gap and observe that no other call enters it.
     */
    internal var onClassified: () -> Unit = {}

}
