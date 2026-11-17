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
 * navigation surface reads it: the rail, the bottom bar, the route table (src/router/routes.ts
 * builds the `meta` of each route from it), the Access and Settings side navigation, the settings
 * hub, the document title and the route guard, which reads that meta.
 *
 * The rail holds three entries: Browse (overview), Search and Administration. Administration
 * opens the hub at /settings and is the group of every page an operator uses to run the instance; ADMIN_TREE
 * lists those pages in four groups. A group has an id, the value a plugin manifest names (Repository, Security,
 * System, Support), and a display heading (GROUP_HEADINGS: Artifacts, Access, Instance, Monitoring and support).
 * Every Administration page shows this one tree in its side navigation (adminGroups), with the current page marked.
 *
 * Paths and route names stay in src/router/routes.ts. An entry names its route by name.
 *
 * Entry fields (PAGES):
 * - key         stable identifier (overview, search, dashboard, settings, access, analytics, audit,
 *               console, maintenance, account)
 * - route       the route name in routes.ts
 * - label       the text in the rail, the bar, the breadcrumbs and the document title
 * - shortLabel  optional. The text the bottom bar shows in place of `label`, for a label too wide for an equal share of
 *               a 320 px window (Administration shows Admin in the bar). The rail and the other places keep `label`.
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
 *                                [{ id, heading, links: [{ key, label, description, to }] }]; settings domains are passed in
 * - shortcutsOf(key, can)        the links a page draws to its related pages: [{ label, to }], each one the visitor may open
 * - currentTreeKey(route, domain) the key of the tree entry on screen (`page:<key>` or `settings:<domain>`), or undefined
 * - administrationLocation(can)  where the Administration entry of the rail opens: the hub, or the one page
 *                                the visitor may open
 * - sideNavOf(key)              { label, sectionLabel, indexTo } of a page for SideNavLayout
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
import type { RouteRecordName } from 'vue-router'
import { ADMIN_NODES, ACCESS_NODES, INSTANCE_ADMIN_NODE, requiredNodes } from '../store/nodeAccess'

// A permission node a route or an entry needs. `anyNode` lists the alternatives, of which one is enough.
export interface Permission {
  node?: string
  anyNode?: readonly string[]
  manager?: boolean
  signedIn?: boolean
}

// Whether the visitor holds a permission node, as the session answers it.
export type Can = (node: string) => boolean

// The `meta` of a route. `section` names the page the route belongs to, or null for the not found page.
export type RouteMeta = {
  section?: string | null
  manager?: boolean
  node?: string
  anyNode?: readonly string[]
  signedIn?: boolean
  [key: string]: unknown
}

// A location of a named route, with its params. The router's own location type also allows a path, which these never are.
export interface NamedLocation {
  name: string
  params?: Record<string, string | string[]>
  query?: Record<string, string>
}

export interface ChildPage {
  key: string
  route: string
  detail?: string
  label: string
  group: string
  permission: Permission
  landing?: boolean
}

export interface Page {
  key: string
  route: string
  label: string
  shortLabel?: string
  area: string
  permission: Permission
  reach?: Permission
  barOrder?: number
  children?: ChildPage[]
  requires?: () => boolean
}

// A page entry of the Administration tree. A settings domain entry is the other kind, see DomainEntry.
export interface PageEntry {
  key: string
  route: string
  detail?: string
  label: string
  permission: Permission
  description: string
  keywords: string
  requires?: () => boolean
  domain?: undefined
}

export interface DomainEntry {
  domain: string
  label: string
  description: string
  keywords: string
  // Opts the domain into showing the sections of its settings form as tabs (see sectionTabsOf).
  sectionTabs?: boolean
  key?: undefined
  route?: undefined
  detail?: undefined
  permission?: undefined
  requires?: undefined
}

export type TreeEntry = PageEntry | DomainEntry

export interface TreeGroup {
  id: string
  label?: string
  entries: TreeEntry[]
}

