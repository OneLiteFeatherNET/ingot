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

@file:Suppress("UnstableApiUsage")

package com.reposilite.status

import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import com.google.common.collect.EvictingQueue
import com.reposilite.VERSION
import com.reposilite.plugin.api.Facade
import com.reposilite.status.api.ClusterBusResponse
import com.reposilite.status.api.InstanceStatusResponse
import com.reposilite.status.api.StatusSnapshot
import panda.std.Result
import panda.std.reactive.Reference
import panda.utilities.IOUtils
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class StatusFacade(
    private val testEnv: Boolean,
    private val startTime: Long = System.currentTimeMillis(),
    private val maxThreads: Reference<Int>,
    private val status: () -> Boolean,
    private val remoteVersionUrl: String,
    private val failureFacade: FailureFacade,
    private val clock: Clock = Clock.systemUTC(),
    /** The checks of the plugins, the database and the storage. The cluster check is added here. */
    private val readinessChecks: List<ReadinessCheck> = emptyList(),
) : Facade {

    private val cachedStatusSnapshots = EvictingQueue.create<StatusSnapshot>(12)

    @Volatile
    private var clusterStatusSource: ClusterStatusSource? = null

    private val snapshotListeners = CopyOnWriteArrayList<(InstanceFigures) -> Unit>()

    private val draining = AtomicBoolean(false)

    /** Answers whether this instance can serve requests now. Served at /api/status/ready. */
    val readinessProbe: ReadinessProbe = ReadinessProbe(
        checks = listOf(ReadinessCheck(name = "draining", check = { !draining.get() })) + readinessChecks + ReadinessCheck(
            name = "cluster",
            applies = { clusterStatusSource != null },
            check = { clusterStatusSource?.clusterStatus(instanceFigures())?.bus?.state == "connected" },
        ),
        onFailure = { name, cause ->
            failureFacade.logger.warn("Readiness check $name failed: ${cause?.let { "${it::class.simpleName}: ${it.message}" } ?: "the check answered no"}")
        },
    )

    /**
     * Marks this instance as leaving. The readiness answer lists the `draining` check as failing from then on. The flag
     * is never cleared again, so the liveness answer does not change and the orchestrator does not restart the instance.
     */
    fun startDraining() {
        draining.set(true)
    }

    fun isDraining(): Boolean =
        draining.get()

    /** Adds the cluster section to the status response. Called by the cluster bus only, when a bus URL is configured. */
    fun attachClusterStatus(source: ClusterStatusSource) {
        clusterStatusSource = source
    }

    /** The bus section of the cluster status, or null when no bus is attached. Read by the metrics at scrape time. */
    fun clusterBus(): ClusterBusResponse? =
        clusterStatusSource?.clusterStatus(instanceFigures())?.bus

    /** Calls [listener] with the figures each time a snapshot is recorded, on the thread that records it. */
    fun onSnapshotRecorded(listener: (InstanceFigures) -> Unit) {
        snapshotListeners.add(listener)
    }

    private val cachedLatestVersion: Supplier<Result<String, String>> =
        Suppliers.memoizeWithExpiration({
            when {
                testEnv || !remoteVersionCheckEnabled ->
                    Result.ok(VERSION)
                else ->
                    IOUtils
                        .fetchContent(remoteVersionUrl)
                        .onError {
                            when (it.message?.contains("java.security.NoSuchAlgorithmException")) {
                                true -> failureFacade.logger.warn("Cannot load SSL context for HTTPS request due to the lack of available memory")
                                else -> failureFacade.logger.warn("$remoteVersionUrl is unavailable: ${it.message}")
                            }
                        }
                        .map { extractRemoteVersion(it) }
                        .mapErr { UNKNOWN_VERSION }
            }
        }, 1, TimeUnit.HOURS)

    private val remoteVersionCheckEnabled: Boolean
        get() = getBooleanProperty("ingot.status.remote-version-check")
            ?: getBooleanProperty("reposilite.status.remote-version-check")
            ?: true

    private fun getBooleanProperty(key: String): Boolean? =
        System.getProperty(key)?.let { it == "true" }

    init {
        recordStatusSnapshot()
    }

    fun recordStatusSnapshot() {
        cachedStatusSnapshots.add(
            StatusSnapshot(
                at = clock.millis(),
                memory = getUsedMemory().roundToInt(),
                threads = getUsedThreads()
            )
        )

        val figures = instanceFigures()
        snapshotListeners.forEach { listener ->
            runCatching { listener(figures) }
                .onFailure { failureFacade.logger.warn("Status snapshot listener failed: ${it.message}") }
        }
    }

    fun fetchInstanceStatus(): InstanceStatusResponse =
        InstanceStatusResponse(
            version = VERSION,
            latestVersion = cachedLatestVersion.get().fold({ it }, { it }),
            uptime = clock.millis() - getUptime(),
            usedMemory = getUsedMemory(),
            maxMemory = (Runtime.getRuntime().maxMemory() / 1024 / 1024).toInt(),
            usedThreads = getUsedThreads(),
            maxThreads = maxThreads.get(),
            failuresCount = failureFacade.getFailures().size,
            cluster = clusterStatusSource?.let { it.clusterStatus(instanceFigures()) }
        )

    /** The figures of this instance, as the cluster sees them. */
    fun instanceFigures(): InstanceFigures =
        InstanceFigures(
            version = VERSION,
            uptimeStart = getUptime(),
            usedMemory = getUsedMemory(),
            maxMemory = (Runtime.getRuntime().maxMemory() / 1024 / 1024).toInt(),
            usedThreads = getUsedThreads(),
            maxThreads = maxThreads.get(),
            failuresCount = failureFacade.getFailures().size,
            health = if (isAlive()) "up" else "down",
            interval = snapshotIntervalMillis(),
            samples = getLatestStatusSnapshots().toList(),
        )

    /** Matches [StatusSnapshotScheduler]: one minute while fewer than twelve snapshots are kept, then one hour. */
    private fun snapshotIntervalMillis(): Long =
        if (cachedStatusSnapshots.size >= 12) TimeUnit.HOURS.toMillis(1) else TimeUnit.MINUTES.toMillis(1)

    private fun getUsedThreads(): Int =
        Thread.activeCount()

    private fun getUsedMemory(): Double =
        (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024.0 / 1024.0

    internal fun getLatestVersion(): Result<String, String> =
        cachedLatestVersion.get()

    internal companion object {

        internal const val UNKNOWN_VERSION = "<unknown>"

        private val TAG_NAME = Regex(""""tag_name"\s*:\s*"([^"]+)"""")

        /**
         * Ingot reads its release feed from the GitHub releases API, which answers with a JSON
         * document rather than a bare version. A response that is not JSON is passed through
         * untouched, so an endpoint that already returns plain text - a Maven latest-version query,
         * for instance - stays a valid value for the remote version URL.
         */
        internal fun extractRemoteVersion(response: String): String {
            val trimmed = response.trim()

            val version = when {
                trimmed.startsWith("{") -> TAG_NAME.find(trimmed)?.groupValues?.get(1) ?: return UNKNOWN_VERSION
                else -> trimmed
            }

            return version
                .removePrefix("v")
                .takeIf { it.isNotEmpty() }
                ?: UNKNOWN_VERSION
        }

    }

    fun getLatestStatusSnapshots(): Array<StatusSnapshot> =
        cachedStatusSnapshots.toTypedArray()

    fun getUptime(): Long =
        startTime

    fun isAlive(): Boolean =
        status()

}
