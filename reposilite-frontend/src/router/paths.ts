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
 * The repository path a browse URL names, as the file browser and the API see it: segments
 * joined by slashes, with no leading or trailing one. The router hands the browse route its
 * segments as a list, and the overview route has none at all.
 */

import type { RouteLocationRaw } from 'vue-router'

const segmentsOf = (path: string): string[] => path.split('/').filter(segment => segment !== '')

// The route params of a browse route: `qualifier` is the repository path, one segment per array item.
const qualifierOf = (params: { qualifier?: string | string[] } = {}): string =>
  [params.qualifier ?? []].flat().filter(segment => segment !== '').join('/')

// The overview is the root of the browser, so the empty path maps to it rather than to a
// browse route with nothing in it.
const browseLocation = (path: string): RouteLocationRaw => {
  const qualifier = segmentsOf(path)
  return qualifier.length === 0
    ? { name: 'overview' }
    : { name: 'browse', params: { qualifier } }
}

const parentPathOf = (path: string): string => segmentsOf(path).slice(0, -1).join('/')

const childPath = (path: string, name: string): string => [...segmentsOf(path), name].join('/')

export { qualifierOf, browseLocation, parentPathOf, childPath }
