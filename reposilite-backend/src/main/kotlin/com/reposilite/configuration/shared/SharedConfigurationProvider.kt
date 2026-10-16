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

package com.reposilite.configuration.shared

import com.reposilite.configuration.VersionedConfiguration

interface SharedConfigurationProvider {

    fun updateConfiguration(content: String)

    fun fetchConfiguration(): String

    /**
     * The stored document together with the revision it was read at. Providers without shared storage have no revisions.
     */
    fun fetchVersionedConfiguration(): VersionedConfiguration =
        VersionedConfiguration(content = fetchConfiguration(), revision = null)

    /**
     * Stores [content] only while the stored document still has [expectedRevision], a null revision meaning that nothing
     * is stored yet. Returns false when another writer has stored a newer document since it was read, in which case
     * nothing is written. Providers without shared storage cannot be written concurrently and always accept the write.
     */
    fun compareAndUpdateConfiguration(content: String, expectedRevision: Long?): Boolean {
        updateConfiguration(content)
        return true
    }

    fun isUpdateRequired(): Boolean

    fun isMutable(): Boolean

    fun name(): String

}
