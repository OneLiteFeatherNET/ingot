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

/** The OpenAPI name of HTTP Basic authentication with an access token as the user name and its secret as the password. */
const val TOKEN_BASIC_SCHEME = "tokenBasic"

/** The OpenAPI name of the bearer session that a login returns. */
const val SESSION_BEARER_SCHEME = "sessionBearer"

/** The OpenAPI name of the browser session cookie that a login sets. Routes that only a browser session may call declare it. */
const val SESSION_COOKIE_SCHEME = "sessionCookie"