// One link of the Administration tree as the side navigation and the find field read it.
export interface TreeLink {
  // Undefined only for the page key of an unset tab (see pageTreeKey).
  key: string | undefined
  label: string
  description: string
  keywords?: string
  plugin?: string
  to: NamedLocation
}

export interface AdminGroup {
  id: string
  heading?: string
  label?: string
  links: TreeLink[]
}

// A page or a section a server plugin contributes, as the plugin UI manifest names it.
export interface PluginPage {
  plugin: string
  id: string
  area: string
  group?: string
  title: string
  description?: string | null
  blocks?: unknown[]
}

export interface PluginSection {
  plugin?: string
  domain: string
  label: string
  group?: string
}

export interface PluginManifest {
  version?: number
  pages?: PluginPage[]
  sections?: PluginSection[]
  enabledPlugins?: string[]
}

interface Contributions {
  pages: PluginPage[]
  sections: PluginSection[]
  enabledPlugins: string[]
}

export type ContributedPage = PluginPage & { key: string; heading: string }

const MORE_KEY = 'more'

// The server plugin whose enabled state shows the nexus import wizard. The name is the plugin's id.
const NEXUS_IMPORT_PLUGIN = 'nexus-import'

/*
 * The pages and settings sections that server plugins contribute, from GET /api/frontend/plugin-ui. The
 * manifest is read after the session is known (src/store/pluginUi.js) and replaces the previous one. The
 * static PAGES list above is never changed by it.
 */
const NO_CONTRIBUTIONS: Contributions = { pages: [], sections: [], enabledPlugins: [] }
const contributions = shallowRef<Contributions>(NO_CONTRIBUTIONS)

// The groups a contributed page may name, in tree order. A page whose group is not one of these sits in Plugins.
const CONTRIBUTED_GROUPS: readonly string[] = ['Repository', 'Security', 'System', 'Support']
const PLUGINS_HEADING = 'Plugins'

// The heading each group shows on screen. The group ids stay the values a plugin manifest names (see CONTRIBUTED_GROUPS).
const GROUP_HEADINGS: Readonly<Record<string, string>> = {
  Repository: 'Artifacts',
  Security: 'Access',
  System: 'Instance',
  Support: 'Monitoring and support',
}

const setContributions = (manifest: PluginManifest | null | undefined) => {
  contributions.value = manifest
    ? { pages: manifest.pages ?? [], sections: manifest.sections ?? [], enabledPlugins: manifest.enabledPlugins ?? [] }
    : NO_CONTRIBUTIONS
}

// The permission node that opens a contributed page: the node of its admin area, or the instance admin node for `*`.
const pageNodeOf = (area: string) => (area === '*' ? INSTANCE_ADMIN_NODE : `ingot.admin.${area}`)

const contributedKey = (page: PluginPage) => `${page.plugin}/${page.id}`

// The tree key of a plugin page: `plugin:<plugin>/<id>`, so it never equals a key of a built-in entry.
const pluginTreeKey = (plugin: string, id: string) => `plugin:${plugin}/${id}`

const contributedHeading = (page: PluginPage) =>
  page.group !== undefined && CONTRIBUTED_GROUPS.includes(page.group) ? GROUP_HEADINGS[page.group] : PLUGINS_HEADING

// The contributed pages the visitor may open, each with its key and the heading of its group.
const contributedPages = (can: Can): ContributedPage[] => contributions.value.pages
  .filter(page => can(pageNodeOf(page.area)))
  .map(page => ({ ...page, key: contributedKey(page), heading: contributedHeading(page) }))

const contributedPageByRoute = (plugin: string, id: string) =>
  contributions.value.pages.find(page => page.plugin === plugin && page.id === id)

// The node a contributed page needs, for the route guard. Undefined for a page the manifest does not name.
const contributedPageNode = (plugin: string, id: string) => {
  const page = contributedPageByRoute(plugin, id)
  return page ? pageNodeOf(page.area) : undefined
}

