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

package com.reposilite.status.application

import com.reposilite.plugin.api.PluginComponents
import com.reposilite.status.FailureFacade
import com.reposilite.status.ReadinessCheck
import com.reposilite.status.StatusFacade
import net.onelitefeather.ingot.readonly.ReadOnlySource
import net.onelitefeather.ingot.readonly.ReadOnlyState
import panda.std.reactive.Reference

class StatusComponents(
    private val testEnv: Boolean,
    private val failureFacade: FailureFacade,
    private val remoteVersionEndpoint: String,
    private val statusSupplier: () -> Boolean,
    private val maxThreads: Reference<Int>,
    private val readinessChecks: List<ReadinessCheck> = emptyList(),
    private val readOnly: () -> ReadOnlyState = { ReadOnlyState(enabled = false, source = ReadOnlySource.STORED) },
) : PluginComponents {

    fun statusFacade(): StatusFacade =
        StatusFacade(
            testEnv = testEnv,
            status = statusSupplier,
            remoteVersionUrl = remoteVersionEndpoint,
            failureFacade = failureFacade,
            maxThreads = maxThreads,
            readinessChecks = readinessChecks,
            readOnly = readOnly,
        )

}
