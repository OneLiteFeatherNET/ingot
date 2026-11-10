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

package net.onelitefeather.ingot.nexusimport.definitions

import com.reposilite.audit.api.AuditActor
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import io.javalin.http.HttpStatus
import panda.std.asSuccess

/**
 * The production port: the `maven` domain of the shared configuration facade. The domain name is the title of [MavenSettings]
 * as the API spells it. A `412` becomes a precondition failure and a `409` the managed answer; anything else is an error.
 */
class SharedMavenDomain(
    private val facade: SharedConfigurationFacade,
    private val actor: AuditActor,
) : MavenDomainPort {

    override fun read(): DomainState {
        val snapshot = facade.getDomainSnapshot<MavenSettings>(NAME)
            ?: throw IllegalStateException("The $NAME settings domain does not exist")
        return DomainState(snapshot.settings.repositories.map { it.id }.toSet(), snapshot.etag)
    }

    override fun update(ifMatch: String, transform: (List<RepositorySettings>) -> List<RepositorySettings>): UpdateResult {
        val result = facade.updateDomain<MavenSettings>(NAME, ifMatch, actor) { current ->
            current.copy(repositories = transform(current.repositories)).asSuccess()
        } ?: throw IllegalStateException("The $NAME settings domain does not exist")
        return when {
            result.isOk -> UpdateResult.Stored
            result.error.status == HttpStatus.PRECONDITION_FAILED.code -> UpdateResult.PreconditionFailed
            result.error.status == HttpStatus.CONFLICT.code -> UpdateResult.ManagedExternally
            else -> throw IllegalStateException(result.error.message)
        }
    }

    private companion object {
        const val NAME = "maven"
    }
}
