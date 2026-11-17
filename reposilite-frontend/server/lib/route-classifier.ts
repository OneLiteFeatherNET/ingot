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

// Returns the path below the base path, or null when the request is outside it.
function stripBase(pathname, basePath) {
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

export function classify(pathname, { segments, basePath = '/' }) {
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
  if (segments.includes(first)) {
    return { kind: 'document', path: rest }
  }
  return { kind: 'proxy', path: rest }
}
