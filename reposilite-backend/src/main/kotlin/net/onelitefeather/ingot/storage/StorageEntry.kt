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

package net.onelitefeather.ingot.storage

import com.reposilite.storage.api.UNKNOWN_LENGTH
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import java.io.InputStream
import java.time.Instant

/**
 * A stored file read in one call: its [content] together with its [size] and [lastModified] time.
 *
 * The caller owns [content] and closes it. [size] is [UNKNOWN_LENGTH] when the provider cannot tell, and [lastModified] is
 * null when the provider has no modification time.
 */
@ExperimentalIngotPluginApi
class StorageEntry(
    val content: InputStream,
    val size: Long = UNKNOWN_LENGTH,
    val lastModified: Instant? = null,
)
