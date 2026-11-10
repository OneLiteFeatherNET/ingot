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

import com.reposilite.audit.AuditFacade
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.MavenFacade
import net.onelitefeather.ingot.nexusimport.copy.MavenTargetWriter
import net.onelitefeather.ingot.nexusimport.copy.TargetWriter
import net.onelitefeather.ingot.nexusimport.definitions.MavenDomainPort
import net.onelitefeather.ingot.nexusimport.definitions.SharedMavenDomain
import net.onelitefeather.ingot.nexusimport.jobs.ImportPorts

/**
 * The Ingot side of an import, wired to the running instance. A job's deploys and definitions are attributed to the actor
 * `migration` with the job id as address, so the audit trail names the import and the job.
 */
internal class IngotImportPorts(
    private val maven: MavenFacade,
    private val shared: SharedConfigurationFacade,
    private val audit: AuditFacade,
) : ImportPorts {

    override fun writer(jobId: Long): TargetWriter = MavenTargetWriter(maven, "migration@$jobId")

    override fun domain(jobId: Long): MavenDomainPort = SharedMavenDomain(shared, actor(jobId))

    override fun existingTargets(): Set<String> = maven.getRepositories().map { it.name }.toSet()

    override fun bulkSessionStarted(session: String) = audit.registerBulkSession(session)

    override fun bulkSessionEnded(session: String) = audit.unregisterBulkSession(session)

    override fun runFinished(jobId: Long, details: Map<String, Any>) {
        audit.record(AuditEvent(AuditAction.NEXUS_IMPORT_RUN, actor(jobId), details = details))
    }

    private fun actor(jobId: Long): AuditActor = AuditActor.named("migration", jobId.toString())
}