// The rail groups of the contributed pages: [{ heading, pages }] in the order of the groups, Plugins last.
const contributedRailGroups = (can: Can) => [...Object.values(GROUP_HEADINGS), PLUGINS_HEADING]
  .map(heading => ({ heading, pages: contributedPages(can).filter(page => page.heading === heading) }))
  .filter(group => group.pages.length > 0)

// The location an address without a page id is replaced with: the first page the manifest names for the plugin.
// Null when the address already names a page, or the plugin contributes none.
const pluginPageRedirect = (plugin: string, page: string | undefined): NamedLocation | null => {
  if (page !== undefined) return null
  const first = contributions.value.pages.find(entry => entry.plugin === plugin)
  return first ? { name: 'settings-plugin', params: { plugin, page: first.id } } : null
}

const sectionOf = (domain: string) => contributions.value.sections.find(section => section.domain === domain)

const settingsLabelOf = (domain: string) => sectionOf(domain)?.label

// Whether a built-in settings domain shows the sections of its form as tabs. Only domains whose entry opts in do.
const sectionTabsOf = (domain: string): boolean => ADMIN_TREE.some(group => group.entries.some(entry => entry.domain === domain && entry.sectionTabs === true))

const enabledPlugins = () => contributions.value.enabledPlugins

// Whether a server plugin is enabled, as the manifest of the plugin UI lists it. False until a session has read it.
const pluginEnabled = (name: string) => enabledPlugins().includes(name)

// An entry with `requires` is listed only while the predicate holds, on top of its permission.
const entryAvailable = (entry: { requires?: () => boolean }) => entry.requires === undefined || entry.requires()

// The groups of the rail. The entries of the rail form one unlabelled block, so the group has no heading.
const AREAS: { area: string; heading: string | undefined }[] = [{ area: 'rail', heading: undefined }]
const BAR_SIZE = 4

// The settings domains that have a page of their own, so they are not a settings tab or a settings card. The maven domain is
// edited on the Repositories page. It is still loaded, saved, exported and imported with the other domains.
// The statistics domain is edited on Analytics, where the collection is, so it is no entry of the tree either.
const DOMAINS_ON_OTHER_PAGES: readonly string[] = ['maven', 'statistics']

// The page names under /settings that a settings domain may not take as its id, since the page is served first.
const RESERVED_SETTINGS_PAGES: readonly string[] = ['repositories']

/*
 * The Administration tree, in its groups, each in the order it is listed. A page entry names its route, label,
 * description, filter keywords and permission. A settings domain entry (`domain`) names the domain and its label,
 * description and keywords, and is listed only when the server lists the domain and the visitor has the settings
 * node. A settings domain the tree does not name is added by adminGroups, after the entries of its group. The
 * order inside a group is the order here: entries that belong together are adjacent (Audit settings follows
 * Audit log), and the pages keep their order relative to each other (see the tree requirement).
 */
