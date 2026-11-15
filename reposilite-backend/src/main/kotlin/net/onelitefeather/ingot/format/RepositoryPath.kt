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

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequestError
import com.reposilite.storage.api.Location
import java.net.URLDecoder
import panda.std.Result

/**
 * The canonical form of a repository request path, `/{repository}/{location}`. A write is authorized on this path and
 * stored at the same repository and location, so no spelling of a path can be judged as one location and written to
 * another.
 */
internal data class RepositoryPath(val repository: String, val location: Location) {

    /** The path the access check judges, in the form every other repository check uses. */
    val authorizationPath: String
        get() = "/$repository/$location"

    companion object {

        private val encodedSlash = Regex("%2[fF]")

        /**
         * Canonicalizes the raw request path: refuses control characters and escaped slashes, percent-decodes once (a `+`
         * stays a `+`, as in the path parameters of the router), refuses backslashes and `.` and `..` segments, collapses
         * repeated slashes and splits off the repository. The location then passes [Location.of], the gate of the storage.
         * Every refusal is a bad request.
         */
        fun canonicalize(rawPath: String): Result<RepositoryPath, ErrorResponse> {
            if (rawPath.any { it.isISOControl() }) return badRequestError("The path contains a control character")
            if (encodedSlash.containsMatchIn(rawPath)) return badRequestError("The path contains an escaped slash")

            val decoded = try {
                URLDecoder.decode(rawPath.replace("+", "%2B"), Charsets.UTF_8)
            } catch (exception: IllegalArgumentException) {
                return badRequestError("The path contains a malformed escape")
            }
            if (decoded.any { it.isISOControl() }) return badRequestError("The path contains a control character")
            if ('\\' in decoded) return badRequestError("The path contains a backslash")

            val segments = decoded.split('/').filter { it.isNotEmpty() }
            if (segments.any { it == "." || it == ".." }) return badRequestError("The path contains a dot segment")
            val repository = segments.firstOrNull() ?: return badRequestError("The path names no repository")

            return Location.ofRequest(segments.drop(1).joinToString("/"))
                .map { location -> RepositoryPath(repository, location) }
        }

    }

}
