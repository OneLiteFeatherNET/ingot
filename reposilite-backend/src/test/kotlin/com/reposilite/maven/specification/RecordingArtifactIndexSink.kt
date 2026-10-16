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

package com.reposilite.maven.specification

import net.onelitefeather.ingot.search.ArtifactIndexSink
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The index sink of the maven tests. It records every call in order and can be told to fail on every call, the way a
 * broken database would. [onCall] runs after the record is made and before a failure is thrown.
 */
internal class RecordingArtifactIndexSink(
    private val failure: Throwable? = null,
    private val onCall: (IndexCall) -> Unit = {},
) : ArtifactIndexSink {

    data class IndexCall(val operation: String, val repository: String, val path: String)

    val recorded = CopyOnWriteArrayList<IndexCall>()

    override fun changed(repository: String, path: String) = record("changed", repository, path)

    override fun removed(repository: String, path: String) = record("removed", repository, path)

    override fun reorigin(repository: String, directory: String) = record("reorigin", repository, directory)

    fun recorded(operation: String): List<IndexCall> =
        recorded.filter { it.operation == operation }

    private fun record(operation: String, repository: String, path: String) {
        val call = IndexCall(operation, repository, path)
        recorded += call
        onCall(call)
        failure?.let { throw it }
    }

}
