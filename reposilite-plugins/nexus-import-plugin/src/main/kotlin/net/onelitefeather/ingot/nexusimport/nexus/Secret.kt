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

package net.onelitefeather.ingot.nexusimport.nexus

/**
 * A password held in memory only. It prints as asterisks, so it cannot reach a log line, an exception message or a
 * serialised response by accident, and [wipe] clears the characters when the job that holds it ends.
 */
class Secret(value: String) {

    private val chars: CharArray = value.toCharArray()

    /** The value, for the one place that needs it: the Authorization header. */
    fun reveal(): String = String(chars)

    fun wipe() = chars.fill('\u0000')

    override fun toString(): String = "********"
}
