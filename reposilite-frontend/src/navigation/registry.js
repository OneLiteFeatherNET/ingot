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
 * lists those pages in four groups. A group has an id, the value a plugin manifest names (Repository, Security,
 * System, Support), and a display heading (GROUP_HEADINGS: Artifacts, Access, Instance, Monitoring and support).
 * Every Administration page shows this one tree in its side navigation (adminGroups), with the current page marked.
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
 *                                [{ id, heading, links: [{ key, label, description, to }] }]; settings domains are passed in
 * - shortcutsOf(key, can)        the links a page draws to its related pages: [{ label, to }], each one the visitor may open
 * - currentTreeKey(route, domain) the key of the tree entry on screen (`page:<key>` or `settings:<domain>`), or undefined
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

// The server plugin whose enabled state shows the nexus import wizard. The name is the plugin's id.
const NEXUS_IMPORT_PLUGIN = 'nexus-import'

/*
 * The pages and settings sections that server plugins contribute, from GET /api/frontend/plugin-ui. The
 * manifest is read after the session is known (src/store/pluginUi.js) and replaces the previous one. The
 * static PAGES list above is never changed by it.
 */
const NO_CONTRIBUTIONS = { pages: [], sections: [], enabledPlugins: [] }
const contributions = shallowRef(NO_CONTRIBUTIONS)

// The groups a contributed page may name, in tree order. A page whose group is not one of these sits in Plugins.
const CONTRIBUTED_GROUPS = ['Repository', 'Security', 'System', 'Support']
const PLUGINS_HEADING = 'Plugins'

// The heading each group shows on screen. The group ids stay the values a plugin manifest names (see CONTRIBUTED_GROUPS).
const GROUP_HEADINGS = {
  Repository: 'Artifacts',
  Security: 'Access',
  System: 'Instance',
  Support: 'Monitoring and support',
}

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

// The tree key of a plugin page: `plugin:<plugin>/<id>`, so it never equals a key of a built-in entry.
const pluginTreeKey = (plugin, id) => `plugin:${plugin}/${id}`

const contributedHeading = (page) => (CONTRIBUTED_GROUPS.includes(page.group) ? GROUP_HEADINGS[page.group] : PLUGINS_HEADING)

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

// The rail groups of the contributed pages: [{ heading, pages }] in the order of the groups, Plugins last.
const contributedRailGroups = (can) => [...Object.values(GROUP_HEADINGS), PLUGINS_HEADING]
  .map(heading => ({ heading, pages: contributedPages(can).filter(page => page.heading === heading) }))
  .filter(group => group.pages.length > 0)

// The location an address without a page id is replaced with: the first page the manifest names for the plugin.
// Null when the address already names a page, or the plugin contributes none.
const pluginPageRedirect = (plugin, page) => {
  if (page !== undefined) return null
  const first = contributions.value.pages.find(entry => entry.plugin === plugin)
  return first ? { name: 'settings-plugin', params: { plugin, page: first.id } } : null
}

const sectionOf = (domain) => contributions.value.sections.find(section => section.domain === domain)

const settingsLabelOf = (domain) => sectionOf(domain)?.label

const enabledPlugins = () => contributions.value.enabledPlugins

// Whether a server plugin is enabled, as the manifest of the plugin UI lists it. False until a session has read it.
const pluginEnabled = (name) => enabledPlugins().includes(name)

// An entry with `requires` is listed only while the predicate holds, on top of its permission.
const entryAvailable = (entry) => entry.requires === undefined || entry.requires()

// The groups of the rail. The four entries of the rail form one unlabelled block, so the group has no heading.
const AREAS = [{ area: 'rail', heading: undefined }]
const BAR_SIZE = 4

// The settings domains that have a page of their own, so they are not a settings tab or a settings card. The maven domain is
// edited on the Repositories page. It is still loaded, saved, exported and imported with the other domains.
const DOMAINS_ON_OTHER_PAGES = ['maven']

