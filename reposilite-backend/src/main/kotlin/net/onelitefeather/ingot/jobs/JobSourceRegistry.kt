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
package net.onelitefeather.ingot.jobs

import com.reposilite.plugin.api.jobs.JobRegistry
import com.reposilite.plugin.api.jobs.JobSource

/**
 * The sources of one instance. [seal] is called after the registration event; a source that registers later is reported
 * through [onLateRegistration] and never listed.
 */
class JobSourceRegistry(private val onLateRegistration: (String) -> Unit = {}) : JobRegistry {

    private val lock = Any()
    private val entries = mutableListOf<JobSource>()
    private var sealed = false

    override fun register(source: JobSource): AutoCloseable = synchronized(lock) {
        if (sealed) {
            onLateRegistration(source.id)
            AutoCloseable { }
        } else {
            entries += source
            AutoCloseable { synchronized(lock) { entries.remove(source) } }
        }
    }

    fun seal() = synchronized(lock) {
        sealed = true
    }

    fun sources(): List<JobSource> = synchronized(lock) { entries.toList() }

}
