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

package com.reposilite.frontend

import io.javalin.http.HandlerType

/** Who answers a GET or HEAD request that arrives on a dashboard route. */
internal enum class DashboardRouteTarget {
    /** The bundled index.html, as served for every app route. */
    DASHBOARD,

    /** The repository of the same id, through the Maven route for `/{repository}/<gav>`. */
    MAVEN,
}

/**
 * Decides between the dashboard and the Maven repository for a request on a dashboard route.
 *
 * Without a repository of the same id, the dashboard answers as it always did. With one, the repository keeps
 * answering every non-browser client, because existing Reposilite installations must not see a changed answer.
 * Only a top-level browser navigation gets the dashboard, recognised by `Sec-Fetch-Mode: navigate` together
 * with `Sec-Fetch-Dest: document`. HEAD always belongs to the repository.
 *
 * Pure on purpose: the caller reads the repository and the headers per request, so a repository added or
 * removed through the shared configuration takes effect without a restart.
 */
internal object DashboardRouteDecision {

    fun decide(
        repositoryExists: Boolean,
        method: HandlerType,
        secFetchMode: String?,
        secFetchDest: String?,
    ): DashboardRouteTarget = when {
        !repositoryExists -> DashboardRouteTarget.DASHBOARD
        method == HandlerType.HEAD -> DashboardRouteTarget.MAVEN
        isTopLevelNavigation(secFetchMode, secFetchDest) -> DashboardRouteTarget.DASHBOARD
        else -> DashboardRouteTarget.MAVEN
    }

    private fun isTopLevelNavigation(secFetchMode: String?, secFetchDest: String?): Boolean =
        secFetchMode.equals("navigate", ignoreCase = true) && secFetchDest.equals("document", ignoreCase = true)

}
