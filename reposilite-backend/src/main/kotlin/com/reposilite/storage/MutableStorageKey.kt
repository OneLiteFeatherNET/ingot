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

package com.reposilite.storage

/**
 * Whether a storage key names metadata that another instance may overwrite at any time: a `maven-metadata` file with its checksums,
 * or any file inside a `-SNAPSHOT` directory. Such answers are never cached. The S3 head cache and the storage cache share this rule.
 */
fun isMutableStorageKey(key: String): Boolean {
    val segments = key.split('/')
    return segments.last().startsWith("maven-metadata") || segments.dropLast(1).any { it.endsWith("-SNAPSHOT") }
}
