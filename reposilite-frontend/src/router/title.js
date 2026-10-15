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

import { sectionLabel } from '../pages/index-tabs'
import { qualifierOf } from './paths'

/*
 * The document title of a route: the repository path while one is browsed, the section name
 * in a manager section, and the site title alone on overview.
 */
const pageTitle = (route, siteTitle) => {
  if (route.name === 'not-found') {
    return `Not found · ${siteTitle}`
  }
  if (route.name === 'browse') {
    const qualifier = qualifierOf(route.params)
    return qualifier ? `${qualifier} · ${siteTitle}` : siteTitle
  }
  const section = route.meta?.section
  if (section && section !== 'overview') {
    return `${sectionLabel(section)} · ${siteTitle}`
  }
  return siteTitle
}

export { pageTitle }
