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

package com.reposilite.configuration.shared.infrastructure

import com.reposilite.console.CommandContext
import com.reposilite.console.CommandStatus.FAILED
import com.reposilite.console.api.ReposiliteCommand
import com.reposilite.configuration.shared.SharedConfigurationFacade
import net.onelitefeather.ingot.settings.encryption.SettingsKeyRecords
import net.onelitefeather.ingot.settings.encryption.SettingsSecretCipher
import picocli.CommandLine.Command

/**
 * Seals the stored secrets with the active key. Run once on one replica after the rotation, see the rotation steps in the
 * encryption how-to. The key record is then moved to the active key, so a replica that holds only the previous key stops at start.
 */
@Command(name = "settings-reencrypt", description = ["Seal the stored settings secrets with the active settings key"])
internal class SettingsReencryptCommand(
    private val sharedConfigurationFacade: SharedConfigurationFacade,
    private val records: SettingsKeyRecords,
    private val cipher: SettingsSecretCipher,
) : ReposiliteCommand {

    override fun execute(context: CommandContext) {
        val record = cipher.activeRecord
        if (record == null) {
            context.status = FAILED
            context.append("No settings key is active, set INGOT_SETTINGS_KEY or INGOT_SETTINGS_KEY_FILE first")
            return
        }

        val count = sharedConfigurationFacade.sealStoredSecrets(reason = "rotation")
        if (count == null) {
            context.status = FAILED
            context.append("The stored settings could not be written, the settings may be managed by a file or changed concurrently")
            return
        }

        records.write(record)
        context.append("Sealed $count stored secret values with the settings key id ${record.keyId}")
    }

}

/**
 * Writes the stored secrets in clear again, for a downgrade to a build without settings encryption. It needs the active key, so the
 * values are read before they are written back. Take an export first, see the encryption how-to.
 */
@Command(name = "settings-decrypt", description = ["Write the stored settings secrets in clear, to downgrade to a build without encryption"])
internal class SettingsDecryptCommand(
    private val sharedConfigurationFacade: SharedConfigurationFacade,
    private val records: SettingsKeyRecords,
    private val cipher: SettingsSecretCipher,
) : ReposiliteCommand {

    override fun execute(context: CommandContext) {
        if (!cipher.sealing) {
            context.status = FAILED
            context.append("No settings key is active, the stored values cannot be read to be written back in clear")
            return
        }

        val count = sharedConfigurationFacade.unsealStoredSecrets(reason = "decrypt")
        if (count == null) {
            context.status = FAILED
            context.append("The stored settings could not be written, the settings may be managed by a file or changed concurrently")
            return
        }

        records.clear()
        context.append("Wrote $count stored secret values in clear, remove the settings key before starting an older build")
    }

}
