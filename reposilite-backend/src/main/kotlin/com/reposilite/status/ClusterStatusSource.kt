package com.reposilite.status

import com.reposilite.status.api.ClusterStatusResponse

/**
 * Supplies the `cluster` section of the instance status. The cluster bus attaches one only when a bus URL is configured,
 * so without a bus the status response has no such field.
 */
interface ClusterStatusSource {

    fun clusterStatus(): ClusterStatusResponse

}
