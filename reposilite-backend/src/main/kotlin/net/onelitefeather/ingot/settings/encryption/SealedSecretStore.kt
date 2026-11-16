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

package net.onelitefeather.ingot.settings.encryption

/**
 * A store outside the shared settings that holds values sealed with the settings key, such as the seeds of authenticator codes.
 * `settings-reencrypt` calls [reseal] on every registered store, so that a key rotation reaches those values too.
 */
fun interface SealedSecretStore {

    /** Seals every value that the active key does not seal yet. Values that no configured key opens are reported and left as they are. */
    fun reseal(): SecretResealReport

}

/**
 * The outcome of one [SealedSecretStore.reseal]. [resealed] counts the values written with the active key, [current] the values that
 * already were sealed with it, and [unreadable] names every value that no configured key opens. Names never carry a secret.
 */
data class SecretResealReport(
    val resealed: Int,
    val current: Int,
    val unreadable: List<String>,
) {

    companion object {
        val EMPTY = SecretResealReport(resealed = 0, current = 0, unreadable = emptyList())
    }

}
