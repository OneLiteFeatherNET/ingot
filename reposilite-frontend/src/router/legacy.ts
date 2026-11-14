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
 * Links made before the dashboard used path based URLs look like `#/<path>?tab=<section>`.
 * This turns one of them into the location of the route that replaces it, or returns null
 * when the hash is not such a link. Pure, so the mapping is tested without a browser.
 *
 * The old section names that have a route of their own. Any other tab, and a missing or
 * repeated one, was shown as Overview, so those keep their repository path.
 */
const SECTION_ROUTES = ['dashboard', 'tokens', 'console', 'settings']

const decodeSegment = (segment) => {
  try {
    return decodeURIComponent(segment)
  } catch {
    // Not valid percent encoding, so the browser would not have produced it. Keep it as written.
    return segment
  }
}

const legacyLocation = (hash) => {
  if (typeof hash !== 'string' || !hash.startsWith('#/')) {
    return null
  }

  const body = hash.slice(1)
  const questionMark = body.indexOf('?')
  const rawPath = questionMark === -1 ? body : body.slice(0, questionMark)
  const search = new URLSearchParams(questionMark === -1 ? '' : body.slice(questionMark + 1))

  const tabs = search.getAll('tab')
  const tab = tabs.length === 1 ? tabs[0] : undefined
  search.delete('tab')
  const query = Object.fromEntries(search)

  if (SECTION_ROUTES.includes(tab)) {
    return { name: tab, query }
  }

  const qualifier = rawPath
    .split('/')
    .filter(segment => segment !== '')
    .map(decodeSegment)

  return qualifier.length === 0
    ? { name: 'overview', query }
    : { name: 'browse', params: { qualifier }, query }
}

export { legacyLocation }
