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
package net.onelitefeather.ingot.nexusimport.jobs

import net.onelitefeather.ingot.nexusimport.copy.TargetWriter
import net.onelitefeather.ingot.nexusimport.definitions.MavenDomainPort

/**
 * The Ingot side of an import: where files go, where definitions are written, which names exist, and how the audit trail
 * hears about a bulk session. Production adapters wrap the Maven facade, the shared configuration facade and the audit facade;
 * tests use in-memory fakes.
 */
interface ImportPorts {
    /** The writer of files for a job; its deploys are attributed to the job's session. */
    fun writer(jobId: Long): TargetWriter

    /** The `maven` settings domain for a job's definitions. */
    fun domain(jobId: Long): MavenDomainPort

    /** The repository names Ingot holds now. */
    fun existingTargets(): Set<String>

    /** The audit trail summarises deploys under [session] until [bulkSessionEnded]. */
    fun bulkSessionStarted(session: String)

    fun bulkSessionEnded(session: String)

    /** One run event for a job that reached an end state. The details carry counts only, never a credential. */
    fun runFinished(jobId: Long, details: Map<String, Any>)
}
