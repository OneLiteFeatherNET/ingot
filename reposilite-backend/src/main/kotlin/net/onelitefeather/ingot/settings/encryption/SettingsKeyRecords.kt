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

import com.reposilite.configuration.ConfigurationFacade

/**
 * Reads and writes the key record of the database. The record lives next to the shared settings in the configuration table, under
 * its own name, so no stored row of the settings changes shape. An empty record means that no key is recorded.
 */
class SettingsKeyRecords(private val configurationFacade: ConfigurationFacade) {

    companion object {
        const val NAME = "settings-key"
    }

    fun read(): StoredSettingsKey? =
        configurationFacade.findConfiguration(NAME)?.let { StoredSettingsKey.parse(it) }

    /**
     * Stores [record] when no record exists yet, and returns the record that is stored afterwards. Two instances that adopt a key at the
     * same time agree on the one that stored first.
     */
    fun adopt(record: StoredSettingsKey): StoredSettingsKey? =
        StoredSettingsKey.parse(configurationFacade.findOrCreateConfiguration(NAME) { record.serialize() })

    fun write(record: StoredSettingsKey) {
        configurationFacade.saveConfiguration(NAME, record.serialize())
    }

    fun clear() {
        configurationFacade.saveConfiguration(NAME, "")
    }

}
