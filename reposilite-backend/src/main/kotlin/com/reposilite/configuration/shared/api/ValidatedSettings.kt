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
 * Settings that reject values the generated schema cannot express, such as a malformed colour.
 *
 * The schema describes the shape of a value, not its constraints, so [violations] is the only
 * check a value gets. It runs before an update is applied, whether the update arrives through
 * the API or is read from the shared configuration file, so an invalid value never replaces a
 * valid one.
 */
interface ValidatedSettings : SharedSettings {

    /**
     * One human readable message per rejected field, naming the field by its path.
     * An empty list accepts the settings.
     */
    fun violations(): List<String>

    /**
     * The form the settings are stored in. It runs before [violations] when an update is applied, so a value that is
     * only noise to the rules can be cleaned up instead of rejected. Settings that have nothing to clean keep [this].
     */
    fun normalized(): ValidatedSettings = this

}
