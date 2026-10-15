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

package com.reposilite.maven

import com.reposilite.maven.RepositoryVisibility.HIDDEN
import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.toErrorResponse
import com.reposilite.shared.unauthorizedError
import com.reposilite.storage.api.Location
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenIdentifier
import net.onelitefeather.ingot.identity.model.Action
import io.javalin.http.HttpStatus.FORBIDDEN
import panda.std.Result

internal class RepositorySecurityProvider(private val accessTokenFacade: AccessTokenFacade) {

    fun canAccessRepository(accessToken: AccessTokenIdentifier?, repository: Repository): Boolean =
        when (repository.visibility) {
            PUBLIC -> true
            HIDDEN, PRIVATE -> canSeeRepository(accessToken, repository)
        }

    /**
     * Decides whether a repository appears in the repository list. Unlike the root directory listing,
     * a HIDDEN repository is listed to managers only, even for a token that has a route to it.
     */
    fun canListRepository(accessToken: AccessTokenIdentifier?, repository: Repository): Boolean =
        when (repository.visibility) {
            PUBLIC -> true
            PRIVATE -> canSeeRepository(accessToken, repository)
            HIDDEN -> accessToken?.let { accessTokenFacade.canListHidden(it, repository.name) } ?: false
        }

    private fun canSeeRepository(accessToken: AccessTokenIdentifier?, repository: Repository): Boolean =
        accessToken?.let { accessTokenFacade.canSee(it, "/${repository.name}") } ?: false

    fun canAccessResource(accessToken: AccessTokenIdentifier?, repository: Repository, gav: Location): Result<Unit, ErrorResponse> =
        when (repository.visibility) {
            PUBLIC -> Result.ok(Unit)
            HIDDEN -> Result.ok(Unit)
            PRIVATE -> hasPermissionTo(accessToken, repository, gav, Action.READ)
        }

    fun canBrowseResource(accessToken: AccessTokenIdentifier?, repository: Repository, gav: Location): Result<Unit, ErrorResponse> =
        when (repository.visibility) {
            PUBLIC -> Result.ok(Unit)
            HIDDEN -> hasPermissionTo(accessToken, repository, gav, Action.READ)
            PRIVATE -> hasPermissionTo(accessToken, repository, gav, Action.READ)
        }

    fun canModifyResource(accessToken: AccessTokenIdentifier?, repository: Repository, gav: Location): Boolean =
        hasPermissionTo(accessToken, repository, gav, Action.WRITE).isOk

    /** Deleting needs DELETE, which legacy write tokens carry as they did under WRITE, and MAINTAIN and above. */
    fun canDeleteResource(accessToken: AccessTokenIdentifier?, repository: Repository, gav: Location): Boolean =
        hasPermissionTo(accessToken, repository, gav, Action.DELETE).isOk

    private fun hasPermissionTo(accessToken: AccessTokenIdentifier?, repository: Repository, gav: Location, action: Action): Result<Unit, ErrorResponse> =
        accessToken
            ?.let {
                Result.`when`(accessTokenFacade.hasAccessTo(accessToken, "/${repository.name}/$gav", action),
                    { },
                    { FORBIDDEN.toErrorResponse("You must be the token owner or a manager to access this.") }
                )
            }
            ?: unauthorizedError("You need to provide credentials.")

}