// The page names under /settings that a settings domain may not take as its id, since the page is served first.
const RESERVED_SETTINGS_PAGES = ['repositories']

/*
 * The Administration tree, in its groups, each in the order it is listed. A page entry names its route, label,
 * description, filter keywords and permission. A settings domain entry (`domain`) names the domain and its label,
 * description and keywords, and is listed only when the server lists the domain and the visitor has the settings
 * node. A settings domain the tree does not name is added by adminGroups, after the entries of its group. The
 * order inside a group is the order here: entries that belong together are adjacent (Audit settings follows
 * Audit log), and the pages keep their order relative to each other (see the tree requirement).
 */
const ADMIN_TREE = [
  {
    id: 'Repository',
    entries: [
      { key: 'repositories', route: 'settings-repositories', detail: 'settings-repository', label: 'Repositories', permission: { node: ADMIN_NODES.settings }, description: 'Hosted, proxied and group repositories, their policies and Maven options.', keywords: 'maven, hosted, proxy, mirror, group' },
      { domain: 'cleanup', label: 'Cleanup policies', description: 'Rules that delete old versions from the repositories they list.', keywords: 'cleanup, retention, delete, snapshots' },
      { domain: 'javadoc', label: 'Javadoc', description: 'Whether Javadoc archives are served, and which file suffixes count as Javadoc.', keywords: 'docs, groovydoc' },
      { domain: 'search', label: 'Search index', description: 'The index behind artifact search: its nightly check against storage and scheduled rebuilds.', keywords: 'search, index, rebuild, reindex, verify' },
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
      { domain: 'statistics', label: 'Usage statistics', description: 'Whether resolved requests are counted, and per which period.', keywords: 'statistics, stats, downloads' },
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
      { key: 'analytics', route: 'analytics', label: 'Analytics', permission: { node: ADMIN_NODES.dashboard }, description: 'Traffic and resource use of this instance over time.', keywords: 'traffic, charts' },
      { key: 'system-info', route: 'system-info', label: 'System information', permission: { node: ADMIN_NODES.console }, requires: () => supportBundleOffered.value, description: 'What this instance runs on. Secrets are withheld or masked, and the page only reads.', keywords: 'version, java, environment' },
      { key: 'support', route: 'support', label: 'Support bundle', permission: { node: ADMIN_NODES.console }, requires: () => supportBundleOffered.value, description: 'What a maintainer needs to look at this instance, as one zip.', keywords: 'zip, debug' },
    ],
  },
]

// The page entries of the tree in their order, the ones that have a route. The rail's Administration entry opens the
// first of them the visitor may open (administrationLocation), so this order is the one the requirement fixes.
const ADMIN_ENTRIES = ADMIN_TREE.flatMap(group => group.entries).filter(entry => entry.route !== undefined)

// Whether the instance offers the support pages (GET /api/admin/support/availability). False until the server has
// answered, so a support page is listed only once the instance says it is on (see src/store/supportBundle.js).
const supportBundleOffered = shallowRef(false)
const setSupportBundleOffered = (offered) => {
  supportBundleOffered.value = offered === true
}

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
  // The nexus import wizard lives in the System group of the Administration tree (see ADMIN_TREE). This entry only gives its
  // route a page, so the route has a section and a title; it is never listed in the rail.
  { key: 'migration', route: 'settings-migration', label: 'Migration', area: 'none', permission: { node: INSTANCE_ADMIN_NODE }, requires: () => pluginEnabled(NEXUS_IMPORT_PLUGIN) },
  { key: 'account', route: 'account', label: 'My account', area: 'none', permission: { signedIn: true } },
]

// Routes that have no page of their own: the browser of a repository path (the overview page), the
// legacy token redirects, the Settings entry below a domain, the authentication settings redirect and
// the not found page. The detail routes of Access are not listed here: each one is the `detail` of its child.
const EXEMPT_ROUTES = ['browse', 'tokens', 'token-new', 'token', 'settings-authentication', 'settings-login', 'settings-entry', 'settings-plugin', 'settings-maven-repositories', 'not-found']

