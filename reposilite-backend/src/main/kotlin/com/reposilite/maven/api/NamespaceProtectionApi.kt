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
import com.reposilite.token.AccessTokenIdentifier

/**
 * A protected group refused a coordinate: a proxy member was skipped and no hosted member holds the coordinate, so the
 * caller received the miss of an unresolved coordinate. Carries names and the token identifier only, never a secret.
 *
 * @param prefix the protected prefix that covers [gav]
 * @param skippedMembers the names of the proxy members that were not asked
 */
data class ProxyRefusedEvent(
    val repository: String,
    val gav: Location,
    val accessToken: AccessTokenIdentifier?,
    val prefix: String,
    val skippedMembers: List<String>,
) : Event
