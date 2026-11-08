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

/** Shared configuration of one settings domain was stored. */
data class SharedConfigurationChangedEvent(val domain: String) : Event

/** Repository storage usage was measured again. */
data class StorageUsageRefreshedEvent(val computedAt: Instant, val repositories: List<StorageUsageFigure>) : Event

data class StorageUsageFigure(val id: String, val usedBytes: Long, val fileCount: Long)
