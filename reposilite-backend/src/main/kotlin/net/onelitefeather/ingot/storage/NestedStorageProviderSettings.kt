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

package net.onelitefeather.ingot.storage

import com.reposilite.storage.StorageProviderSettings

/**
 * Settings that wrap the settings of another storage type, the [delegate]. Until the plugins have loaded, the delegate of a plugin type
 * is an unresolved block, so a check on its type or its namespace finds nothing. The server binds the delegate of these settings, see
 * `StorageFacade.bind`, before such a check runs.
 */
@ExperimentalIngotStorageApi
interface NestedStorageProviderSettings : StorageProviderSettings {

    val delegate: StorageProviderSettings

    /** A copy of these settings that wraps [delegate] instead of the current delegate. */
    fun withDelegate(delegate: StorageProviderSettings): StorageProviderSettings

}
