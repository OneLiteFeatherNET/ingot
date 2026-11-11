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

package net.onelitefeather.ingot.nexusimport.definitions

import com.reposilite.maven.application.RepositorySettings

/**
 * An in-memory `maven` settings domain with entity tags: an update whose tag is no longer current is refused with a
 * precondition failure, and a managed domain refuses every update with a conflict.
 */
internal class FakeMavenDomain(
    initial: List<RepositorySettings>,
    private val managed: Boolean = false,
) : MavenDomainPort {

    var stored: List<RepositorySettings> = initial
        private set

    private var version = 1
    private var pending: RepositorySettings? = null

    val firstTag: String = tag()
    val currentTag: String get() = tag()
    val updates = mutableListOf<UpdateCall>()

    data class UpdateCall(val ifMatch: String)

    /** The next update finds the domain changed by another writer, so its entity tag no longer matches. */
    fun changeBeforeNextUpdate(added: RepositorySettings) {
        pending = added
    }

    override fun read(): DomainState = DomainState(stored.map { it.id }.toSet(), currentTag)

    override fun update(ifMatch: String, transform: (List<RepositorySettings>) -> List<RepositorySettings>): UpdateResult {
        updates += UpdateCall(ifMatch)
        pending?.let { added ->
            pending = null
            stored = stored + added
            version++
        }
        if (managed) return UpdateResult.ManagedExternally
        if (ifMatch != currentTag) return UpdateResult.PreconditionFailed
        stored = transform(stored)
        version++
        return UpdateResult.Stored
    }

    private fun tag(): String = "v$version"
}
