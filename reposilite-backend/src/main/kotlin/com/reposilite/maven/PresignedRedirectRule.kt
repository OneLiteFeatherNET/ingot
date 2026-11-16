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

package com.reposilite.maven

import com.reposilite.shared.http.CacheScope
import java.time.Duration

/** A file answer that redirects to a presigned URL, with the Cache-Control of the redirect. */
internal data class PresignedRedirect(val url: String, val cacheControl: String)

/**
 * Whether a file answer may redirect to a presigned URL. Only a `GET` of a cacheable file without a range or a conditional header
 * qualifies, and only with the [CacheScope.PUBLIC] scope. A presigned URL is a bearer link, so a private file, a file that a
 * credentialed mirror serves, and a file of a repository whose visibility is not PUBLIC are always served by the instance.
 */
internal fun presignedRedirectApplies(method: String, hasRangeOrConditionalHeaders: Boolean, cacheable: Boolean, scope: CacheScope): Boolean =
    method == "GET" && !hasRangeOrConditionalHeaders && cacheable && scope == CacheScope.PUBLIC

/**
 * The Cache-Control of a redirect: the public scope with the smaller of [maxAgeSeconds] and half the lifetime of the URL, so that a
 * shared cache drops the redirect before the presigned URL expires. The half leaves a margin for clock skew.
 */
internal fun redirectCacheControl(maxAgeSeconds: Long, ttl: Duration): String =
    "public, max-age=${minOf(maxAgeSeconds, ttl.seconds / 2)}"