const ADMIN_TREE: TreeGroup[] = [
  // Overview is the page /settings without a domain. It is listed for every visitor with the settings node, before the
  // first group, and its group has no heading: the group is named by its label instead.
  {
    id: 'overview',
    label: 'Overview',
    entries: [
      { key: 'overview', route: 'settings', label: 'Overview', permission: { anyNode: [ADMIN_NODES.settings, ADMIN_NODES.dashboard] }, description: 'Shared configuration for all instances.', keywords: 'settings, configuration, import, export, draft, unsaved' },
    ],
  },
  {
    id: 'Repository',
    entries: [
      { key: 'repositories', route: 'settings-repositories', detail: 'settings-repository', label: 'Repositories', permission: { node: ADMIN_NODES.settings }, description: 'Hosted, proxied and group repositories, their policies and Maven options.', keywords: 'maven, hosted, proxy, mirror, group' },
      { domain: 'cleanup', label: 'Cleanup policies', description: 'Rules that delete old versions from the repositories they list.', keywords: 'cleanup, retention, delete, snapshots' },
      { domain: 'javadoc', label: 'Javadoc', description: 'Whether Javadoc archives are served, and which file suffixes count as Javadoc.', keywords: 'docs, groovydoc' },
      { domain: 'search', label: 'Search index', description: 'The index behind artifact search: its nightly check against storage and scheduled rebuilds.', keywords: 'search, index, rebuild, reindex, verify', sectionTabs: true },
    ],
  },
  {
    id: 'Security',
    entries: [
      { key: 'users', route: 'access-users', label: 'Users', permission: { node: ADMIN_NODES.users }, description: 'People who sign in, with their permissions and tokens.', keywords: 'people, account, password, token' },
      { key: 'machines', route: 'access-machines', label: 'Machine accounts', permission: { node: ADMIN_NODES.users }, description: 'Accounts for CI and other automated clients, with their tokens.', keywords: 'ci, bot, robot, deploy, token' },
      { key: 'teams', route: 'access-teams', label: 'Teams', permission: { node: ADMIN_NODES.teams }, description: 'Groups of users and machine accounts that share permissions.', keywords: 'group, members' },
      { key: 'sso', route: 'access-sso', label: 'Single sign-on', permission: { node: INSTANCE_ADMIN_NODE }, description: 'External sign-in providers such as OpenID Connect.', keywords: 'sso, oidc, oauth, openid, login' },
      { key: 'ldap', route: 'access-ldap', label: 'LDAP directory', permission: { node: INSTANCE_ADMIN_NODE }, description: 'Sign-in with accounts from an LDAP directory.', keywords: 'ldap, active directory' },
      { key: 'security', route: 'access-security', label: 'Sign-in protection', permission: { node: INSTANCE_ADMIN_NODE }, description: 'Limits on repeated failed sign-ins from one IP address.', keywords: 'security settings, brute force, lockout' },
      { key: 'check', route: 'access-check', label: 'Permission check', permission: { anyNode: ACCESS_NODES }, description: 'Find out why someone can or cannot reach a path.', keywords: 'why, denied, 403, access' },
    ],
  },
  {
    id: 'System',
    entries: [
      { domain: 'frontend', label: 'Branding', description: 'Name, description, logo, colours and links shown on this instance.', keywords: 'frontend, logo, colour, color, theme, title, privacy' },
      { domain: 'web', label: 'Reverse proxy', description: 'The headers that carry the client address and the path prefix behind a proxy.', keywords: 'web, nginx, traefik, x-forwarded, header' },
      // The nexus import wizard. Listed only while the nexus-import plugin is enabled, so an installation without it sees
      // no entry and the address answers as an unknown one.
      { key: 'migration', route: 'settings-migration', label: 'Migration', permission: { node: INSTANCE_ADMIN_NODE }, requires: () => pluginEnabled(NEXUS_IMPORT_PLUGIN), description: 'Import the repositories of another instance, step by step.', keywords: 'nexus, import' },
    ],
  },
  {
    id: 'Support',
    entries: [
      { key: 'audit', route: 'audit', label: 'Audit log', permission: { node: ADMIN_NODES.audit }, description: 'Deployments, deletions, settings changes, token changes and failed sign ins on this instance, newest first.', keywords: 'audit, events, history, who' },
      { domain: 'audit', label: 'Audit settings', description: 'How long audit events are kept, and whether the client address is recorded.', keywords: 'audit, retention, client address' },
      { key: 'console', route: 'console', label: 'Console', permission: { node: ADMIN_NODES.console }, description: 'The log lines of this instance, filtered by level and text.', keywords: 'logs' },
      { key: 'maintenance', route: 'maintenance', label: 'Maintenance', permission: { node: ADMIN_NODES.maintenance }, description: 'Storage usage, the search index and cleanup for this Ingot instance.', keywords: 'storage, rebuild index, cleanup run' },
      { key: 'analytics', route: 'analytics', label: 'Analytics', permission: { anyNode: [ADMIN_NODES.settings, ADMIN_NODES.dashboard] }, description: 'What this instance collects about its usage, and the metrics it exposes.', keywords: 'usage statistics, stats, traffic, metrics, prometheus, collection, charts' },
      { key: 'system-info', route: 'system-info', label: 'System and support', permission: { node: ADMIN_NODES.console }, requires: () => supportBundleOffered.value, description: 'What this instance runs on and what a maintainer needs to look at. Secrets are withheld or masked, and the page only reads.', keywords: 'version, java, environment, zip, debug' },
    ],
  },
]

