package com.reposilite.status

import com.reposilite.status.api.ClusterStatusResponse
import com.reposilite.status.api.StatusSnapshot

/**
 * Supplies the `cluster` section of the instance status. The cluster bus attaches one only when a bus URL is configured,
 * so without a bus the status response has no such field.
 */
interface ClusterStatusSource {

    /** @param local the figures of this instance, which the section lists next to the instances it has received */
    fun clusterStatus(local: InstanceFigures): ClusterStatusResponse

}

/** The figures one instance reports about itself, as the status.snapshot event and the local status both carry them. */
data class InstanceFigures(
    val version: String,
    /** Epoch milliseconds of the last restart */
    val uptimeStart: Long,
    /** Megabytes */
    val usedMemory: Double,
    /** Megabytes */
    val maxMemory: Int,
    val usedThreads: Int,
    val maxThreads: Int,
    val failuresCount: Int,
    /** up or down */
    val health: String,
    /** Milliseconds between two snapshots of this instance */
    val interval: Long,
    /** Oldest first */
    val samples: List<StatusSnapshot>,
)
