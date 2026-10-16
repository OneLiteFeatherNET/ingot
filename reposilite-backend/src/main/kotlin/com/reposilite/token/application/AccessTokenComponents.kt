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

package com.reposilite.token.application

import com.reposilite.journalist.Journalist
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.PluginComponents
import com.reposilite.shared.EntityTagSigner
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.AccessTokenRepository
import com.reposilite.token.ExportService
import com.reposilite.token.infrastructure.InMemoryAccessTokenRepository
import com.reposilite.token.infrastructure.SqlAccessTokenRepository
import net.onelitefeather.ingot.identity.storage.IdentityAccessTokenRepository
import net.onelitefeather.ingot.identity.storage.IdentityStore
import net.onelitefeather.ingot.identity.storage.LegacyAccessTokenMigration
import net.onelitefeather.ingot.identity.storage.PermissionNodeMigration
import org.jetbrains.exposed.v1.jdbc.Database

class AccessTokenComponents(
    private val journalist: Journalist,
    private val database: Database?,
    private val runMigrations: Array<String> = emptyArray()
) : PluginComponents {

    private fun temporaryRepository(): AccessTokenRepository =
        InMemoryAccessTokenRepository()

    /**
     * Persistent tokens live in the identity tables. The legacy repository is still constructed first: it creates and
     * repairs the legacy tables, which the migration reads. Those tables are never written again.
     */
    private fun persistentRepository(): AccessTokenRepository =
        when (database) {
            null -> InMemoryAccessTokenRepository()
            else -> {
                SqlAccessTokenRepository(database, journalist, runMigrations)
                val store = IdentityStore(database)
                val outcome = LegacyAccessTokenMigration(database, journalist).migrate()
                journalist.logger.debug("Identity | access token migration: $outcome")
                PermissionNodeMigration(database, journalist).migrate()
                IdentityAccessTokenRepository(store)
            }
        }

    private fun exportService(): ExportService =
        ExportService()

    fun accessTokenFacade(
        temporaryRepository: AccessTokenRepository = temporaryRepository(),
        persistentRepository: AccessTokenRepository = persistentRepository(),
        exportService: ExportService = exportService(),
        extensions: Extensions = Extensions(journalist),
        entityTags: EntityTagSigner = EntityTagSigner.ephemeral(),
        bcryptStrength: Int = AccessTokenSecurityProvider.DEFAULT_BCRYPT_STRENGTH,
    ): AccessTokenFacade =
        AccessTokenFacade(
            journalist = journalist,
            temporaryRepository = temporaryRepository,
            persistentRepository = persistentRepository,
            exportService = exportService,
            extensions = extensions,
            entityTags = entityTags,
            bcryptStrength = bcryptStrength,
        )

}
