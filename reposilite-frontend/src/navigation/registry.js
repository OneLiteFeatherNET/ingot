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
 * The one list of pages of the dashboard. A page is added or renamed here, and every
 * navigation surface reads it: the rail, the bottom bar and its More sheet, the route
 * table (src/router/routes.js builds the `meta` of each route from it), the Access tabs,
 * the document title and the route guard, which reads that meta.
 *
 * Paths and route names stay in src/router/routes.js. An entry names its route by name.
 *
 * Entry fields:
 * - key         stable identifier (overview, search, dashboard, analytics, access, console,
 *               maintenance, audit, settings, account)
 * - route       the route name in routes.js
 * - label       the text in the rail, the bar, the breadcrumbs and the document title
 * - area        work, monitor, administration, troubleshoot, or none for a page outside the rail
 * - permission  { node } or { anyNode } for a manager page, { manager: false } for an open page,
 *               { signedIn: true } for a page that needs a signed in visitor only
 * - barOrder    the place of the page in the bottom bar rule (the first four permitted pages in this order
 *               are in the bar, the rest sit under More); a page without one is never in the bar
 * - children    for Access: the side pages, each with key, route, label, group, permission and
 *               optional `detail` (the route of one principal or team of that page) and `landing`
 *               (true when the /access address may open it). Settings gets its children later.
 *
 * Functions:
 * - visiblePages(can)            the rail pages a visitor may open, in rail order (no account page)
 * - visibleChildren(key, can)    the children of a page the visitor may open
 * - groupedChildren(key, can)  the visible children in headed groups, as SideNavLayout takes them
 * - sideNavOf(key)              { label, sectionLabel, indexTo } of a page for SideNavLayout
 * - settingsGroupOf(domain)    the category of a Settings domain: Repository, Instance, Data, or Plugins for any other
 * - groupedSettingsDomains(domains)  a domain list in category groups [{ heading, domains }], empty groups left out
 * - firstAccessRoute(can)        the route /access opens: the first permitted landing page, or users
 * - accessTabOf(routeName)       the Access tab key of a route, including its detail routes
 * - railGroups(can)            the rail pages in their groups: [{ area, heading, pages }], empty groups left out
 * - barLayout(can)             { bar, more }: the first four permitted pages in bar order, and the others under
 *                               their group headings as [{ area, heading, pages }]
 * - pageByKey(key), pageLabel(key)
 * - routeMeta(key, childKey?)    the `meta` of a route: section, manager, node or anyNode, signedIn
 * - unlistedRoutes(names)        the route names that no entry, child, detail or exempt name covers;
 *                                a route added to the route table without one of these fails the suite
 *
 * `can(node)` answers for a permission node, as the session does. MORE_KEY is the bottom bar
 * item that opens the overflow pages; no page has that key.
 */

import { shallowRef } from 'vue'
import { ADMIN_NODES, ACCESS_NODES, INSTANCE_ADMIN_NODE, requiredNodes } from '../store/nodeAccess'

const MORE_KEY = 'more'

/*
 * The pages and settings sections that server plugins contribute, from GET /api/frontend/plugin-ui. The
 * manifest is read after the session is known (src/store/pluginUi.js) and replaces the previous one. The
 * static PAGES list above is never changed by it.
 */
const NO_CONTRIBUTIONS = { pages: [], sections: [], enabledPlugins: [] }
const contributions = shallowRef(NO_CONTRIBUTIONS)

// The groups a contributed page may name, in rail order. A page whose group is not one of these sits in Plugins.
const CONTRIBUTED_GROUPS = ['Repository', 'Security', 'System', 'Support']
const PLUGINS_HEADING = 'Plugins'

// The Settings category a contributed section is placed in. System has no Settings category of its own.
const SECTION_SETTINGS_GROUPS = { Repository: 'Repository', System: 'Instance' }

const setContributions = (manifest) => {
  contributions.value = manifest
    ? { pages: manifest.pages ?? [], sections: manifest.sections ?? [], enabledPlugins: manifest.enabledPlugins ?? [] }
    : NO_CONTRIBUTIONS
}

// The permission node that opens a contributed page: the node of its admin area, or the instance admin node for `*`.
const pageNodeOf = (area) => (area === '*' ? INSTANCE_ADMIN_NODE : `ingot.admin.${area}`)

const contributedKey = (page) => `${page.plugin}/${page.id}`

const contributedHeading = (page) => (CONTRIBUTED_GROUPS.includes(page.group) ? page.group : PLUGINS_HEADING)

// The contributed pages the visitor may open, each with its key and the heading of its group.
const contributedPages = (can) => contributions.value.pages
  .filter(page => can(pageNodeOf(page.area)))
  .map(page => ({ ...page, key: contributedKey(page), heading: contributedHeading(page) }))

