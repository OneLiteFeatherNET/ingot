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

package com.reposilite.audit.api

import com.reposilite.plugin.api.Event
import com.reposilite.shared.ContextDsl

/**
 * An audited action. Producers emit it on the plugin event bus and never put secret values into
 * [details]: only names, counts and field paths belong there.
 */
class AuditEvent(
    val action: AuditAction,
    val actor: AuditActor,
    val repository: String? = null,
    val path: String? = null,
    val details: Map<String, Any?> = emptyMap(),
    val success: Boolean = true,
) : Event

/**
 * The actor of the request that is being handled. Used inside routes that have already
 * authenticated the caller; a caller that cannot be resolved is recorded as anonymous.
 */
fun ContextDsl<*>.auditActor(): AuditActor =
    authentication().fold(
        { token -> AuditActor.named(token.name, ctx.ip()) },
        { AuditActor.anonymous(ctx.ip()) }
    )
