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

/*
 * The address a single sign on provider starts at. The server names it as a path (loginUrl), the
 * dashboard resolves it against the API base, and the current page goes along as `redirect`, so the
 * provider brings the visitor back to the page they were on. The redirect is a path of this
 * dashboard, relative to its mount point, and is only ever read by the server.
 *
 * `resolveUrl` turns the server path into an address; the dashboard passes the API base lookup.
 */
const ABSOLUTE = /^https?:\/\//

const providerLoginHref = (loginUrl, returnTo, resolveUrl = (path) => path) => {
  const target = ABSOLUTE.test(loginUrl) ? loginUrl : resolveUrl(loginUrl)
  const url = new URL(target, window.location.href)
  url.searchParams.set('redirect', returnTo)
  return url.origin === window.location.origin
    ? `${url.pathname}${url.search}`
    : url.href
}

export { providerLoginHref }