const contributedPageByRoute = (plugin, id) =>
  contributions.value.pages.find(page => page.plugin === plugin && page.id === id)

// The node a contributed page needs, for the route guard. Undefined for a page the manifest does not name.
const contributedPageNode = (plugin, id) => {
  const page = contributedPageByRoute(plugin, id)
  return page ? pageNodeOf(page.area) : undefined
}

// The rail groups of the contributed pages: [{ heading, pages }] in the order of CONTRIBUTED_GROUPS, Plugins last.
const contributedRailGroups = (can) => [...CONTRIBUTED_GROUPS, PLUGINS_HEADING]
  .map(heading => ({ heading, pages: contributedPages(can).filter(page => page.heading === heading) }))
  .filter(group => group.pages.length > 0)

const sectionOf = (domain) => contributions.value.sections.find(section => section.domain === domain)

const settingsLabelOf = (domain) => sectionOf(domain)?.label

const enabledPlugins = () => contributions.value.enabledPlugins

// The groups of the rail, in order, and the number of pages the bottom bar keeps before More.
const AREAS = [
  { area: 'work', heading: 'Work' },
  { area: 'monitor', heading: 'Monitor' },
  { area: 'administration', heading: 'Administration' },
  { area: 'troubleshoot', heading: 'Troubleshoot' },
]
const BAR_SIZE = 4

const PAGES = [
  { key: 'overview', route: 'overview', label: 'Repositories', area: 'work', permission: { manager: false }, barOrder: 1 },
  { key: 'search', route: 'search', label: 'Search', area: 'work', permission: { manager: false }, barOrder: 2 },
  { key: 'dashboard', route: 'dashboard', label: 'Dashboard', area: 'monitor', permission: { node: ADMIN_NODES.dashboard }, barOrder: 3 },
  { key: 'analytics', route: 'analytics', label: 'Analytics', area: 'monitor', permission: { node: ADMIN_NODES.dashboard }, barOrder: 6 },
  { key: 'audit', route: 'audit', label: 'Audit log', area: 'monitor', permission: { node: ADMIN_NODES.audit }, barOrder: 7 },
  {
    key: 'access',
    route: 'access',
    label: 'Access',
    area: 'administration',
    permission: { anyNode: ACCESS_NODES },
    barOrder: 4,
    children: [
      { key: 'users', route: 'access-users', detail: 'access-user', label: 'Users', group: 'People', permission: { node: ADMIN_NODES.users }, landing: true },
      { key: 'machines', route: 'access-machines', detail: 'access-machine', label: 'Machine accounts', group: 'People', permission: { node: ADMIN_NODES.users }, landing: true },
      { key: 'teams', route: 'access-teams', detail: 'access-team', label: 'Teams', group: 'People', permission: { node: ADMIN_NODES.teams }, landing: true },
      { key: 'sso', route: 'access-sso', label: 'SSO', group: 'Sign in', permission: { node: INSTANCE_ADMIN_NODE } },
      { key: 'ldap', route: 'access-ldap', label: 'LDAP', group: 'Sign in', permission: { node: INSTANCE_ADMIN_NODE } },
      { key: 'security', route: 'access-security', label: 'Security', group: 'Sign in', permission: { node: INSTANCE_ADMIN_NODE } },
      { key: 'check', route: 'access-check', label: 'Check', group: 'Tools', permission: { anyNode: ACCESS_NODES } },
    ],
  },
  { key: 'settings', route: 'settings', label: 'Settings', area: 'administration', permission: { node: ADMIN_NODES.settings }, barOrder: 5 },
  { key: 'console', route: 'console', label: 'Console', area: 'troubleshoot', permission: { node: ADMIN_NODES.console }, barOrder: 8 },
  { key: 'maintenance', route: 'maintenance', label: 'Maintenance', area: 'troubleshoot', permission: { node: ADMIN_NODES.maintenance }, barOrder: 9 },
  { key: 'account', route: 'account', label: 'My account', area: 'none', permission: { signedIn: true } },
]

// Routes that have no page of their own: the browser of a repository path (the overview page), the
// legacy token redirects, the Settings entry below a domain, the authentication settings redirect and
// the not found page. The detail routes of Access are not listed here: each one is the `detail` of its child.
const EXEMPT_ROUTES = ['browse', 'tokens', 'token-new', 'token', 'settings-authentication', 'settings-login', 'settings-entry', 'settings-plugin', 'not-found']

const pageByKey = (key) => PAGES.find(page => page.key === key)

const childrenOf = (key) => pageByKey(key)?.children ?? []

const allowedBy = (permission, can) => requiredNodes(permission).some(node => can(node))

const isOpen = (permission) => requiredNodes(permission).length === 0

const visiblePages = (can) =>
  PAGES.filter(page => page.area !== 'none' && (isOpen(page.permission) || allowedBy(page.permission, can)))