// The page entries of the tree in their order, the ones that have a route. The rail's Administration entry opens the
// first of them the visitor may open (administrationLocation), so this order is the one the requirement fixes.
const ADMIN_ENTRIES: PageEntry[] = ADMIN_TREE
  .flatMap(group => group.entries)
  .filter((entry): entry is PageEntry => entry.route !== undefined)

// The node names a permission lists: its `node`, then its `anyNode` alternatives. The store is still JavaScript, so its
// list is typed here, at the boundary.
const requiredNodesOf = (permission: Permission): string[] => requiredNodes(permission)

// Whether the instance offers the support pages (GET /api/admin/support/availability). False until the server has
// answered, so a support page is listed only once the instance says it is on (see src/store/supportBundle.js).
const supportBundleOffered = shallowRef(false)
const setSupportBundleOffered = (offered: unknown) => {
  supportBundleOffered.value = offered === true
}

// The Administration entry is shown to a visitor who may open the hub or any page of the tree.
const ADMINISTRATION_REACH: Permission = {
  anyNode: [ADMIN_NODES.settings, ...ADMIN_ENTRIES.flatMap(entry => requiredNodesOf(entry.permission))],
}

// The names that are set, for the route name lists below. A route without a detail or a redirect has none.
const definedNames = (names: (string | undefined)[]): string[] => names.filter((name): name is string => Boolean(name))

