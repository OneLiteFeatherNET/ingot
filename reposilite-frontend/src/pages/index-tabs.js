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
 * are the rarer admin pages, so they overflow and the daily pages (overview, dashboard, access,
 * console) stay one tap away.
 */

import { ACCESS_NODES, ADMIN_NODES, requiredNodes } from '../store/nodeAccess'

const SECTIONS = [
  { key: 'overview', label: 'Overview', manager: false, route: 'overview' },
  { key: 'dashboard', label: 'Dashboard', manager: true, node: ADMIN_NODES.dashboard, route: 'dashboard' },
  { key: 'access', label: 'Access', manager: true, anyNode: ACCESS_NODES, route: 'access' },
  { key: 'console', label: 'Console', manager: true, node: ADMIN_NODES.console, route: 'console' },
  { key: 'maintenance', label: 'Maintenance', manager: true, node: ADMIN_NODES.maintenance, route: 'maintenance', overflow: true },
  { key: 'audit', label: 'Audit', manager: true, node: ADMIN_NODES.audit, route: 'audit', overflow: true },
  { key: 'settings', label: 'Settings', manager: true, node: ADMIN_NODES.settings, route: 'settings', overflow: true },
]

const MORE_KEY = 'more'

// `can(node)` answers for a permission node. A session without a node list answers with the
// manager flag, so the legacy behaviour lives in the session and not here.
const visibleSections = (can) =>
  SECTIONS.filter(section => !section.manager || requiredNodes(section).some(node => can(node)))

// Pages that are not in the navigation but have a name in the document title.
const EXTRA_LABELS = { account: 'My account' }

const sectionLabel = (key) =>
  SECTIONS.find(section => section.key === key)?.label ?? EXTRA_LABELS[key] ?? key

/** The sections that sit under "More" in the bottom bar. */
const isOverflow = (key) =>
  SECTIONS.find(section => section.key === key)?.overflow === true

export { SECTIONS, MORE_KEY, visibleSections, sectionLabel, isOverflow }
