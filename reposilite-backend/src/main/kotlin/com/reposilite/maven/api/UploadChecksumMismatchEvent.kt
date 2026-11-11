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

import com.reposilite.plugin.api.Event
import com.reposilite.storage.api.Location

/** What the server did with a checksum file that did not match its stored artifact. */
enum class UploadChecksumOutcome {
    /** The checksum file was stored anyway, under the warn policy. */
    ACCEPTED,
    /** The checksum file was refused, under the reject policy. */
    REJECTED,
}

/**
 * A checksum file deployed to [repository] whose digest differs from the stored artifact it describes, or which is not a
 * digest at all. It is emitted under the warn and the reject policy and never under ignore, or when the artifact is not
 * stored yet.
 */
class UploadChecksumMismatchEvent(
    val repository: String,
    val gav: Location,
    val by: String,
    val algorithm: Checksum,
    val outcome: UploadChecksumOutcome,
) : Event
