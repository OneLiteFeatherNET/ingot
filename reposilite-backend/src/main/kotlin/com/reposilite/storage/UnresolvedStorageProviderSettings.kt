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

package com.reposilite.storage

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * A storage block whose type no built-in factory knows, typically a plugin type read before the plugins have loaded. The block is kept
 * as read and written back unchanged. [StorageFacade] binds it to the settings class of its factory when a repository is built.
 */
data class UnresolvedStorageProviderSettings(
    override val type: String,
    @get:JsonValue val raw: ObjectNode,
) : StorageProviderSettings
