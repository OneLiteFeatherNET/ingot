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

package com.reposilite.maven.api

import com.reposilite.maven.ChecksumPolicy
import com.reposilite.maven.ChecksumResult
import com.reposilite.maven.CopyOutcome
import com.reposilite.plugin.api.Event

/**
 * A mirror copy with checksum policy warn or fail did not match its checksum file ([ChecksumResult.MISMATCH]), or its host
 * published no checksum file ([ChecksumResult.MISSING]). Carries names only, never a digest.
 *
 * @param algorithm the extension of the checksum file that was compared, without the dot, or `none` when the host has none
 */
data class MirrorIntegrityEvent(
    val result: ChecksumResult,
    val repository: String,
    val path: String,
    val host: String,
    val policy: ChecksumPolicy,
    val algorithm: String,
    val outcome: CopyOutcome,
) : Event
