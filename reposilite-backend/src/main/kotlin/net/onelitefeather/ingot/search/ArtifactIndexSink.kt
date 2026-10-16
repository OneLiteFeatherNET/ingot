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

package net.onelitefeather.ingot.search

/**
 * The write side that call sites use (artifact search spec, 7.1). An implementation only records that a path needs
 * another look and never throws: a write path of Ingot must not fail because the index is unavailable.
 */
interface ArtifactIndexSink {

    /** A file at [path] was written, replaced or its checksum changed. */
    fun changed(repository: String, path: String)

    /** The file or directory at [path] was removed, together with everything below it. */
    fun removed(repository: String, path: String)

    /** The version directory [directory] was deployed into, so it is no longer a mirror copy. */
    fun reorigin(repository: String, directory: String)

}

/** The sink while search is disabled. */
object NoOpArtifactIndexSink : ArtifactIndexSink {
    override fun changed(repository: String, path: String) = Unit
    override fun removed(repository: String, path: String) = Unit
    override fun reorigin(repository: String, directory: String) = Unit
}
