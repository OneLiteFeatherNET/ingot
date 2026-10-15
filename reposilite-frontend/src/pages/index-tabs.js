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
 * The sections the navigation offers. Which section a route belongs to is written in the
 * route table (src/router/routes.js); this table says what each section is called, which
 * route its link opens and whether a visitor has to be a manager to see it.
 *
 * The bottom bar on a phone holds five destinations at most. A section marked `overflow` is
 * kept out of it and sits under "More" instead. The rail beside the page has room for all of them,
 * seven destinations being the most a navigation rail holds. Maintenance, the audit log and Settings
 * are the rarer admin pages, so they overflow and the daily pages (overview, dashboard, tokens,
 * console) stay one tap away.
 */

const SECTIONS = [
  { key: 'overview', label: 'Overview', manager: false, route: 'overview' },
  { key: 'dashboard', label: 'Dashboard', manager: true, route: 'dashboard' },
  { key: 'tokens', label: 'Tokens', manager: true, route: 'tokens' },
  { key: 'console', label: 'Console', manager: true, route: 'console' },
  { key: 'maintenance', label: 'Maintenance', manager: true, route: 'maintenance', overflow: true },
  { key: 'audit', label: 'Audit', manager: true, route: 'audit', overflow: true },
  { key: 'settings', label: 'Settings', manager: true, route: 'settings', overflow: true },
]

const MORE_KEY = 'more'

const visibleSections = (isManager) =>
  SECTIONS.filter(section => !section.manager || isManager)

const sectionLabel = (key) =>
  SECTIONS.find(section => section.key === key)?.label ?? key

/** The sections that sit under "More" in the bottom bar. */
const isOverflow = (key) =>
  SECTIONS.find(section => section.key === key)?.overflow === true

export { SECTIONS, MORE_KEY, visibleSections, sectionLabel, isOverflow }
