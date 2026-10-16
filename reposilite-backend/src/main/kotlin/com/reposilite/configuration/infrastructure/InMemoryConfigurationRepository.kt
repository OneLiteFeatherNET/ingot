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

package com.reposilite.configuration.infrastructure

import com.reposilite.configuration.ConfigurationRepository
import com.reposilite.configuration.VersionedConfiguration
import java.time.Instant

internal class InMemoryConfigurationRepository : ConfigurationRepository {

    private class Entry(val updateDate: Instant, val content: String, val revision: Long)

    private val settings = HashMap<String, Entry>()

    override fun saveConfiguration(name: String, configuration: String) {
        synchronized(settings) {
            settings[name] = Entry(Instant.now(), configuration, revision = (settings[name]?.revision ?: 0L) + 1)
        }
    }

    override fun findConfiguration(name: String): String? =
        synchronized(settings) { settings[name]?.content }

    override fun findConfigurationUpdateDate(name: String): Instant? =
        synchronized(settings) { settings[name]?.updateDate }

    override fun findVersionedConfiguration(name: String): VersionedConfiguration? =
        synchronized(settings) {
            settings[name]?.let { VersionedConfiguration(content = it.content, revision = it.revision) }
        }

    override fun compareAndSaveConfiguration(name: String, expectedRevision: Long?, configuration: String): Boolean =
        synchronized(settings) {
            if (settings[name]?.revision != expectedRevision) {
                return false
            }

            settings[name] = Entry(Instant.now(), configuration, revision = (expectedRevision ?: 0L) + 1)
            true
        }

    override fun saveConfigurationIfAbsent(name: String, configuration: String): Boolean =
        synchronized(settings) {
            if (settings.containsKey(name)) {
                return false
            }

            settings[name] = Entry(Instant.now(), configuration, revision = 1)
            true
        }

}
