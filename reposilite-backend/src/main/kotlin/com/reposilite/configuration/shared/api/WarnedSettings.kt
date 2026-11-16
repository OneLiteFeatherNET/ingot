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
 * Settings whose valid value still deserves a notice to the one who saves it. A warning never refuses the write: it comes
 * back with the answer of the write, as `Warning` headers, and the settings are stored as given.
 */
interface WarnedSettings : SharedSettings {

    /** One human readable message per notice, in plain ASCII. An empty list adds no header to the answer. */
    fun warnings(): List<String>

}
