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

package com.reposilite.configuration

/**
 * Delegates every call to [delegate] and runs a scheduled action once, right before the next call of one operation. A test
 * uses it to put a write of another instance at an exact point of an interleaving, instead of relying on timing.
 */
internal class InterleavingConfigurationRepository(
    private val delegate: ConfigurationRepository,
) : ConfigurationRepository by delegate {

    private var scheduledSaveIfAbsent: (() -> Unit)? = null

    fun beforeNextSaveIfAbsent(action: () -> Unit) {
        scheduledSaveIfAbsent = action
    }

    override fun saveConfigurationIfAbsent(name: String, configuration: String): Boolean {
        val action = scheduledSaveIfAbsent
        scheduledSaveIfAbsent = null
        action?.invoke()
        return delegate.saveConfigurationIfAbsent(name, configuration)
    }

}
