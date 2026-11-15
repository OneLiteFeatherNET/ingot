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

package net.onelitefeather.ingot.lucene

import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendContract
import java.nio.file.Path
import java.time.Clock

/**
 * The shared contract of the search backends, run in-process against the Lucene backend on a temporary directory. No container and no
 * clock: the backend reads no time of its own in these cases.
 */
internal class LuceneSearchBackendContractTest : ArtifactSearchBackendContract() {

    override fun open(directory: Path, clock: Clock): Opened {
        val settings = LuceneSettings.read(
            mapOf("INGOT_LUCENE_DIRECTORY" to directory.resolve("index").toString()),
            directory.resolve("unused"),
        )
        val backend = testBackend(settings)
        backend.open()
        return Opened(backend) { backend.close() }
    }

}
