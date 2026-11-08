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
 * - overflow    true when the bottom bar keeps the page under More (the rule the bar reads today)
 * - barOrder    the place of the page in the bottom bar rule; a page without one is never in the bar
 * - children    for Access: the side pages, each with key, route, label, group, permission and
 *               optional `detail` (the route of one principal or team of that page) and `landing`
 *               (true when the /access address may open it). Settings gets its children later.
 *
 * Functions:
 * - visiblePages(can)            the rail pages a visitor may open, in rail order (no account page)
 * - visibleChildren(key, can)    the children of a page the visitor may open
 * - groupedChildren(key, can)  the visible children in headed groups, as SideNavLayout takes them
 * - sideNavOf(key)              { label, sectionLabel, indexTo } of a page for SideNavLayout
 * - firstAccessRoute(can)        the route /access opens: the first permitted landing page, or users
 * - accessTabOf(routeName)       the Access tab key of a route, including its detail routes
 * - pageByKey(key), pageLabel(key), isOverflow(key)
 * - routeMeta(key, childKey?)    the `meta` of a route: section, manager, node or anyNode, signedIn
 * - unlistedRoutes(names)        the route names that no entry, child, detail or exempt name covers;
 *                                a route added to the route table without one of these fails the suite
 *
 * `can(node)` answers for a permission node, as the session does. MORE_KEY is the bottom bar
 * item that opens the overflow pages; no page has that key.
 */

import { ADMIN_NODES, ACCESS_NODES, INSTANCE_ADMIN_NODE, requiredNodes } from '../store/nodeAccess'

const MORE_KEY = 'more'

const PAGES = [
  { key: 'overview', route: 'overview', label: 'Overview', area: 'work', permission: { manager: false }, barOrder: 1, overflow: false },
  { key: 'search', route: 'search', label: 'Search', area: 'work', permission: { manager: false }, barOrder: 2, overflow: false },
  { key: 'dashboard', route: 'dashboard', label: 'Dashboard', area: 'monitor', permission: { node: ADMIN_NODES.dashboard }, barOrder: 3, overflow: false },
  { key: 'analytics', route: 'analytics', label: 'Analytics', area: 'monitor', permission: { node: ADMIN_NODES.dashboard }, barOrder: 6, overflow: true },
  {
    key: 'access',
    route: 'access',
    label: 'Access',
    area: 'administration',
    permission: { anyNode: ACCESS_NODES },
    barOrder: 4,
    overflow: false,
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
  { key: 'console', route: 'console', label: 'Console', area: 'troubleshoot', permission: { node: ADMIN_NODES.console }, barOrder: 8, overflow: true },
  { key: 'maintenance', route: 'maintenance', label: 'Maintenance', area: 'troubleshoot', permission: { node: ADMIN_NODES.maintenance }, barOrder: 9, overflow: true },
  { key: 'audit', route: 'audit', label: 'Audit', area: 'monitor', permission: { node: ADMIN_NODES.audit }, barOrder: 7, overflow: true },
  { key: 'settings', route: 'settings', label: 'Settings', area: 'administration', permission: { node: ADMIN_NODES.settings }, barOrder: 5, overflow: true },
  { key: 'account', route: 'account', label: 'My account', area: 'none', permission: { signedIn: true } },
]

// Routes that have no page of their own: the browser of a repository path (the overview page), the
// legacy token redirects, the Settings entry below a domain, the authentication settings redirect and
// the not found page. The detail routes of Access are not listed here: each one is the `detail` of its child.
const EXEMPT_ROUTES = ['browse', 'tokens', 'token-new', 'token', 'settings-authentication', 'settings-login', 'settings-entry', 'not-found']

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

const firstAccessRoute = (can) => {
  const landing = visibleChildren('access', can).find(child => child.landing)
    ?? childrenOf('access').find(child => child.landing)
  return landing.route
}

const accessTabOf = (routeName) =>
  childrenOf('access').find(child => child.route === routeName || child.detail === routeName)?.key

const pageLabel = (key) => pageByKey(key)?.label ?? key

const isOverflow = (key) => pageByKey(key)?.overflow === true

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
  pageByKey,
  visiblePages,
  visibleChildren,
  groupedChildren,
  sideNavOf,
  firstAccessRoute,
  accessTabOf,
  pageLabel,
  isOverflow,
  routeMeta,
  unlistedRoutes,
}
