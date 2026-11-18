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

package net.onelitefeather.ingot.plugin

/**
 * Marks a type of the plugin API whose shape may still change before it is declared stable. A plugin that uses such a type opts
 * in with `@OptIn(ExperimentalIngotPluginApi::class)`, and Kotlin warns about a use without the opt-in. Java plugins see the
 * annotation but get no compiler warning.
 */
@RequiresOptIn(
    message = "Marked with ExperimentalIngotPluginApi: this plugin API may change without a major version.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.TYPEALIAS)
annotation class ExperimentalIngotPluginApi
