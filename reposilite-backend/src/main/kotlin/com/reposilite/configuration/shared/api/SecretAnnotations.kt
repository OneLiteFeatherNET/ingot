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

import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER

/**
 * Marks a String setting whose value is a secret, such as a password, an access key or an endpoint that embeds credentials.
 * With `hideSettingsSecrets` enabled, the settings API never returns the value of such a property, it returns a placeholder instead.
 */
@Retention
@Target(PROPERTY_GETTER)
annotation class Sensitive

/**
 * Marks the property that identifies an entry of a list of settings, e.g. the id of a repository.
 * A placeholder sent back for a secret inside such an entry is matched to the stored entry with the same key.
 */
@Retention
@Target(PROPERTY_GETTER)
annotation class SettingsKey
