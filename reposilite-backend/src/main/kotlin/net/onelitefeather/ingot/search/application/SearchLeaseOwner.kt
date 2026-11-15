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

package net.onelitefeather.ingot.search.application

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * The owner of the search lease, which is kept with the data directory. The owner of the instance itself changes on every start, so a
 * restart would look like a takeover and the search index would be rebuilt. A replica keeps one owner for as long as its data directory
 * exists, so a restart within the lease time keeps its epoch. Two replicas never share a data directory.
 */
internal object SearchLeaseOwner {

    private const val FILE = "search/lease-owner"

    fun of(dataDirectory: Path): String {
        val file = dataDirectory.resolve(FILE)
        if (Files.isRegularFile(file)) {
            val stored = Files.readString(file).trim()
            if (stored.isNotEmpty()) return stored
        }
        val created = UUID.randomUUID().toString()
        Files.createDirectories(file.parent)
        Files.writeString(file, created)
        return created
    }

}
