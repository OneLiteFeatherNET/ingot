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

package net.onelitefeather.ingot.identity.login.mfa

import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore
import net.onelitefeather.ingot.identity.login.storage.SeedReplacement
import net.onelitefeather.ingot.settings.encryption.SealedSecretStore
import net.onelitefeather.ingot.settings.encryption.SecretResealReport

/**
 * Re-seals the authenticator seeds with the active settings key, for `settings-reencrypt`. The seeds are read in batches by id, and
 * each batch of writes is one transaction. A seed that no configured key opens is named by its factor id and left unchanged, it is
 * never deleted: its owner has to enrol the authenticator again, or the key it was sealed with has to be configured once more.
 */
internal class SecondFactorSeedResealer(
    private val store: SecondFactorStore,
    private val seeds: SeedCipher,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) : SealedSecretStore {

    init {
        require(batchSize > 0) { "The batch size must be positive, found $batchSize" }
    }

    override fun reseal(): SecretResealReport {
        val activeKeyId = seeds.activeKeyId ?: return SecretResealReport.EMPTY
        var resealed = 0
        var current = 0
        val unreadable = mutableListOf<String>()
        var afterId = 0
        while (true) {
            val batch = store.sealedSeedsAfter(afterId, batchSize)
            if (batch.isEmpty()) {
                break
            }
            afterId = batch.last().id

            val replacements = mutableListOf<SeedReplacement>()
            for (row in batch) {
                val envelope = checkNotNull(row.secret)
                if (envelope.split(':').getOrNull(2) == activeKeyId) {
                    current++
                    continue
                }
                val seed = runCatching { seeds.open(row.factorKey, envelope) }.getOrNull()
                if (seed == null) {
                    unreadable += "second factor ${row.id}"
                    continue
                }
                replacements += SeedReplacement(id = row.id, expected = envelope, sealed = seeds.seal(row.factorKey, seed))
            }
            if (replacements.isNotEmpty()) {
                resealed += store.replaceSeeds(replacements)
            }
        }
        return SecretResealReport(resealed = resealed, current = current, unreadable = unreadable)
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 100
    }

}
