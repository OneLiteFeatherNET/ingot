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
package net.onelitefeather.ingot.nexusimport

import com.reposilite.plugin.api.Facade
import net.onelitefeather.ingot.nexusimport.jobs.JobScheduler

/**
 * What other plugins and the tests of this plugin use: the scheduler of this replica. [tick] runs one pass over the jobs this
 * replica may take. The background ticker calls it on its own; a caller that needs a job to run now calls it directly.
 */
class NexusImportFacade internal constructor(private val scheduler: JobScheduler) : Facade {

    /** One pass over the runnable jobs. Returns how many jobs this replica ran or started. */
    fun tick(): Int = scheduler.tick()
}
