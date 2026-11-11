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

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.status.api.ClusterStatusResponse

/** The system information page, and the content of `system-info.json` in the bundle. Every value in it is already redacted. */
data class SystemInformation(
    val instance: String,
    val generatedAt: String,
    val redactionRuleVersion: Int,
    val ingot: IngotInformation,
    val jvm: JvmInformation,
    val operatingSystem: OperatingSystemInformation,
    val database: DatabaseInformation,
    val storage: List<StorageInformation>,
    val cluster: ClusterStatusResponse?,
    val plugins: List<PluginInformation>,
    /** The environment variables, withheld by name unless the contract allows the value. */
    val environment: Map<String, String>,
    /** The system properties, withheld by name unless the contract allows the value. */
    val systemProperties: Map<String, String>,
    val configuration: ConfigurationInformation,
)

data class IngotInformation(val version: String, val buildTime: String, val commit: String)

data class JvmInformation(
    val vendor: String,
    val version: String,
    val uptimeMillis: Long,
    val heapUsedMb: Double,
    val heapMaxMb: Int,
    val processors: Int,
    val threads: Int,
    val inputArguments: List<String>,
)

data class OperatingSystemInformation(val name: String, val version: String, val architecture: String, val container: Boolean)

/** The kind of the database, and for a networked kind its host and port. */
data class DatabaseInformation(val kind: String, val endpoint: String?)

data class StorageInformation(val repository: String, val kind: String, val region: String?)

data class PluginInformation(val name: String, val version: String, val dependencies: List<String>)

data class ConfigurationInformation(val local: Map<String, String>, val shared: Map<String, JsonNode>)

/** The content of `manifest.json`: every other file with its size, the rule version and the number of redacted values. */
data class BundleManifest(
    val format: String,
    val redactionRuleVersion: Int,
    val instance: String,
    val generatedAt: String,
    val redactions: Int,
    val files: List<ManifestEntry>,
)

data class ManifestEntry(val path: String, val bytes: Int)
