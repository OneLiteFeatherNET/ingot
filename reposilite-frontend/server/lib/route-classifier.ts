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

// Decides who answers a request. Dashboard addresses get the document, the bundled assets come from the
// build, and everything else belongs to the server. Only whole segments match, so a repository named like
// a dashboard word that continues it (settingsfoo) is proxied.
//
// The navigation segments are the dashboard's only for a browser navigation (Sec-Fetch-Mode: navigate).
// Plugin reader pages live at /plugins/<plugin>/<page>, and a repository named plugins must keep answering
// Maven and Gradle clients, so every other request to that segment is proxied to the server.
export const navigationSegments: readonly string[] = ['plugins']

// Returns the path below the base path, or null when the request is outside it.
function stripBase(pathname: string, basePath: string): string | null {
  if (basePath === '/') {
    return pathname
  }
  const bare = basePath.slice(0, -1)
  if (pathname === bare) {
    return '/'
  }
  if (pathname.startsWith(basePath)) {
    return `/${pathname.slice(basePath.length)}`
  }
  return null
}

export interface RouteOptions {
  segments: readonly string[]
  basePath?: string
  // True when the request is a browser navigation; only then do the navigation segments get the document.
  navigate?: boolean
}

// The answer for one request: the kind decides who serves it, and the path is the part below the base path.
export type Route =
  | { kind: 'health' }
  | { kind: 'outside' }
  | { kind: 'document'; path: string }
  | { kind: 'asset'; path: string }
  | { kind: 'favicon'; path: string }
  | { kind: 'proxy'; path: string }

export function classify(pathname: string, { segments, basePath = '/', navigate = false }: RouteOptions): Route {
  if (pathname === '/healthz') {
    return { kind: 'health' }
  }

  const rest = stripBase(pathname, basePath)
  if (rest === null) {
    return { kind: 'outside' }
  }
  if (rest === '/' || rest === '/index.html') {
    return { kind: 'document', path: '/' }
  }
  if (rest.startsWith('/assets/')) {
    return { kind: 'asset', path: rest }
  }
  if (rest === '/favicon.png') {
    return { kind: 'favicon', path: rest }
  }

  const first = rest.split('/')[1]
  if (segments.includes(first) || (navigate && navigationSegments.includes(first))) {
    return { kind: 'document', path: rest }
  }
  return { kind: 'proxy', path: rest }
}
