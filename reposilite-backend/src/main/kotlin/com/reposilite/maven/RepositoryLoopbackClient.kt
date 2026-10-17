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

import com.reposilite.auth.AuthenticationFacade
import com.reposilite.auth.api.Credentials
import com.reposilite.maven.api.LookupRequest
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.notFound
import com.reposilite.shared.http.AuthenticationMethod.LOOPBACK_LINK
import com.reposilite.shared.http.RemoteClient
import com.reposilite.shared.http.RemoteCredentials
import com.reposilite.shared.toErrorResponse
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.Location
import com.reposilite.storage.api.toLocation
import com.reposilite.token.AccessTokenIdentifier
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.UNAUTHORIZED
import panda.std.Option
import panda.std.Result
import java.io.InputStream

internal class RepositoryLoopbackClient(
    private val authenticationFacade: AuthenticationFacade,
    private val repositoryService: RepositoryService,
    private val repositoryName: String
) : RemoteClient {

    override fun head(uri: String, credentials: RemoteCredentials?, connectTimeoutInSeconds: Int, readTimeoutInSeconds: Int): Result<FileDetails, ErrorResponse> =
        repositoryService.findDetails(
            LookupRequest(
                accessToken = credentials.toAccessToken(),
                repository = repositoryName,
                gav = toGav(uri)
            )
        ).mapErr { it.refusedAsMissing() }

    override fun get(uri: String, credentials: RemoteCredentials?, connectTimeoutInSeconds: Int, readTimeoutInSeconds: Int): Result<InputStream, ErrorResponse> =
        repositoryService.findFile(
            LookupRequest(
                accessToken = credentials.toAccessToken(),
                repository = repositoryName,
                gav = toGav(uri)
            )
        ).map { (_, content) -> content }.mapErr { it.refusedAsMissing() }

    private fun RemoteCredentials?.toAccessToken(): AccessTokenIdentifier? =
        Option.of(this)
            .toResult(UNAUTHORIZED.toErrorResponse("Missing credentials"))
            .filter({ it.method == LOOPBACK_LINK }, { UNAUTHORIZED.toErrorResponse() })
            .flatMap { authenticationFacade.authenticateByCredentials(Credentials(ip = "loopback", name = it.login, secret = it.password)) }
            .fold(
                { it.identifier },
                { null }
            )

    /**
     * A member that refuses the mirror identity of this reference does not hold the file for the group. The refusal is about
     * the instance and not about the caller who browses the group, so it is answered as missing: a 401 or 403 here would
     * reach the browser as a refused session, and the browser would end a session that is still valid.
     */
    private fun ErrorResponse.refusedAsMissing(): ErrorResponse =
        if (status == UNAUTHORIZED.code || status == FORBIDDEN.code) notFound("Cannot find '$repositoryName' with the mirror identity") else this

    private fun toGav(uri: String): Location =
        uri.substring(repositoryName.length + 1).toLocation()

}
