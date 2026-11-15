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

package net.onelitefeather.ingot.deploy

import com.reposilite.maven.RepositoryType
import com.reposilite.maven.application.RepositorySettings
import net.onelitefeather.ingot.backup.isSqliteDatabaseSetting

/**
 * The start was refused because the shared state is missing. [refusal] carries the one line and the exit code.
 */
class SharedStateRefusalException(val refusal: StartupRefusal) : IllegalStateException(refusal.line)

/**
 * The shared state a worker, or a server without its background jobs, needs from the rest of the cluster. The check is a
 * pure function: every value is passed in, so it reads neither the environment nor the database.
 */
object ClusterRequirements {

    const val ALLOW_SHARED_FILESYSTEM_VARIABLE = "INGOT_ALLOW_SHARED_FILESYSTEM"

    /** The storage type of a repository on the local file system, see `FileSystemStorageProviderSettings`. */
    private const val FILE_SYSTEM_STORAGE = "fs"

    /**
     * Whether the check runs at all: on `worker`, and on `server` with the background jobs switched off. `all` and
     * `server` with the default never run it, so existing installs start as before.
     */
    fun applies(target: DeployTarget, backgroundJobsEnabled: Boolean): Boolean =
        target == DeployTarget.WORKER || (target == DeployTarget.SERVER && !backgroundJobsEnabled)

    /**
     * The refusal naming every unmet requirement, or null when the start may go on. [database] is the database setting,
     * [clusterBusConfigured] tells whether a bus URL is set, and [repositoryStorageTypes] maps each repository id to its
     * storage type. [groupRepositories] are the ids of the repositories of type GROUP: they hold no files of their own,
     * so their storage type does not count. A repository on the local file system is only unmet while
     * [allowSharedFilesystem] is false.
     */
    fun refusalFor(
        target: DeployTarget,
        backgroundJobsEnabled: Boolean,
        database: String,
        clusterBusConfigured: Boolean,
        repositoryStorageTypes: Map<String, String>,
        groupRepositories: Set<String>,
        allowSharedFilesystem: Boolean,
    ): StartupRefusal? {
        if (!applies(target, backgroundJobsEnabled)) return null

        val fileSystemRepositories = repositoryStorageTypes
            .filterKeys { it !in groupRepositories }
            .filterValues { it == FILE_SYSTEM_STORAGE }
            .keys
            .map { "'$it'" }

        val unmet = buildList {
            if (isSqliteDatabaseSetting(database)) {
                add("the database is embedded sqlite (set INGOT_LOCAL_DATABASE to mariadb, mysql or postgresql)")
            }
            if (!clusterBusConfigured) {
                add("no cluster bus is configured (set INGOT_CLUSTER_URL)")
            }
            if (!allowSharedFilesystem && fileSystemRepositories.isNotEmpty()) {
                add(
                    if (fileSystemRepositories.size == 1) {
                        "repository ${fileSystemRepositories.single()} is stored on the local file system (use object storage)"
                    } else {
                        "repositories ${andList(fileSystemRepositories)} are stored on the local file system (use object storage)"
                    }
                )
            }
        }
        if (unmet.isEmpty()) return null

        val subject = if (target == DeployTarget.WORKER) "worker" else "server with INGOT_BACKGROUND_JOBS=false"
        return StartupRefusal(
            line = "Ingot did not start: target $subject needs shared state, but ${andList(unmet)}.",
            exitCode = STARTUP_FAILURE_EXIT_CODE,
        )
    }

    /**
     * The ids of the repositories declared with type GROUP, see [refusalFor]. Only the declared [RepositorySettings.type]
     * counts: an untyped repository that proxies local repositories stores its cached files on its own file system.
     */
    fun declaredGroupRepositories(repositories: List<RepositorySettings>): Set<String> =
        repositories
            .filter { it.type == RepositoryType.GROUP }
            .map { it.id }
            .toSet()

    /** Joins the items as an English list: `a`, `a and b`, `a, b, and c`. */
    private fun andList(items: List<String>): String =
        when (items.size) {
            0 -> ""
            1 -> items.single()
            2 -> "${items[0]} and ${items[1]}"
            else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
        }

    private const val STARTUP_FAILURE_EXIT_CODE = 1
}
