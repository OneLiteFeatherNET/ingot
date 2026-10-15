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

/**
 * The one place that names the paths the server reserves for itself.
 *
 * [APP_ROUTES] is the route table of the dashboard, relative to the base path. The dashboard is served
 * by this instance only while `defaultFrontend` is enabled, but the reserved segments are configuration
 * independent: a separately deployed dashboard reached through the same host still owns these paths.
 * Both the route registration in `FrontendPlugin` and the repository id check in `MavenPlugin` read from here.
 */
internal object DashboardPaths {

    /** Routes that answer with index.html. Only the segments that take a sub-path use a parameter. */
    val APP_ROUTES: List<String> = listOf(
        "/browse",
        "/browse/<path>",
        "/dashboard",
        "/tokens",
        "/tokens/{name}",
        "/console",
        "/maintenance",
        "/audit",
        "/settings",
        "/settings/<path>",
    )

    /** Top-level segments that belong to the API and to the bundled assets, independent of the dashboard routes. */
    val SERVER_SEGMENTS: Set<String> = setOf("api", "assets")

    /**
     * Every top-level segment the server claims. For the dashboard routes, a repository with one of these ids
     * still answers every non-browser request; only a top-level browser navigation gets the dashboard.
     */
    val RESERVED_SEGMENTS: Set<String> =
        APP_ROUTES.map { it.removePrefix("/").substringBefore('/') }.toSet() + SERVER_SEGMENTS

    /**
     * Returns one warning per configured repository id that equals a reserved segment. Ids are compared
     * exactly, the same way the routes match. Nothing is rejected, so existing configurations keep loading.
     */
    fun reservedRepositoryWarnings(repositoryIds: Iterable<String>): List<String> =
        repositoryIds
            .filter { it in RESERVED_SEGMENTS }
            .map { id -> if (id in SERVER_SEGMENTS) serverSegmentWarning(id) else dashboardSegmentWarning(id) }

    private fun dashboardSegmentWarning(id: String): String =
        "Repository '$id' shares its path segment with the dashboard route '/$id'. The repository keeps answering " +
            "Maven, Gradle and other non-browser clients under '/$id/<gav>', including HEAD requests. Only a top-level " +
            "browser navigation to '/$id' shows the dashboard, and the contents of the repository stay reachable in the " +
            "dashboard under '/browse/$id'. The configuration is not rejected."

    private fun serverSegmentWarning(id: String): String =
        "Repository '$id' uses the reserved path segment '/$id', which Ingot answers itself. " +
            "GET requests under '/$id/' (including Maven clients) may be answered by the dashboard or the API " +
            "instead of the repository. The configuration is not rejected, but renaming the repository avoids this."

}
