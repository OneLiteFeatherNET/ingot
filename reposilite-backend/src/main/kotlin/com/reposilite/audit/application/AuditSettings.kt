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

package com.reposilite.audit.application

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.SharedSettings
import io.javalin.openapi.JsonSchema

@JsonSchema(requireNonNulls = false)
@Doc(title = "Audit", description = "Audit log of administrative and artifact events.")
data class AuditSettings(
    @get:Doc(title = "Retention in days", description = """
        Events older than this many days are deleted once a day. Set to 0 to keep every event.
    """)
    val retentionDays: Int = 90,
    @get:Doc(title = "Record client addresses", description = """
        Store the IP address of the client next to the actor of each event. Turn it off where
        addresses must not be kept at all.
    """)
    val recordActorAddress: Boolean = true
) : SharedSettings
