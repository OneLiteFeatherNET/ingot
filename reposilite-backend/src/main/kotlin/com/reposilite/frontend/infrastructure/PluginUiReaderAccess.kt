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
package com.reposilite.frontend.infrastructure

import io.javalin.http.HandlerType

/**
 * The rules that decide whether a request to a plugin route is a read of a reader source, which needs no sign in.
 * The exemption is exact: a `GET` or `HEAD` whose path is a declared source, and nothing else. A path with a `..`, `//`,
 * an empty segment or a percent sign is never exempt, so the admin rule of the plugin applies to it.
 */
internal object PluginUiReaderAccess {

    /** True when a request with [method] and [path] reads one of the [sources] of its plugin anonymously. */
    fun isExemptRead(method: HandlerType, path: String, sources: Set<String>): Boolean =
        (method == HandlerType.GET || method == HandlerType.HEAD) && isPlain(path) && path in sources

    /** A path the guard reads exactly: no dot segments, no empty segments and no percent-encoded characters. */
    fun isPlain(path: String): Boolean =
        !path.contains("..") && !path.contains('%') && path.removePrefix("/").split('/').none { it.isEmpty() }
}
