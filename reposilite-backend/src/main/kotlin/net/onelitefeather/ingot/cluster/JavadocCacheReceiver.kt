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

package net.onelitefeather.ingot.cluster

import com.reposilite.shared.ErrorResponse
import panda.std.Result

/**
 * The javadoc cache as the cluster sees it. The javadoc viewer implements it on its facade, so the cluster applies a received
 * `javadoc.invalidated` event without naming the viewer. Without a loaded implementation the event is only logged.
 */
interface JavadocCacheReceiver {

    /**
     * Drops the unpacked javadoc cache of the artifact at [path] of [repository], as another instance asked. The payload is not
     * trusted: an implementation refuses an unknown repository or a path outside the javadoc cache of that repository and then
     * deletes nothing.
     */
    fun evictReceived(repository: String, path: String): Result<Unit, ErrorResponse>
}
