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

package net.onelitefeather.ingot.format

import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.Facade
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.api.Location
import com.reposilite.token.AccessTokenIdentifier
import io.javalin.http.Context
import panda.std.Result

/** Where a format is served. Every format so far lives under `/{repository}/<path>`. */
@ExperimentalIngotFormatApi
sealed interface FormatMount {

    /** The repository path, `/{repository}/<path>`, which Maven and every other format so far is served under. */
    data object RepositoryPath : FormatMount

}

/** Answers the requests of one format for the repositories of that format. */
@ExperimentalIngotFormatApi
fun interface RepositoryRequestHandler {

    fun handle(request: RepositoryRequest): Result<Unit, ErrorResponse>

}

/**
 * One request under a repository path, already checked for its authentication and authorization. The method is the method of
 * [ctx]. [path] is the location of the request inside the repository, and [by] names the caller for the audit trail.
 */
@ExperimentalIngotFormatApi
class RepositoryRequest(
    val ctx: Context,
    val identifier: AccessTokenIdentifier?,
    val repository: String,
    val path: Location,
    val by: String,
)

/** The services a format may use while it builds its handler. The server registers each of them as a facade. */
@ExperimentalIngotFormatApi
interface FormatContext {

    /** The facade of [type]. Looked up when the handler is built, so a facade registered later is found as well. */
    fun <F : Facade> facade(type: Class<F>): F

}

/** The format context of a server, backed by the facades its plugins registered. */
@ExperimentalIngotFormatApi
class ExtensionsFormatContext(private val extensions: Extensions) : FormatContext {

    override fun <F : Facade> facade(type: Class<F>): F =
        extensions.facade(type)

}
