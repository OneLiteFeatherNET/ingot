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

package com.reposilite.configuration.shared.api

/**
 * Settings whose rules apply to a write only, not to a document read at start. A rule that depends on what the server has
 * installed belongs here: a document written by a later version may name something this server does not have, and the start
 * must still load the rest of it. [updateViolations] runs when settings are written through the shared configuration, after
 * [ValidatedSettings.violations] has passed, and an empty list accepts the write.
 */
interface UpdateValidatedSettings : SharedSettings {

    /** One human readable message per rejected field, naming the field by its path. An empty list accepts the write. */
    fun updateViolations(): List<String>

}
