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

package net.onelitefeather.ingot.support

import com.reposilite.status.api.ClusterStatusResponse
import net.onelitefeather.ingot.cluster.ClusterBusUrl
import java.time.Clock

/**
 * Everything the support bundle reads, each as a plain function. The collector receives nothing else, so it cannot reach the
 * tokens, the sessions, the identities or the audit log, and a test asserts that structurally. The plugin wires the real sources.
 */
class SupportSources(
    /** The identifier of the answering instance, used in the file names of the bundle. */
    val instance: String,
    val clock: Clock,
    val version: String,
    /** Build facts read from the jar, with the keys `buildTime` and `commit`. Absent keys are written as `unknown`. */
    val buildInformation: () -> Map<String, String> = { emptyMap() },
    val jvm: () -> JvmFacts,
    val operatingSystem: () -> OperatingSystemFacts,
    val environment: () -> Map<String, String> = { emptyMap() },
    val systemProperties: () -> Map<String, String> = { emptyMap() },
    val jvmArguments: () -> List<String> = { emptyList() },
    /** Every local configuration value by its property name. A value is null when the property has none. */
    val localValues: () -> Map<String, String?> = { emptyMap() },
    val sharedDomains: () -> List<SharedDomainDocument> = { emptyList() },
    val repositoryStorage: () -> List<RepositoryStorage> = { emptyList() },
    val plugins: () -> List<PluginFact> = { emptyList() },
    /** The cluster section as this instance sees it, already without bus credentials. Null without a bus. */
    val cluster: () -> ClusterStatusResponse? = { null },
    /** The parsed bus URL, read only to scrub its credentials from the log lines. */
    val busUrl: () -> ClusterBusUrl? = { null },
    /** The cached log lines of this instance, oldest first. */
    val cachedLog: () -> List<String> = { emptyList() },
)

data class JvmFacts(
    val vendor: String,
    val version: String,
    val uptimeMillis: Long,
    val heapUsedMb: Double,
    val heapMaxMb: Int,
    val processors: Int,
    val threads: Int,
)

data class OperatingSystemFacts(
    val name: String,
    val version: String,
    val architecture: String,
    /** True when the process appears to run in a container. */
    val container: Boolean,
)

data class PluginFact(val name: String, val version: String, val dependencies: List<String>)

/** The storage of one repository: its kind, and the region for an S3 bucket. Keys are never part of it. */
data class RepositoryStorage(val repository: String, val kind: String, val region: String?)
