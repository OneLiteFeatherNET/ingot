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
package com.reposilite.configuration.shared.infrastructure

import com.reposilite.audit.api.AuditActor
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.shared.ErrorResponse
import com.reposilite.token.AccessTokenIdentifier

/**
 * Decides whether a write of a settings domain may proceed, for example because it changes fields that decide who may log in.
 * The endpoint asks it with the stored and the incoming document, and a refusal is returned as its error.
 */
internal fun interface SettingsWriteGuard {

    /** The refusal of the write, or null when it may proceed. */
    fun refusal(domain: String, before: SharedSettings, after: SharedSettings, caller: AccessTokenIdentifier, actor: AuditActor): ErrorResponse?
}
