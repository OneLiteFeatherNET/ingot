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

package com.reposilite.plugin.api

import java.time.Instant

// The events below are experimental until one release has shipped. They are emitted after the database write they describe,
// on the instance that made the change. They carry names and identifiers only, never secret material.

/** A token was created, changed, renamed, deleted or had its rights or secret changed. */
data class TokenStateChangedEvent(val tokenName: String) : Event

/** A resolution-metadata file was deployed or deleted, so the resolution pins of its prefix are no longer valid. */
data class ResolutionInvalidatedEvent(val repository: String, val path: String) : Event

/** A session family was revoked by logout, reuse detection or a revocation of all sessions of a principal. */
data class SessionFamilyRevokedEvent(val familyId: String) : Event

/** A javadoc jar was deployed, so the unpacked javadoc cache of the artifact at [path] is dropped on every instance. */
data class JavadocInvalidatedEvent(val repository: String, val path: String) : Event

/** Shared configuration of one settings domain was stored. */
data class SharedConfigurationChangedEvent(val domain: String) : Event

/** The read-only state was stored on this instance, with the flag that was stored. */
data class ReadOnlyChangedEvent(val enabled: Boolean) : Event

/** Repository storage usage was measured again. */
data class StorageUsageRefreshedEvent(val computedAt: Instant, val repositories: List<StorageUsageFigure>) : Event

/**
 * The figures of one repository. [storageType] is the type it was measured with. A figure of a replica that does not send
 * the type reads as unknown.
 */
data class StorageUsageFigure(val id: String, val usedBytes: Long, val fileCount: Long, val storageType: String) {

    constructor(id: String, usedBytes: Long, fileCount: Long) : this(id, usedBytes, fileCount, "unknown")

}
