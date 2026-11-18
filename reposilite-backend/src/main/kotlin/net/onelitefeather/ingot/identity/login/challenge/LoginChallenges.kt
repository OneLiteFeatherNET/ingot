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

@file:OptIn(ExperimentalIngotPluginApi::class)

package net.onelitefeather.ingot.identity.login.challenge

import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi

/** The `challenge` member of a login request, as far as it could be read. A missing or `null` member is no answer at all. */
sealed interface LoginChallengeAnswer {

    /** Not an object, or without a string `provider` or an object `answer`. */
    data object Malformed : LoginChallengeAnswer

    data class Given(val provider: String, val answer: Map<String, Any?>) : LoginChallengeAnswer

    companion object {

        /** Reads the member as the JSON mapper decoded it: an object is a [Map], a string a [String], `null` is no answer. */
        fun of(raw: Any?): LoginChallengeAnswer? {
            raw ?: return null
            val member = raw as? Map<*, *> ?: return Malformed
            val provider = member["provider"] as? String ?: return Malformed
            val answer = member["answer"] as? Map<*, *> ?: return Malformed
            return Given(provider, answer.entries.associate { (key, value) -> key.toString() to value })
        }

    }

}

/** The registered login challenge provider, at most one. */
internal class LoginChallenges(private val warn: (String) -> Unit) {

    @Volatile
    private var provider: LoginChallengeProvider? = null

    fun registered(): LoginChallengeProvider? = provider

    /** Keeps the first provider with an id that is not blank. Every later one is ignored with a warning naming both. */
    fun register(candidate: LoginChallengeProvider) {
        synchronized(this) {
            val current = provider
            when {
                candidate.id.isBlank() ->
                    warn("Login challenge provider ${candidate.javaClass.name} is ignored, its id is blank")
                current != null ->
                    warn("Login challenge provider '${candidate.id}' (${candidate.javaClass.name}) is ignored, '${current.id}' (${current.javaClass.name}) is already registered")
                else -> provider = candidate
            }
        }
    }

}
