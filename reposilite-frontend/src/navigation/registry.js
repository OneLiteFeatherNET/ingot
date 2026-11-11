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
 * navigation surface reads it: the rail, the bottom bar, the route table (src/router/routes.js
 * builds the `meta` of each route from it), the Access and Settings side navigation, the settings
 * hub, the document title and the route guard, which reads that meta.
 *
 * The rail holds four entries: Browse (overview), Search, Dashboard and Administration. Administration
 * opens the hub at /settings and is the group of every page an operator uses to run the instance; ADMIN_TREE
 * lists those pages in four groups (Repository, Security, System, Support). Every Administration page shows
 * this one tree in its side navigation (adminGroups), with the current page marked.
 *
 * Paths and route names stay in src/router/routes.js. An entry names its route by name.
 *
 * Entry fields (PAGES):
 * - key         stable identifier (overview, search, dashboard, settings, access, analytics, audit,
 *               console, maintenance, account)
 * - route       the route name in routes.js
 * - label       the text in the rail, the bar, the breadcrumbs and the document title
 * - area        rail for an entry of the rail, or none for a page outside it
 * - permission  { node } or { anyNode } that the route needs, { manager: false } for an open page,
 *               { signedIn: true } for a page that needs a signed in visitor only
 * - reach       optional. The permission that shows the entry in the rail, when it is wider than the
 *               route's own permission (Administration is shown to anyone with an Administration page)
 * - barOrder    the place of the page in the bottom bar rule (the first four permitted pages in this order
 *               are in the bar, the rest sit under More); a page without one is never in the bar
 * - children    for Access: the side pages, each with key, route, label, group, permission and
 *               optional `detail` (the route of one principal or team of that page) and `landing`
 *               (true when the /access address may open it).
 *
 * Functions:
 * - visiblePages(can)            the rail pages a visitor may open, in rail order (no account page)
 * - visibleChildren(key, can)    the children of a page the visitor may open
 * - adminGroups(can, options)    the Administration tree the visitor may open, in headed groups
 *                                [{ heading, links: [{ key, label, to }] }]; settings domains are passed in
 * - administrationLocation(can)  where the Administration entry of the rail opens: the hub, or the one page
 *                                the visitor may open
 * - sideNavOf(key)              { label, sectionLabel, indexTo } of a page for SideNavLayout
 * - settingsGroupOf(domain)    the category of a Settings domain in the legacy Settings groups (Repository, Instance, Data, Plugins)
 * - reservedDomainIds(domains)  the settings domain ids that a reserved page name under /settings shadows
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

// The groups of the rail. The four entries of the rail form one unlabelled block, so the group has no heading.
const AREAS = [{ area: 'rail', heading: undefined }]
const BAR_SIZE = 4

// The settings domains that belong to the Repository group of the Administration tree. Any other domain, a plugin
// domain included, is listed under System, so it never fails to show.
const REPOSITORY_DOMAINS = ['maven', 'javadoc', 'cleanup']

// The page names under /settings that a settings domain may not take as its id, since the page is served first.
const RESERVED_SETTINGS_PAGES = ['repositories']

/*
 * The Administration tree. A static entry names its route, label and permission. The settings domains are not
 * listed here, because the server decides which exist; they are added to their group by adminGroups.
 */
const ADMIN_TREE = [
  {
    heading: 'Repository',
    entries: [
      { key: 'repositories', route: 'settings-repositories', detail: 'settings-repository', label: 'Repositories', permission: { node: ADMIN_NODES.settings } },
    ],
  },
  {
    heading: 'Security',
    entries: [
      { key: 'users', route: 'access-users', label: 'Users', permission: { node: ADMIN_NODES.users } },
      { key: 'machines', route: 'access-machines', label: 'Machine accounts', permission: { node: ADMIN_NODES.users } },
      { key: 'teams', route: 'access-teams', label: 'Teams', permission: { node: ADMIN_NODES.teams } },
      { key: 'sso', route: 'access-sso', label: 'SSO', permission: { node: INSTANCE_ADMIN_NODE } },
      { key: 'ldap', route: 'access-ldap', label: 'LDAP', permission: { node: INSTANCE_ADMIN_NODE } },
      { key: 'security', route: 'access-security', label: 'Security settings', permission: { node: INSTANCE_ADMIN_NODE } },
      { key: 'check', route: 'access-check', label: 'Permission check', permission: { anyNode: ACCESS_NODES } },
      { key: 'audit', route: 'audit', label: 'Audit log', permission: { node: ADMIN_NODES.audit } },
    ],
  },
  { heading: 'System', entries: [] },
  {
    heading: 'Support',
    entries: [
      { key: 'console', route: 'console', label: 'Console', permission: { node: ADMIN_NODES.console } },
      { key: 'maintenance', route: 'maintenance', label: 'Maintenance', permission: { node: ADMIN_NODES.maintenance } },
      { key: 'analytics', route: 'analytics', label: 'Analytics', permission: { node: ADMIN_NODES.dashboard } },
    ],
  },
]

const ADMIN_ENTRIES = ADMIN_TREE.flatMap(group => group.entries)

