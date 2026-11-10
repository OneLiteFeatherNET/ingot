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

import com.reposilite.plugin.api.Facade

/**
 * The read-only state of this instance, for the request dispatch, the scheduled runs and the status.
 */
class ReadOnlyFacade internal constructor(
    private val cache: ReadOnlyStateCache,
) : Facade {

    fun state(): ReadOnlyState =
        cache.current()

    fun isReadOnly(): Boolean =
        state().enabled

    /** Re-reads the stored state at once, for a change that another instance announced. */
    fun refresh() =
        cache.refresh()

}

/**
 * The requests that stay answered while read-only is on: login, session refresh and the toggle itself. Matching is by the
 * route template and the method, so a path that only shares a prefix with one of them is not exempt.
 */
object ReadOnlyRoutes {

    private val exempt = setOf(
        "POST /api/auth/login",
        "POST /api/auth/refresh",
        "POST /api/admin/read-only",
        "DELETE /api/admin/read-only",
    )

    fun isExempt(method: String, routeTemplate: String): Boolean =
        "$method $routeTemplate" in exempt

}
