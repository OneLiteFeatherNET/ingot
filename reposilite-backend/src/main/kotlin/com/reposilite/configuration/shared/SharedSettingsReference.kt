/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.configuration.shared

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.configuration.shared.api.ValidatedSettings
import panda.std.Result
import panda.std.Result.supplyThrowing
import panda.std.asError
import java.io.InputStream
import java.util.function.Supplier
import kotlin.reflect.full.findAnnotation

/**
 * An update that breaks the rules of its settings. The message is the rules themselves, one after another, so the API can
 * show it as it is: no class name and no prefix.
 */
class SettingsViolationException(val violations: List<String>) : IllegalArgumentException(violations.joinToString("; "))

interface SharedSettingsReference<S : SharedSettings> {

    val type: Class<out S>
    val name: String
    val schema: Supplier<InputStream>

    fun get(): S
    fun update(value: S): Result<S, out Exception>

}

internal class DefaultSharedSettingsReference<T : SharedSettings>(
    override val type: Class<out T>,
    override val schema: Supplier<InputStream>,
    private val getter: () -> T,
    private val setter: (T) -> Unit
) : SharedSettingsReference<T> {

    override val name = type.kotlin.findAnnotation<Doc>()!!.title.sanitizeURLParam()

    override fun get(): T =
        getter()

    override fun update(value: T): Result<T, out Exception> {
        // Normalized and checked before the setter runs, so a rejected update leaves the current settings in place.
        val settings = normalize(value)
        val violations = (settings as? ValidatedSettings)?.violations().orEmpty()
        if (violations.isNotEmpty()) {
            return SettingsViolationException(violations).asError()
        }

        return supplyThrowing { setter(settings) }
            .mapErr { IllegalStateException("Cannot update settings reference $name", it) }
            .map { get() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun normalize(value: T): T =
        (value as? ValidatedSettings)?.normalized() as? T ?: value

    private fun String.sanitizeURLParam(): String =
        lowercase().replace(' ', '_')

}
