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

package com.reposilite.storage.filesystem

/** Prefix of the temporary file an upload is written to, which lies beside the target of the upload. */
internal const val PARTIAL_UPLOAD_PREFIX = ".ingot-"

/** Suffix of the temporary file an upload is written to. */
internal const val PARTIAL_UPLOAD_SUFFIX = ".part"

/**
 * A name with the prefix and the suffix is reserved for an upload in progress. No Maven artifact has a name with a
 * leading dot, so the name is never a name a client needs.
 */
internal fun isPartialUploadName(name: String): Boolean =
    name.startsWith(PARTIAL_UPLOAD_PREFIX) && name.endsWith(PARTIAL_UPLOAD_SUFFIX)
