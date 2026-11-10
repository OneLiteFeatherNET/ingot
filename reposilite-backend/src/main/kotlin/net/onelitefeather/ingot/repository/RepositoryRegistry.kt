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

package net.onelitefeather.ingot.repository

import com.reposilite.maven.EffectiveRepositoryType
import com.reposilite.maven.RepositoryVisibility
import com.reposilite.plugin.api.Facade
import com.reposilite.storage.StorageProvider

/**
 * Read-only access to the repositories that exist, for the parts of Ingot that only need their names, visibility, format and storage.
 * Not yet stable for plugins: [RegisteredRepository] may gain members while the format SPI is experimental, which breaks implementors.
 */
interface RepositoryRegistry : Facade {

    /** The repository named [name], or null. Reads the live state, so a settings change is visible at once. */
    fun repository(name: String): RegisteredRepository?

    /** All repositories in configuration order. */
    fun repositories(): List<RegisteredRepository>

}

/** A repository as the rest of Ingot sees it. Not yet stable for plugins, see [RepositoryRegistry]. */
interface RegisteredRepository {

    val name: String

    val visibility: RepositoryVisibility

    /** The id of the format of this repository, `maven2` unless the repository names another installed format. */
    val format: String

    val effectiveType: EffectiveRepositoryType

    val storageProvider: StorageProvider

    /** Whether a mirror of this repository stores the files it proxies, so they are kept locally. */
    val storesProxiedFiles: Boolean

}