// The Administration entry is shown to a visitor who may open the hub or any page of the tree.
const ADMINISTRATION_REACH = {
  anyNode: [ADMIN_NODES.settings, ...ADMIN_ENTRIES.flatMap(entry => requiredNodes(entry.permission))],
}

const PAGES = [
  { key: 'overview', route: 'overview', label: 'Browse', area: 'rail', permission: { manager: false }, barOrder: 1 },
  { key: 'search', route: 'search', label: 'Search', area: 'rail', permission: { manager: false }, barOrder: 2 },
  { key: 'dashboard', route: 'dashboard', label: 'Dashboard', area: 'rail', permission: { node: ADMIN_NODES.dashboard }, barOrder: 3 },
  {
    key: 'settings',
    route: 'settings',
    label: 'Administration',
    area: 'rail',
    permission: { node: ADMIN_NODES.settings },
    reach: ADMINISTRATION_REACH,
    barOrder: 4,
  },
  {
    key: 'access',
    route: 'access',
    label: 'Access',
    area: 'none',
    permission: { anyNode: ACCESS_NODES },
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
  { key: 'analytics', route: 'analytics', label: 'Analytics', area: 'none', permission: { node: ADMIN_NODES.dashboard } },
  { key: 'audit', route: 'audit', label: 'Audit log', area: 'none', permission: { node: ADMIN_NODES.audit } },
  { key: 'console', route: 'console', label: 'Console', area: 'none', permission: { node: ADMIN_NODES.console } },
  { key: 'maintenance', route: 'maintenance', label: 'Maintenance', area: 'none', permission: { node: ADMIN_NODES.maintenance } },
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
  PAGES.filter(page => page.area !== 'none' && (isOpen(page.permission) || allowedBy(page.reach ?? page.permission, can)))

const visibleChildren = (key, can) => childrenOf(key).filter(child => allowedBy(child.permission, can))

/*
 * The Administration tree a visitor may open, in its groups: [{ heading, links: [{ key, label, to }] }]. `to` is the
 * router location of the link. A group without a visible link is left out. The settings domains come from the
 * caller (`domains` is the list the server gave, Access domains left out), and are listed only to a visitor with
 * the settings node, each in the group treeGroupOf names. `titleOf` gives their label.
 */
const adminGroups = (can, { domains = [], titleOf = (domain) => domain } = {}) => ADMIN_TREE
  .map(group => {
    const pages = group.entries
      .filter(entry => allowedBy(entry.permission, can))
      .map(entry => ({ key: entry.key, label: entry.label, to: { name: entry.route } }))
    const settingsLinks = can(ADMIN_NODES.settings)
      ? domains
        .filter(domain => treeGroupOf(domain) === group.heading)
        .map(domain => ({ key: domain, label: titleOf(domain), to: { name: 'settings', params: { domain } } }))
      : []
    return { heading: group.heading, links: [...pages, ...settingsLinks] }
  })
  .filter(group => group.links.length > 0)

/*
 * Where the Administration entry of the rail opens: the hub when the visitor may open it, otherwise the first
 * Administration page the visitor may open (for example the audit log of a visitor with only that node).
 */
const administrationLocation = (can) => {
  if (can(ADMIN_NODES.settings)) return { name: 'settings' }
  const first = ADMIN_ENTRIES.find(entry => allowedBy(entry.permission, can))
  return first ? { name: first.route } : { name: 'settings' }
}

// What SideNavLayout needs of a page except the groups: the accessible name of the navigation, the first
// crumb and the location of the index. The navigation is the Administration tree on every page of it.
// The first crumb of an Administration page is the section, and the page is the last crumb, so a page never repeats itself.
const sideNavOf = (key) => {
  const page = pageByKey(key)
  return { label: 'Administration', sectionLabel: 'Administration', indexTo: { name: page.route } }
}

// The group of a Settings domain in the Administration tree: Repository for the repository domains and a plugin section
// in the Repository group, System for every other domain, a plugin domain included.
const treeGroupOf = (domain) => {
  const section = sectionOf(domain)
  if (section) return section.group === 'Repository' ? 'Repository' : 'System'
  return REPOSITORY_DOMAINS.includes(domain) ? 'Repository' : 'System'
}

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

// The settings domain ids that a reserved page name under /settings would shadow.
const reservedDomainIds = (domains) => domains.filter(domain => RESERVED_SETTINGS_PAGES.includes(domain))

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
    ...ADMIN_ENTRIES.flatMap(entry => [entry.route, entry.detail].filter(Boolean)),
    ...EXEMPT_ROUTES,
  ])
  return names.filter(name => !covered.has(name))
}

export {
  PAGES,
  ADMIN_TREE,
  MORE_KEY,
  setContributions,
  contributedPages,
  contributedPageByRoute,
  contributedPageNode,
  contributedRailGroups,
  pluginPageRedirect,
  settingsLabelOf,
  enabledPlugins,
  pageByKey,
  visiblePages,
  visibleChildren,
  adminGroups,
  administrationLocation,
  sideNavOf,
  settingsGroupOf,
  reservedDomainIds,
  firstAccessRoute,
  accessTabOf,
  pageLabel,
  railGroups,
  barLayout,
  routeMeta,
  unlistedRoutes,
}
