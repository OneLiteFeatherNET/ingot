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

package com.reposilite.web.api

import com.fasterxml.jackson.databind.node.ObjectNode

/** The OpenAPI name of HTTP Basic authentication with an access token as the user name and its secret as the password. */
const val TOKEN_BASIC_SCHEME = "tokenBasic"

/** The OpenAPI name of an access token secret sent as a bearer value. A login session is not sent this way. */
const val SESSION_BEARER_SCHEME = "sessionBearer"

/** The OpenAPI name of the browser session cookie that a login sets. Routes that need a login list it as an alternative. */
const val SESSION_COOKIE_SCHEME = "sessionCookie"

/** The description of the bearer scheme in the served document: a bearer value is an access token secret, never a login. */
private const val BEARER_DESCRIPTION = "The secret of an access token, sent as a bearer value. Browser logins are not credentials of this scheme."

/**
 * Sets the description of the bearer scheme. The OpenAPI plugin writes no description for a bearer scheme, so the
 * definition processor adds it to the finished document.
 */
fun describeSecuritySchemes(document: ObjectNode): ObjectNode {
    val schemes = document.path("components").path("securitySchemes") as? ObjectNode ?: return document
    (schemes.path(SESSION_BEARER_SCHEME) as? ObjectNode)?.put("description", BEARER_DESCRIPTION)
    return document
}
