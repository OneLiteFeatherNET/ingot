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

package net.onelitefeather.ingot.search.config

import java.net.URI
import java.net.URISyntaxException

/**
 * The cluster base URLs of a search plugin, from its `URLS` variable: a comma-separated list, in the configured order. Only `https`
 * URLs are accepted unless [allowHttp] is set. A URL with user information is refused, because credentials belong in the user and
 * password variables, and the refusal never echoes the URL.
 *
 * [productName] is the name of the plugin in the message of a missing variable, for example `OpenSearch`.
 */
fun SearchVariables.urls(allowHttp: Boolean, productName: String): List<URI> {
    val (variable, raw) = lookup(URLS)
        ?: throw IllegalArgumentException("${name(URLS)} is required when the $productName plugin is enabled")
    val entries = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    require(entries.isNotEmpty()) { "$variable lists no URL" }
    return entries.map { entry -> url(variable, entry, allowHttp) }
}

private const val URLS = "URLS"

private fun SearchVariables.url(variable: String, entry: String, allowHttp: Boolean): URI {
    val uri = try {
        URI(entry)
    } catch (failure: URISyntaxException) {
        throw IllegalArgumentException("$variable contains a value that is not a valid URL")
    }
    require(uri.rawAuthority?.contains('@') != true) {
        "$variable contains a URL with user information, set ${name("USERNAME")} and ${name("PASSWORD")} instead"
    }
    when (uri.scheme?.lowercase()) {
        "https" -> Unit
        "http" -> require(allowHttp) {
            "$variable contains an http URL, set ${name("ALLOW_HTTP")}=true to accept plain HTTP"
        }
        else -> throw IllegalArgumentException("$variable only accepts https URLs")
    }
    require(!uri.host.isNullOrBlank()) { "$variable contains a URL without a host" }
    return uri
}
