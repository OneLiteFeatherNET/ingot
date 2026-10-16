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

package com.reposilite.shared

import com.google.common.html.HtmlEscapers
import com.google.common.net.UrlEscapers
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Escaping for request-derived values that are interpolated into server-rendered markup.
 */
object HtmlEscaping {

    private val htmlEscaper = HtmlEscapers.htmlEscaper()
    private val pathSegmentEscaper = UrlEscapers.urlPathSegmentEscaper()

    /**
     * Escapes `& < > " '` so the value is safe as element text and inside quoted attribute values.
     */
    fun escape(value: String): String =
        htmlEscaper.escape(value)

    /**
     * Percent-encodes every segment of a decoded path and keeps the `/` separators.
     */
    fun encodePath(decodedPath: String): String =
        decodedPath.split('/').joinToString(separator = "/") { pathSegmentEscaper.escape(it) }

    /**
     * Turns the raw, percent-encoded path of a request (as returned by the servlet container) into a decoded path.
     * A `+` is a literal plus in a path, so it is protected from being read as a space. Malformed escapes are kept as they are.
     */
    fun decodeRequestPath(rawPath: String): String =
        rawPath.split('/').joinToString(separator = "/") { segment ->
            runCatching { URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8) }
                .getOrDefault(segment)
        }

}
