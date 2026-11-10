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

package net.onelitefeather.ingot.repository.index

import com.reposilite.plugin.Extensions

/**
 * The sink the maven plugin writes through. Search initializes after maven, so maven cannot look up the sink provider at its
 * own start; the provider is looked up on every call instead. While no provider is registered, or search is disabled and
 * provides the no-op sink, the calls do nothing.
 */
internal class SearchFacadeArtifactIndexSink(private val extensions: Extensions) : ArtifactIndexSink {

    private fun sink(): ArtifactIndexSink =
        extensions.getFacades().filterIsInstance<ArtifactIndexSinkProvider>().firstOrNull()?.sink ?: NoOpArtifactIndexSink

    override fun changed(repository: String, path: String) = sink().changed(repository, path)

    override fun removed(repository: String, path: String) = sink().removed(repository, path)

    override fun reorigin(repository: String, directory: String) = sink().reorigin(repository, directory)

}
