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

package net.onelitefeather.ingot.readonly

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.configuration.ConfigurationFacade

/** The stored read-only state and the revision it was read at. A null [revision] means that nothing is stored. */
data class StoredReadOnly(val enabled: Boolean, val revision: Long?)

/**
 * The shared place of the read-only state. The database is the authority, so every replica reads the same value.
 */
interface ReadOnlyStore {

    /** @return the stored state, or null when no state has been stored yet */
    fun read(): StoredReadOnly?

    /**
     * Stores [enabled] only while the stored revision still equals [expectedRevision], null meaning that nothing is stored.
     *
     * @return false when another writer changed the state since it was read
     */
    fun compareAndSave(expectedRevision: Long?, enabled: Boolean): Boolean
}

/**
 * Keeps the read-only state as one entry of the existing configuration store, so no schema change is needed.
 */
class ConfigurationReadOnlyStore(private val configuration: ConfigurationFacade) : ReadOnlyStore {

    override fun read(): StoredReadOnly? {
        val versioned = configuration.findVersionedConfiguration(ENTRY) ?: return null
        val enabled = DEFAULT_OBJECT_MAPPER.readTree(versioned.content).path("enabled").asBoolean(false)
        return StoredReadOnly(enabled = enabled, revision = versioned.revision)
    }

    override fun compareAndSave(expectedRevision: Long?, enabled: Boolean): Boolean =
        configuration.compareAndSaveConfiguration(
            name = ENTRY,
            expectedRevision = expectedRevision,
            configuration = DEFAULT_OBJECT_MAPPER.writeValueAsString(mapOf("enabled" to enabled)),
        )

    companion object {

        /** The name of the entry in the configuration store. */
        const val ENTRY = "ingot.read-only"
    }
}