const pageByKey = (key) => PAGES.find(page => page.key === key)

const childrenOf = (key) => pageByKey(key)?.children ?? []

const allowedBy = (permission, can) => requiredNodes(permission).some(node => can(node))

const isOpen = (permission) => requiredNodes(permission).length === 0

const visiblePages = (can) =>
  PAGES.filter(page => page.area !== 'none' && (isOpen(page.permission) || allowedBy(page.reach ?? page.permission, can)))

const visibleChildren = (key, can) => childrenOf(key).filter(child => allowedBy(child.permission, can))

/*
 * The links of the plugin pages a contributed page puts under one heading of the tree, in the order the manifest lists them.
 * A plugin page is listed after the built-in entries of its group.
 */
const pluginLinksOf = (can, heading) => contributedPages(can)
  .filter(page => page.heading === heading)
  .map(page => ({
    key: pluginTreeKey(page.plugin, page.id),
    label: page.title,
    description: page.description ?? '',
    to: { name: 'settings-plugin', params: { plugin: page.plugin, page: page.id } },
  }))

/*
 * The key of an entry of the tree. A static page and a settings domain may share an id (the audit log page and the audit
 * settings domain do), so each kind has its own prefix: no two entries of the tree have the same key, whatever a plugin adds.
 * A key is internal: it is never a route name, an address or a label.
 */
const pageTreeKey = (key) => (key === undefined ? undefined : `page:${key}`)
const settingsTreeKey = (domain) => (domain === undefined ? undefined : `settings:${domain}`)

// The key of the entry the page on screen is, from its route name and, on a Settings page, its domain. Undefined when the
// page is no entry of the tree, such as the settings hub.
const currentTreeKey = (routeName, domain) => {
  if (domain !== undefined) return settingsTreeKey(domain)
  const entry = ADMIN_ENTRIES.find(item => item.route === routeName || item.detail === routeName)
  return entry ? pageTreeKey(entry.key) : undefined
}

// The group of a settings domain in the Administration tree. A plugin section takes the group it names (Repository or
// System). A built-in domain takes the group of its entry in ADMIN_TREE. Any other domain, a plugin domain included,
// is listed under System, so it never fails to show.
const treeGroupOf = (domain) => {
  const section = sectionOf(domain)
  if (section) return section.group === 'Repository' ? 'Repository' : 'System'
  return ADMIN_TREE.find(group => group.entries.some(entry => entry.domain === domain))?.id ?? 'System'
}

// The label of a settings domain: the frontend's label for a built-in domain, else the caller's title (the plugin section
// label, or the schema title).
const settingsLinkOf = (domain, entry, { titleOf, descriptionOf }) => ({
  key: settingsTreeKey(domain),
  label: entry?.label ?? titleOf(domain),
  description: entry?.description ?? descriptionOf(domain) ?? '',
  to: { name: 'settings', params: { domain } },
})

/*
 * The Administration tree a visitor may open, in its groups: [{ id, heading, links: [{ key, label, description, to }] }].
 * `to` is the router location of the link. A group without a visible link is left out. The settings domains come from
 * the caller (`domains` is the list the server gave, Access domains left out), and are listed only to a visitor with the
 * settings node. `titleOf` and `descriptionOf` give the label and the description of a domain the frontend does not name.
 */
