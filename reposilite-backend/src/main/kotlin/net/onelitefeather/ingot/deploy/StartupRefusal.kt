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

package net.onelitefeather.ingot.deploy

/**
 * What the process prints on standard error and the exit code it ends with when the start is refused.
 */
data class StartupRefusal(
    val line: String,
    val exitCode: Int,
)

/**
 * The refusal of an unknown deploy target as one console line, with the exit code of a failed start.
 */
fun UnknownDeployTargetException.toStartupRefusal(): StartupRefusal =
    StartupRefusal(line = "Ingot did not start: $message", exitCode = STARTUP_FAILURE_EXIT_CODE)

private const val STARTUP_FAILURE_EXIT_CODE = 1
