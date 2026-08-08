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
package com.reposilite.storage.api

import com.reposilite.maven.RepositoryVisibility
import com.reposilite.storage.api.FileType.DIRECTORY
import com.reposilite.storage.api.FileType.FILE
import io.javalin.http.ContentType
import java.time.Instant

const val UNKNOWN_LENGTH = -1L

enum class FileType {
    FILE,
    DIRECTORY
}

sealed class FileDetails(
    val type: FileType,
    open val name: String,
) : Comparable<FileDetails> {

    override fun compareTo(other: FileDetails): Int =
        type.compareTo(other.type)
            .takeIf { it != 0 }
            ?: name.compareTo(other.name)

}

data class DocumentInfo(
    override val name: String,
    val contentType: ContentType,
    val contentLength: Long = UNKNOWN_LENGTH,
    val lastModifiedTime: Instant? = null,
) : FileDetails(FILE, name)

sealed class AbstractDirectoryInfo(
    name: String,
) : FileDetails(DIRECTORY, name)

class SimpleDirectoryInfo(
    name: String,
) : AbstractDirectoryInfo(name)

/**
 * A directory that stands for a whole repository, used by the root listing of `/api/maven/details`.
 *
 * [SimpleDirectoryInfo] describes every other directory, so it deliberately carries no visibility:
 * a folder inside a repository has none. This type exists so the repository level can expose one
 * without putting a meaningless value on every entry of every other listing.
 *
 * Direct subclasses of the sealed [FileDetails] have to live in this package, which is why a
 * repository concept is declared next to the storage ones.
 */
class RepositoryDirectoryInfo(
    name: String,
    val visibility: RepositoryVisibility,
) : AbstractDirectoryInfo(name)

class DirectoryInfo(
    name: String,
    val files: List<FileDetails>
) : AbstractDirectoryInfo(name) {

    fun filter(predicate: (FileDetails) -> Boolean): DirectoryInfo =
        DirectoryInfo(name, files.filter { predicate(it) })

}
