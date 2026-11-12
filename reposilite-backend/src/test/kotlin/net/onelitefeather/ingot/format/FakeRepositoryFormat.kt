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

import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.api.Location
import panda.std.Result
import panda.std.asSuccess

/** A format that exists only in tests. Tests build their registries from lists of these, never from a shared global. */
@OptIn(ExperimentalIngotFormatApi::class)
internal class FakeRepositoryFormat(override val id: String) : RepositoryFormat {
    override val pathRules: PathRules = NoPathRules
    override val mount: FormatMount = FormatMount.RepositoryPath
    override val storageHints: StorageHints = NoStorageHints
    override fun handler(context: FormatContext): RepositoryRequestHandler = NoRequestHandler
}

/** A second implementation with the same id as [FakeRepositoryFormat], to prove that duplicates are refused by name. */
@OptIn(ExperimentalIngotFormatApi::class)
internal class DuplicateFakeRepositoryFormat(override val id: String) : RepositoryFormat {
    override val pathRules: PathRules = NoPathRules
    override val mount: FormatMount = FormatMount.RepositoryPath
    override val storageHints: StorageHints = NoStorageHints
    override fun handler(context: FormatContext): RepositoryRequestHandler = NoRequestHandler
}

/** Storage hints that name no related keys, so the storage keeps its own rule. */
@OptIn(ExperimentalIngotFormatApi::class)
internal object NoStorageHints : StorageHints {
    override val relatedKeys: ((key: String) -> Collection<String>)? = null
}

/** A handler that answers every request with success, for the formats that are only registered in a test. */
@OptIn(ExperimentalIngotFormatApi::class)
internal object NoRequestHandler : RepositoryRequestHandler {
    override fun handle(request: RepositoryRequest): Result<Unit, ErrorResponse> = Unit.asSuccess()
}

/** Path rules that answer every question with the neutral answer, so a fake format never affects a rule of the server. */
@OptIn(ExperimentalIngotFormatApi::class)
internal object NoPathRules : PathRules {
    override fun isGeneratedMetadata(path: Location): Boolean = false
    override fun isMetadataOrChecksum(path: Location): Boolean = false
    override fun isResolutionMetadata(path: Location): Boolean = false
    override fun countsAsDownload(path: Location): Boolean = true
    override fun indexPath(path: Location): String? = path.toString()
}
