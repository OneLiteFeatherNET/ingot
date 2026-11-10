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

package net.onelitefeather.ingot.format

import java.util.ServiceLoader

/**
 * Marks the repository format SPI as experimental. Its members are added while Raw, Helm and npm prove the surface, so a
 * plugin that implements one of these types opts in explicitly and accepts that the members may change.
 */
@RequiresOptIn(
    message = "The repository format SPI is experimental and may change until it is declared stable.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.TYPEALIAS)
annotation class ExperimentalIngotFormatApi

/**
 * A repository format. A repository names its format with its `format` key, and a repository without the key is
 * [RepositoryFormats.DEFAULT_ID]. Implementations are found through [ServiceLoader], see [RepositoryFormats.installed].
 */
@ExperimentalIngotFormatApi
interface RepositoryFormat {

    /** The id a repository names in its `format` key, for example `maven2`. Unique among the installed formats. */
    val id: String

}

/**
 * The formats of one server, keyed by id. Immutable once built, so the rules that depend on the formats do not change while
 * the server runs. Tests build their own registries from lists.
 */
@ExperimentalIngotFormatApi
class RepositoryFormats(formats: Collection<RepositoryFormat>) {

    private val byId: Map<String, RepositoryFormat>

    init {
        val duplicates = formats.groupBy { it.id }.filterValues { it.size > 1 }
        check(duplicates.isEmpty()) {
            duplicates.entries.joinToString("; ") { (id, implementations) ->
                "repository format '$id' is implemented by ${implementations.joinToString(" and ") { it.javaClass.name }}"
            }
        }
        check(formats.any { it.id == DEFAULT_ID }) {
            "The repository format '$DEFAULT_ID' is the default of every repository and must be installed"
        }
        byId = formats.associateBy { it.id }
    }

    /** The ids of the installed formats, in no particular order. */
    val ids: Set<String>
        get() = byId.keys

    /** The format named [id], or null when no installed format has that id. */
    fun get(id: String): RepositoryFormat? =
        byId[id]

    companion object {

        /** The id of the format of a repository that names none, and the only format of this version. */
        const val DEFAULT_ID = "maven2"

        private val installedFormats: RepositoryFormats by lazy {
            RepositoryFormats(ServiceLoader.load(RepositoryFormat::class.java).toList())
        }

        /** The formats found on the class path of the server, loaded once. */
        fun installed(): RepositoryFormats =
            installedFormats

    }

}