const PAGES: Page[] = [
  { key: 'overview', route: 'overview', label: 'Browse', area: 'rail', permission: { manager: false }, barOrder: 1 },
  { key: 'search', route: 'search', label: 'Search', area: 'rail', permission: { manager: false }, barOrder: 2 },
  {
    key: 'settings',
    route: 'settings',
    label: 'Administration',
    shortLabel: 'Admin',
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
  { key: 'analytics', route: 'analytics', label: 'Analytics', area: 'none', permission: { anyNode: [ADMIN_NODES.settings, ADMIN_NODES.dashboard] } },
  { key: 'audit', route: 'audit', label: 'Audit log', area: 'none', permission: { node: ADMIN_NODES.audit } },
  { key: 'console', route: 'console', label: 'Console', area: 'none', permission: { node: ADMIN_NODES.console } },
  { key: 'maintenance', route: 'maintenance', label: 'Maintenance', area: 'none', permission: { node: ADMIN_NODES.maintenance } },
  // The nexus import wizard lives in the System group of the Administration tree (see ADMIN_TREE). This entry only gives its
  // route a page, so the route has a section and a title; it is never listed in the rail.
  { key: 'migration', route: 'settings-migration', label: 'Migration', area: 'none', permission: { node: INSTANCE_ADMIN_NODE }, requires: () => pluginEnabled(NEXUS_IMPORT_PLUGIN) },
  { key: 'account', route: 'account', label: 'My account', area: 'none', permission: { signedIn: true } },
]

// Routes that have no page of their own: the browser of a repository path (the overview page), the
// legacy token redirects, the Settings entry below a domain, the authentication settings redirect and
// the not found page, and the old support address, which redirects to the system information page. The detail routes
// of Access are not listed here: each one is the `detail` of its child.
const EXEMPT_ROUTES: readonly string[] = ['browse', 'dashboard', 'support', 'tokens', 'token-new', 'token', 'settings-authentication', 'settings-login', 'settings-statistics', 'settings-entry', 'settings-plugin', 'settings-maven-repositories', 'not-found']

const pageByKey = (key: string): Page | undefined => PAGES.find(page => page.key === key)

const childrenOf = (key: string): ChildPage[] => pageByKey(key)?.children ?? []

const allowedBy = (permission: Permission, can: Can) => requiredNodesOf(permission).some(node => can(node))

const isOpen = (permission: Permission) => requiredNodesOf(permission).length === 0

const visiblePages = (can: Can) =>
  PAGES.filter(page => page.area !== 'none' && (isOpen(page.permission) || allowedBy(page.reach ?? page.permission, can)))

const visibleChildren = (key: string, can: Can) => childrenOf(key).filter(child => allowedBy(child.permission, can))

/*
 * The links of the plugin pages a contributed page puts under one heading of the tree, in the order the manifest lists them.
 * A plugin page is listed after the built-in entries of its group.
 */
const pluginLinksOf = (can: Can, heading: string): TreeLink[] => contributedPages(can)
  .filter(page => page.heading === heading)
  .map(page => ({
    key: pluginTreeKey(page.plugin, page.id),
    label: page.title,
    description: page.description ?? '',
    plugin: page.plugin,
    to: { name: 'settings-plugin', params: { plugin: page.plugin, page: page.id } },
  }))

/*
 * The key of an entry of the tree. A static page and a settings domain may share an id (the audit log page and the audit
 * settings domain do), so each kind has its own prefix: no two entries of the tree have the same key, whatever a plugin adds.
 * A key is internal: it is never a route name, an address or a label.
 */
const pageTreeKey = (key: string | undefined): string | undefined => (key === undefined ? undefined : `page:${key}`)
const settingsTreeKey = (domain: string): string => `settings:${domain}`
const OVERVIEW_TREE_KEY = pageTreeKey('overview')

// The key of the entry the page on screen is, from its route name and, on a Settings page, its domain. Undefined when the
// page is no entry of the tree, such as the settings hub.
const currentTreeKey = (routeName: RouteRecordName | null | undefined, domain?: string): string | undefined => {
  if (domain !== undefined) return settingsTreeKey(domain)
  const entry = ADMIN_ENTRIES.find(item => item.route === routeName || item.detail === routeName)
  return entry ? pageTreeKey(entry.key) : undefined
}

// The group of a settings domain in the Administration tree. A plugin section takes the group it names (Repository or
// System). A built-in domain takes the group of its entry in ADMIN_TREE. Any other domain, a plugin domain included,
// is listed under System, so it never fails to show.
const treeGroupOf = (domain: string): string => {
  const section = sectionOf(domain)
  if (section) return section.group === 'Repository' ? 'Repository' : 'System'
  return ADMIN_TREE.find(group => group.entries.some(entry => entry.domain === domain))?.id ?? 'System'
}

// The label of a settings domain: the frontend's label for a built-in domain, else the caller's title (the plugin section
// label, or the schema title).
const settingsLinkOf = (
  domain: string,
  entry: DomainEntry | undefined,
  { titleOf, descriptionOf }: DomainTitles,
): TreeLink => ({
  key: settingsTreeKey(domain),
  label: entry?.label ?? titleOf(domain),
  description: entry?.description ?? descriptionOf(domain) ?? '',
  keywords: entry?.keywords,
  to: { name: 'settings', params: { domain } },
})

interface DomainTitles {
  titleOf: (domain: string) => string
  descriptionOf: (domain: string) => string | undefined
}

/*
 * The Administration tree a visitor may open, in its groups: [{ id, heading, links: [{ key, label, description, to }] }].
 * `to` is the router location of the link. A group without a visible link is left out. The settings domains come from
 * the caller (`domains` is the list the server gave, Access domains left out), and are listed only to a visitor with the
 * settings node. `titleOf` and `descriptionOf` give the label and the description of a domain the frontend does not name.
 */
const adminGroups = (
  can: Can,
  { domains: allDomains = [], titleOf = (domain: string) => domain, descriptionOf = () => '' }: Partial<DomainTitles> & { domains?: string[] } = {},
): AdminGroup[] => {
  const domains = allDomains.filter(domain => !DOMAINS_ON_OTHER_PAGES.includes(domain))
  const showsDomains = can(ADMIN_NODES.settings)
  const flattened = ADMIN_TREE.flatMap(group => group.entries.map(entry => ({ ...entry, group: group.id })))
  // The entries the visitor may open, in the order of the tree, each with the group it is listed under.
  const listed = flattened
    .filter(entry => (entry.domain === undefined
      ? entryAvailable(entry) && allowedBy(entry.permission, can)
      : showsDomains && domains.includes(entry.domain)))
    .map(entry => (entry.domain === undefined
      ? { group: entry.group, link: { key: pageTreeKey(entry.key), label: entry.label, description: entry.description, keywords: entry.keywords, to: { name: entry.route } } }
      : { group: treeGroupOf(entry.domain), link: settingsLinkOf(entry.domain, entry, { titleOf, descriptionOf }) }))
  // The domains the tree does not name come after the named ones, in the order the server gave.
  const unnamed = showsDomains
    ? domains
      .filter(domain => !flattened.some(entry => entry.domain === domain))
      .map(domain => ({ group: treeGroupOf(domain), link: settingsLinkOf(domain, undefined, { titleOf, descriptionOf }) }))
    : []
  const placed = [...listed, ...unnamed]
  const groups: AdminGroup[] = ADMIN_TREE.map(group => ({
    id: group.id,
    heading: GROUP_HEADINGS[group.id],
    label: group.label,
    links: [
      ...placed.filter(item => item.group === group.id).map(item => item.link),
      ...pluginLinksOf(can, GROUP_HEADINGS[group.id]),
    ],
  }))
  return [...groups, { id: PLUGINS_HEADING, heading: PLUGINS_HEADING, links: pluginLinksOf(can, PLUGINS_HEADING) }]
    .filter(group => group.links.length > 0)
}

// The shortcuts between the pages of the tree (design D6). A shortcut is a link inside a page, never a tree entry, and
// it is listed only when the visitor may open its target. The key is the tree key of the page the shortcut is drawn on.
const settingsTo = (domain: string): NamedLocation => ({ name: 'settings', params: { domain } })
interface Shortcut {
  label: string
  to: NamedLocation
  node: string
}
const SHORTCUTS: Record<string, Shortcut[]> = {
  'page:audit': [{ label: 'Audit settings', to: settingsTo('audit'), node: ADMIN_NODES.settings }],
  'settings:audit': [{ label: 'Open the audit log', to: { name: 'audit' }, node: ADMIN_NODES.audit }],
}

// The rebuild of the search index runs on the Index tab of Maintenance. The Search index page links there from its
// content, beside an explanation of what a rebuild does, so the link is not a header shortcut (see SettingsView).
export const indexRebuildLinkOf = (can: Can): NamedLocation | undefined => (can(ADMIN_NODES.maintenance)
  ? { name: 'maintenance', query: { tab: 'index' } }
  : undefined)

const shortcutsOf = (treeKey: string, can: Can): { label: string; to: NamedLocation }[] => (SHORTCUTS[treeKey] ?? [])
  .filter(shortcut => can(shortcut.node))
  .map(({ label, to }) => ({ label, to }))

/*
 * Where the Administration entry of the rail opens: the hub when the visitor may open it, otherwise the first
 * Administration page the visitor may open (for example the audit log of a visitor with only that node).
 */
const administrationLocation = (can: Can): NamedLocation => {
  // Overview is the first entry of the tree and holds the status for the dashboard node, so both nodes open it.
  if (can(ADMIN_NODES.settings) || can(ADMIN_NODES.dashboard)) return { name: 'settings' }
  const first = ADMIN_ENTRIES.find(entry => entryAvailable(entry) && allowedBy(entry.permission, can))
  return first ? { name: first.route } : { name: 'settings' }
}

// What SideNavLayout needs of a page except the groups: the accessible name of the navigation, the first
// crumb and the location of the index. The navigation is the Administration tree on every page of it.
// The first crumb of an Administration page is the section, and the page is the last crumb, so a page never repeats itself.
const sideNavOf = (key: string) => {
  const page = pageByKey(key)!
  return { label: 'Administration', sectionLabel: 'Administration', indexTo: { name: page.route } }
}

// The settings domain ids that a reserved page name under /settings would shadow.
const reservedDomainIds = (domains: string[]) => domains.filter(domain => RESERVED_SETTINGS_PAGES.includes(domain))

const firstAccessRoute = (can: Can): string => {
  const landing = visibleChildren('access', can).find(child => child.landing)
    ?? childrenOf('access').find(child => child.landing)
  return landing!.route
}

const accessTabOf = (routeName: string): string | undefined =>
  childrenOf('access').find(child => child.route === routeName || child.detail === routeName)?.key

interface PageGroup<T> {
  area: string
  heading: string | undefined
  pages: T[]
}

const groupPages = <T extends { area: string }>(pages: T[]): PageGroup<T>[] => AREAS
  .map(({ area, heading }) => ({ area, heading, pages: pages.filter(page => page.area === area) }))
  .filter(group => group.pages.length > 0)

const railGroups = (can: Can) => groupPages(visiblePages(can))

const barLayout = (can: Can): { bar: Page[]; more: PageGroup<Page>[] } => {
  const ordered = visiblePages(can)
    .filter(page => page.barOrder !== undefined)
    .sort((a, b) => a.barOrder! - b.barOrder!)
  return { bar: ordered.slice(0, BAR_SIZE), more: groupPages(ordered.slice(BAR_SIZE)) }
}

const pageLabel = (key: string): string => pageByKey(key)?.label ?? key


// The meta of a route. `manager` stays on every route that names a node or an alternative, because
// it is what a session without a node list is judged by, and what tells a visitor to sign in.
const metaOf = (section: string, permission: Permission): RouteMeta => {
  if (permission.signedIn) return { section, signedIn: true }
  if (isOpen(permission)) return { section }
  return permission.node
    ? { section, manager: true, node: permission.node }
    : { section, manager: true, anyNode: permission.anyNode }
}

// The meta of a route of an Administration tree entry (see ADMIN_TREE), named by its route.
const adminRouteMeta = (routeName: string): RouteMeta => {
  const entry = ADMIN_ENTRIES.find(item => item.route === routeName)!
  return metaOf('settings', entry.permission)
}

const routeMeta = (key: string, childKey?: string): RouteMeta => {
  const page = pageByKey(key)!
  const permission = childKey ? childrenOf(key).find(child => child.key === childKey)!.permission : page.permission
  return metaOf(page.key, permission)
}

const unlistedRoutes = (names: string[]): string[] => {
  const covered = new Set<string>([
    ...PAGES.map(page => page.route),
    ...PAGES.flatMap(page => childrenOf(page.key)).flatMap(child => definedNames([child.route, child.detail])),
    ...ADMIN_ENTRIES.flatMap(entry => definedNames([entry.route, entry.detail])),
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
  sectionTabsOf,
  enabledPlugins,
  pluginEnabled,
  NEXUS_IMPORT_PLUGIN,
  pageByKey,
  visiblePages,
  visibleChildren,
  adminGroups,
  shortcutsOf,
  GROUP_HEADINGS,
  DOMAINS_ON_OTHER_PAGES,
  currentTreeKey,
  pageTreeKey,
  OVERVIEW_TREE_KEY,
  pluginTreeKey,
  administrationLocation,
  sideNavOf,
  reservedDomainIds,
  firstAccessRoute,
  accessTabOf,
  pageLabel,
  railGroups,
  barLayout,
  routeMeta,
  adminRouteMeta,
  supportBundleOffered,
  setSupportBundleOffered,
  unlistedRoutes,
}
