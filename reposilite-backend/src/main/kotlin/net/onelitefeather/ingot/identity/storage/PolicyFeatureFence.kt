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
package net.onelitefeather.ingot.identity.storage

import com.reposilite.VERSION
import com.reposilite.journalist.Journalist
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * The policy features this build understands. Empty in the first release with the fence; a change that starts writing a
 * feature adds its name here.
 */
internal val KnownPolicyFeatures: Set<String> = emptySet()

/**
 * Refuses to start on a database that records a policy feature this build does not know. Such a database was written by a
 * newer build, and ignoring its policy rows would silently drop restrictions, namespaces or signing policies.
 */
internal class PolicyFeatureFence(
    private val database: Database,
    private val journalist: Journalist,
    private val knownFeatures: Set<String> = KnownPolicyFeatures,
    private val runningVersion: String = VERSION,
) {

    /** Throws [IllegalStateException] naming every unknown feature and the running version. */
    fun check() {
        val recorded = transaction(database) {
            SchemaUtils.create(PolicyFeatureTable)
            PolicyFeatureTable.selectAll().map { it[PolicyFeatureTable.name] }
        }
        val unknown = recorded.filter { it !in knownFeatures }
        if (unknown.isEmpty()) {
            return
        }

        val message = "Identity | policy feature(s) ${unknown.joinToString()} were written by a newer build, " +
            "Ingot $runningVersion does not know them and refuses to start. Start a build that knows these features."
        journalist.logger.error(message)
        throw IllegalStateException(message)
    }
}
