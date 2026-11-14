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

package net.onelitefeather.ingot.storage.capability

import com.reposilite.storage.s3.RelatedKeyRule
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import java.time.Duration

/*
 * The capability interfaces of the storage providers. Core asks an interface that a provider or its settings implements, and never
 * tests for a concrete class, so a provider of a plugin gets the same behaviour as the file system and S3 providers. A type that does
 * not implement a capability keeps the default behaviour of that capability.
 */

/** The cluster bus of this instance, as far as a storage needs it. The cluster plugin implements it; it has no members yet. */
@ExperimentalIngotStorageApi
interface ClusterBus

/**
 * A storage whose head cache is bounded by the cluster bus. Core calls [bind] once the bus is open, and [invalidate] for writes that
 * another replica made. Nothing publishes such writes yet, so a bound cache lives at most the maximum age it was bound with.
 */
@ExperimentalIngotStorageApi
interface ClusterCacheAware {

    /** Called once the cluster bus is open, with the longest an answer may live after it was stored. */
    fun bind(clusterBus: ClusterBus, maxAge: Duration)

    /** Drops the answers of [keys] and the related keys of the format from the cache. */
    fun invalidate(keys: Collection<String>)

}

/** A storage whose head cache takes the related keys of the format that stores in it. */
@ExperimentalIngotStorageApi
interface RelatedKeyAware {

    fun useRelatedKeys(rule: RelatedKeyRule)

}

/** What a storage reports about its usage. The defaults describe a storage that reports no quota and no capacity. */
@ExperimentalIngotStorageApi
interface UsageCapability {

    /** True when the usage and the room a storage reports are real figures. False when the storage reports no figures of its own. */
    val reportsUsage: Boolean

    /** True when a configured quota is reported for the storage. Enforcement of a quota stays with the file system provider. */
    val supportsQuota: Boolean

    /** The base of a percentage quota, or null when the capacity is unknown. */
    fun totalCapacityBytes(): Long? = null

}

/** The outcome of a readiness probe of a storage root. */
@ExperimentalIngotStorageApi
sealed interface ReadinessOutcome {

    /** The root answered a listing, empty or not. */
    data object Ok : ReadinessOutcome

    /** The backend answered that the root does not exist, so the backend itself is reachable. */
    data object NotFoundOk : ReadinessOutcome

    /** The backend did not answer, or refused the request. The reason is the message of the failed request. */
    data class Failed(val reason: String) : ReadinessOutcome

}

/** A storage that answers its own readiness probe instead of the listing of its root. */
@ExperimentalIngotStorageApi
interface ReadinessProbe {

    fun probe(): ReadinessOutcome

}

/** Non-sensitive key and value pairs that describe a storage in the support bundle. */
@ExperimentalIngotStorageApi
interface SupportDetails {

    /** Only values that are safe to publish. The support redaction still runs over them. */
    fun describe(): Map<String, String>

}

/** The namespace of a storage configuration, which two repositories must not share. */
@ExperimentalIngotStorageApi
interface SharedNamespaceAware {

    /** An opaque key of the namespace, or null when this configuration has no shared namespace to check. */
    fun namespaceKey(): String?

    /** Whether this namespace collides with [other]. The default is equality of the keys. */
    fun overlaps(other: SharedNamespaceAware): Boolean {
        val key = namespaceKey() ?: return false
        return key == other.namespaceKey()
    }

    /** The message of a repository refused because its namespace overlaps another one. */
    fun overlapMessage(): String =
        "Its storage namespace overlaps another repository sharing the same storage location. Give each repository a distinct namespace."

}

/** Storage settings that carry a quota, as configured, for example "100%" or "10GB". */
@ExperimentalIngotStorageApi
interface QuotaSettings {

    val quota: String?

}