const visibleChildren = (key, can) => childrenOf(key).filter(child => allowedBy(child.permission, can))

// Children in their groups, in the order the groups first appear: [{ heading, links: [{ key, label, to }] }].
// A group without a visible child is left out. `to` is the router location of the child.
const groupedChildren = (key, can) => {
  const groups = []
  visibleChildren(key, can).forEach(child => {
    let group = groups.find(entry => entry.heading === child.group)
    if (!group) {
      group = { heading: child.group, links: [] }
      groups.push(group)
    }
    group.links.push({ key: child.key, label: child.label, to: { name: child.route } })
  })
  return groups
}

// What SideNavLayout needs of a page except the groups: the accessible name of the navigation, the first
// crumb and the location of the index. All three come from the entry of the page.
const sideNavOf = (key) => {
  const page = pageByKey(key)
  return { label: `${page.label} sections`, sectionLabel: page.label, indexTo: { name: page.route } }
}

// The Settings domains are filled from the server schema at run time, so Settings has no static children.
// A fixed mapping places the domains the server is known to list in a category; any other domain, such
// as one a plugin adds, falls into Plugins and so never fails to show.
const SETTINGS_GROUPS = [
  { heading: 'Repository', domains: ['maven', 'javadoc', 'cleanup'] },
  { heading: 'Instance', domains: ['web', 'frontend'] },
  { heading: 'Data', domains: ['statistics', 'audit', 'search'] },
  { heading: 'Plugins', domains: [] }
]

const SETTINGS_FALLBACK_GROUP = 'Plugins'

// A domain a plugin placed with a section takes that section's category; the other domains keep the mapping above.
const settingsGroupOf = (domain) => {
  const section = sectionOf(domain)
  if (section) return SECTION_SETTINGS_GROUPS[section.group] ?? SETTINGS_FALLBACK_GROUP
  return SETTINGS_GROUPS.find(group => group.domains.includes(domain))?.heading ?? SETTINGS_FALLBACK_GROUP
}

// A domain list in groups: [{ heading, domains }], in the order of SETTINGS_GROUPS, the domains in the order
// they were given. A group without a domain is left out.
const groupedSettingsDomains = (domains) => SETTINGS_GROUPS
  .map(group => ({ heading: group.heading, domains: domains.filter(domain => settingsGroupOf(domain) === group.heading) }))
  .filter(group => group.domains.length > 0)

const firstAccessRoute = (can) => {
  const landing = visibleChildren('access', can).find(child => child.landing)
    ?? childrenOf('access').find(child => child.landing)
  return landing.route
}

const accessTabOf = (routeName) =>
  childrenOf('access').find(child => child.route === routeName || child.detail === routeName)?.key

const groupPages = (pages) => AREAS
  .map(({ area, heading }) => ({ area, heading, pages: pages.filter(page => page.area === area) }))
  .filter(group => group.pages.length > 0)

const railGroups = (can) => groupPages(visiblePages(can))

const barLayout = (can) => {
  const ordered = visiblePages(can)
    .filter(page => page.barOrder !== undefined)
    .sort((a, b) => a.barOrder - b.barOrder)
  return { bar: ordered.slice(0, BAR_SIZE), more: groupPages(ordered.slice(BAR_SIZE)) }
}

const pageLabel = (key) => pageByKey(key)?.label ?? key


// The meta of a route. `manager` stays on every route that names a node or an alternative, because
// it is what a session without a node list is judged by, and what tells a visitor to sign in.
const metaOf = (section, permission) => {
  if (permission.signedIn) return { section, signedIn: true }
  if (isOpen(permission)) return { section }
  return permission.node
    ? { section, manager: true, node: permission.node }
    : { section, manager: true, anyNode: permission.anyNode }
}

const routeMeta = (key, childKey) => {
  const page = pageByKey(key)
  const permission = childKey ? childrenOf(key).find(child => child.key === childKey).permission : page.permission
  return metaOf(page.key, permission)
}

const unlistedRoutes = (names) => {
  const covered = new Set([
    ...PAGES.map(page => page.route),
    ...PAGES.flatMap(page => childrenOf(page.key)).flatMap(child => [child.route, child.detail].filter(Boolean)),
    ...EXEMPT_ROUTES,
  ])
  return names.filter(name => !covered.has(name))
}

export {
  PAGES,
  MORE_KEY,
  setContributions,
  contributedPages,
  contributedPageByRoute,
  contributedPageNode,
  contributedRailGroups,
  settingsLabelOf,
  enabledPlugins,
  pageByKey,
  visiblePages,
  visibleChildren,
  groupedChildren,
  sideNavOf,
  settingsGroupOf,
  groupedSettingsDomains,
  firstAccessRoute,
  accessTabOf,
  pageLabel,
  railGroups,
  barLayout,
  routeMeta,
  unlistedRoutes,
}
