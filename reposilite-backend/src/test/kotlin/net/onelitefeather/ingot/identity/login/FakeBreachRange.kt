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

package net.onelitefeather.ingot.identity.login

/**
 * A range service that answers from memory and records every prefix it was asked for. No test reaches the network through
 * it; the production client is only built, never sent, in the tests.
 */
internal class FakeBreachRange(
    private val answers: Map<String, List<String>> = emptyMap(),
    private val unavailable: Boolean = false,
) : BreachRangeClient {

    val prefixes = mutableListOf<String>()

    override fun suffixes(rangeUrl: String, prefix: String): List<String> {
        prefixes += prefix
        if (unavailable) {
            throw BreachRangeUnavailable("the fake range service does not answer")
        }
        return answers[prefix].orEmpty()
    }

    companion object {

        /** An answer in which [password] is listed as breached, in the form of the range API: suffix and count. */
        fun listing(password: String): Map<String, List<String>> {
            val hash = PasswordBreachCheck.sha1Hex(password)
            return mapOf(hash.take(5) to listOf("${hash.drop(5)}:42"))
        }

    }

}