const adminGroups = (can, { domains: allDomains = [], titleOf = (domain) => domain, descriptionOf = () => '' } = {}) => {
  const domains = allDomains.filter(domain => !DOMAINS_ON_OTHER_PAGES.includes(domain))
  const showsDomains = can(ADMIN_NODES.settings)
  const flattened = ADMIN_TREE.flatMap(group => group.entries.map(entry => ({ ...entry, group: group.id })))
  // The entries the visitor may open, in the order of the tree, each with the group it is listed under.
  const listed = flattened
    .filter(entry => (entry.domain === undefined
      ? entryAvailable(entry) && allowedBy(entry.permission, can)
      : showsDomains && domains.includes(entry.domain)))
    .map(entry => (entry.domain === undefined
      ? { group: entry.group, link: { key: pageTreeKey(entry.key), label: entry.label, description: entry.description, to: { name: entry.route } } }
      : { group: treeGroupOf(entry.domain), link: settingsLinkOf(entry.domain, entry, { titleOf, descriptionOf }) }))
  // The domains the tree does not name come after the named ones, in the order the server gave.
  const unnamed = showsDomains
    ? domains
      .filter(domain => !flattened.some(entry => entry.domain === domain))
      .map(domain => ({ group: treeGroupOf(domain), link: settingsLinkOf(domain, undefined, { titleOf, descriptionOf }) }))
    : []
  const placed = [...listed, ...unnamed]
  return ADMIN_TREE
    .map(group => ({
      id: group.id,
      heading: GROUP_HEADINGS[group.id],
      links: [
        ...placed.filter(item => item.group === group.id).map(item => item.link),
        ...pluginLinksOf(can, GROUP_HEADINGS[group.id]),
      ],
    }))
    .concat([{ id: PLUGINS_HEADING, heading: PLUGINS_HEADING, links: pluginLinksOf(can, PLUGINS_HEADING) }])
    .filter(group => group.links.length > 0)
}

// The shortcuts between the pages of the tree (design D6). A shortcut is a link inside a page, never a tree entry, and
// it is listed only when the visitor may open its target. The key is the tree key of the page the shortcut is drawn on.
const settingsTo = (domain) => ({ name: 'settings', params: { domain } })
const SHORTCUTS = {
  'page:audit': [{ label: 'Audit settings', to: settingsTo('audit'), node: ADMIN_NODES.settings }],
  'settings:audit': [{ label: 'Open the audit log', to: { name: 'audit' }, node: ADMIN_NODES.audit }],
  'page:analytics': [{ label: 'Usage statistics', to: settingsTo('statistics'), node: ADMIN_NODES.settings }],
  'page:maintenance': [
    { label: 'Search index', to: settingsTo('search'), node: ADMIN_NODES.settings },
    { label: 'Cleanup policies', to: settingsTo('cleanup'), node: ADMIN_NODES.settings },
  ],
  'settings:search': [{ label: 'Rebuild now on Maintenance', to: { name: 'maintenance' }, node: ADMIN_NODES.maintenance }],
}

const shortcutsOf = (treeKey, can) => (SHORTCUTS[treeKey] ?? [])
  .filter(shortcut => can(shortcut.node))
  .map(({ label, to }) => ({ label, to }))

/*
 * Where the Administration entry of the rail opens: the hub when the visitor may open it, otherwise the first
 * Administration page the visitor may open (for example the audit log of a visitor with only that node).
 */
const administrationLocation = (can) => {
  if (can(ADMIN_NODES.settings)) return { name: 'settings' }
  const first = ADMIN_ENTRIES.find(entry => entryAvailable(entry) && allowedBy(entry.permission, can))
  return first ? { name: first.route } : { name: 'settings' }
}

// What SideNavLayout needs of a page except the groups: the accessible name of the navigation, the first
// crumb and the location of the index. The navigation is the Administration tree on every page of it.
// The first crumb of an Administration page is the section, and the page is the last crumb, so a page never repeats itself.
const sideNavOf = (key) => {
  const page = pageByKey(key)
  return { label: 'Administration', sectionLabel: 'Administration', indexTo: { name: page.route } }
}

const SETTINGS_GROUPS = [
  { heading: 'Repository', domains: ['javadoc', 'cleanup'] },
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

// The meta of a route of an Administration tree entry (see ADMIN_TREE), named by its route.
const adminRouteMeta = (routeName) => {
  const entry = ADMIN_ENTRIES.find(item => item.route === routeName)
  return metaOf('settings', entry.permission)
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
  pluginTreeKey,
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
  adminRouteMeta,
  supportBundleOffered,
  setSupportBundleOffered,
  unlistedRoutes,
}
