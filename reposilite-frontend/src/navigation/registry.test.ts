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

import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import {
  PAGES,
  visiblePages,
  visibleChildren,
  firstAccessRoute,
  accessTabOf,
  adminGroups,
  administrationLocation,
  pageLabel,
  routeMeta,
  setContributions,
  setSupportBundleOffered,
  shortcutsOf,
  indexRebuildLinkOf,
  setReaderPages,
  readerPages,
  readerPageByRoute,
  readerPageRedirect,
  readerRailGroups,
  barLayout,
  unlistedRoutes
} from './registry'
import { ROUTES } from '../router/routes'
import { ACCESS_NODES, ADMIN_NODES, canUse } from '../store/nodeAccess'

// A session answers for a node from its node list, as the session store does.
const canWith = (nodes: string[]) => (node: string) => canUse(nodes, node)
const keysOf = (items: { key: string | undefined }[]) => items.map(item => item.key)


describe('registry entries', () => {
  it('gives every entry a key, a route name and a label', () => {
    const incomplete = PAGES.filter(page => !page.key || !page.route || !page.label).map(page => page.key)

    expect(incomplete, 'entries without a key, a route name or a label').toEqual([])
  })

  it('places every entry in a known area', () => {
    const areas = ['rail', 'none']
    const unknown = PAGES.filter(page => !areas.includes(page.area)).map(page => page.key)

    expect(unknown, 'entries whose area is neither the rail nor none').toEqual([])
  })

  it('gives every entry a permission', () => {
    const withoutPermission = PAGES.filter(page => !page.permission).map(page => page.key)

    expect(withoutPermission, 'entries without a permission').toEqual([])
  })

  it('names a route of the route table for every entry', () => {
    const routeNames = new Set(ROUTES.map(route => route.name))
    const missing = PAGES.filter(page => !routeNames.has(page.route)).map(page => page.route)

    expect(missing, 'entries that name a route the route table does not have').toEqual([])
  })

  it('keeps the account page out of the navigation', () => {
    expect(keysOf(visiblePages(() => true))).not.toContain('account')
  })

  it('names the node of every admin route in the route table', () => {
    const nodes = Object.fromEntries(
      ROUTES.filter(route => route.meta?.node).map(route => [route.name, route.meta!.node])
    )

    expect(nodes).toMatchObject({
      console: 'ingot.admin.console',
      maintenance: 'ingot.admin.maintenance',
      audit: 'ingot.admin.audit',
      'access-users': 'ingot.admin.users',
      'access-machines': 'ingot.admin.users',
      'access-teams': 'ingot.admin.teams',
      tokens: 'ingot.admin.users',
      token: 'ingot.admin.users'
    })
  })

  it('lets the settings route open for either the settings node or the dashboard node, the Overview of both', () => {
    const settings = ROUTES.find(route => route.name === 'settings')!

    expect(settings.meta?.anyNode, 'Overview is open to both nodes').toEqual(['ingot.admin.settings', 'ingot.admin.dashboard'])
  })

  it('keeps the manager flag on every route that names a node, for legacy sessions', () => {
    const missing = ROUTES.filter(route => (route.meta?.node || route.meta?.anyNode) && !route.meta.manager)

    expect(missing.map(route => route.name)).toEqual([])
  })

  it('guards the maintenance route with the manager permission', () => {
    const route = ROUTES.find(entry => entry.name === 'maintenance')

    expect(route?.path).toBe('/maintenance')
    expect(route?.meta).toEqual({ section: 'maintenance', manager: true, node: 'ingot.admin.maintenance' })
  })
})

// The Administration entry of the rail opens the same page for every node list as before the regrouping (task 3.5).
// The table was computed against the tree on main; the rows are the guard, so a regrouping that changes one of them fails.
describe('administrationLocation for fixed node lists', () => {
  beforeEach(() => setContributions(null))

  it.each([
    ['an instance admin, ingot.admin.*', ['ingot.admin.*'], { name: 'settings' }],
    ['the settings node alone', ['ingot.admin.settings'], { name: 'settings' }],
    ['no node at all', [], { name: 'settings' }],
    ['the users node alone', ['ingot.admin.users'], { name: 'access-users' }],
    ['the teams node alone', ['ingot.admin.teams'], { name: 'access-teams' }],
    ['the tokens node alone, which opens the permission check', ['ingot.admin.tokens'], { name: 'access-check' }],
    ['the audit node alone', ['ingot.admin.audit'], { name: 'audit' }],
    ['the console node alone', ['ingot.admin.console'], { name: 'console' }],
    ['the maintenance node alone', ['ingot.admin.maintenance'], { name: 'maintenance' }],
    ['the dashboard node alone, which opens the status on Overview', ['ingot.admin.dashboard'], { name: 'settings' }],
    ['the console and dashboard nodes, which open Overview first', ['ingot.admin.console', 'ingot.admin.dashboard'], { name: 'settings' }],
    ['the audit and console nodes, which open audit log', ['ingot.admin.audit', 'ingot.admin.console'], { name: 'audit' }],
    ['the maintenance and dashboard nodes, which open Overview first', ['ingot.admin.maintenance', 'ingot.admin.dashboard'], { name: 'settings' }],
    ['the users and console nodes, which open the users page', ['ingot.admin.users', 'ingot.admin.console'], { name: 'access-users' }],
  ])('opens the page the same as before for %s', (_description, nodes, expected) => {
    expect(administrationLocation(canUse.bind(null, nodes)), `the location for ${nodes.join(', ') || 'no node'}`).toEqual(expected)
  })

  it('opens Overview, where the status is, for a visitor who holds the dashboard node alone', () => {
    expect(administrationLocation(canUse.bind(null, ['ingot.admin.dashboard'])), 'Overview is the first entry of the tree').toEqual({ name: 'settings' })
  })
})

describe('the Overview entry of the tree', () => {
  const keysFor = (nodes: string[]) => adminGroups(canUse.bind(null, nodes))
    .flatMap(group => group.links)
    .map(link => link.key)

  it('is listed for a visitor who holds the dashboard node alone', () => {
    expect(keysFor(['ingot.admin.dashboard']), 'the status is shown on Overview').toContain('page:overview')
  })

  it('is listed for a visitor who holds the settings node alone', () => {
    expect(keysFor(['ingot.admin.settings']), 'the shared configuration is shown on Overview').toContain('page:overview')
  })

  it('is not listed for a visitor who holds neither node', () => {
    expect(keysFor(['ingot.admin.console']), 'no node, no Overview').not.toContain('page:overview')
  })
})

describe('visibleChildren of access', () => {
  it('shows the users, machine accounts, teams and check tabs to a visitor holding the users and teams nodes', () => {
    const tabs = visibleChildren('access', canWith(['ingot.admin.users', 'ingot.admin.teams']))

    expect(keysOf(tabs)).toEqual(['users', 'machines', 'teams', 'check'])
  })

  it('hides the machine accounts tab from a visitor who holds only the tokens node', () => {
    const tabs = keysOf(visibleChildren('access', canWith(['ingot.admin.tokens'])))

    expect(tabs, 'machine accounts are listed with the users node, not the tokens node').not.toContain('machines')
    expect(tabs).toEqual(['check'])
  })

  it('offers every access tab to an instance administrator, in the tab order', () => {
    expect(keysOf(visibleChildren('access', canWith(['*'])))).toEqual([
      'users', 'machines', 'teams', 'sso', 'ldap', 'security', 'check'
    ])
  })

  it('opens the users page when the visitor may use every access list', () => {
    expect(firstAccessRoute(canWith(['*']))).toBe('access-users')
  })

  it('opens the teams page for a visitor who may use only teams', () => {
    expect(firstAccessRoute(canWith(['ingot.admin.teams']))).toBe('access-teams')
  })

  it('falls back to the users page when no people tab is permitted', () => {
    expect(firstAccessRoute(canWith(['ingot.admin.tokens']))).toBe('access-users')
  })

  it('maps the detail address of a user to the users tab', () => {
    expect(accessTabOf('access-user')).toBe('users')
  })

  it('maps the detail addresses of machine accounts and teams to their tabs', () => {
    expect(accessTabOf('access-machine')).toBe('machines')
    expect(accessTabOf('access-team')).toBe('teams')
  })

  it('maps the sign in and check pages to their own tabs', () => {
    expect(accessTabOf('access-sso')).toBe('sso')
    expect(accessTabOf('access-check')).toBe('check')
  })

  it('returns nothing for a route outside Access', () => {
    expect(accessTabOf('overview')).toBeUndefined()
  })
})

describe('pageLabel', () => {
  it('returns the label of an entry', () => {
    expect(pageLabel('search')).toBe('Search')
    expect(pageLabel('access')).toBe('Access')
  })

  it('names the account page in the document title', () => {
    expect(pageLabel('account')).toBe('My account')
  })

  it('returns the key when no entry is named', () => {
    expect(pageLabel('unknown')).toBe('unknown')
  })
})

describe('routeMeta', () => {
  it('gives an open page only its section', () => {
    expect(routeMeta('search')).toEqual({ section: 'search' })
  })

  it('gives the retired dashboard address no page, because it redirects to Overview', () => {
    const dashboard = ROUTES.find(route => route.name === 'dashboard')!

    expect(dashboard.redirect, 'the address redirects').toBeDefined()
    expect(dashboard.meta, 'the redirect has no meta of a page').toBeUndefined()
    expect(PAGES.map(page => page.key), 'no rail page keeps the dashboard key').not.toContain('dashboard')
  })

  it('gives a manager page its node and the manager flag', () => {
    expect(routeMeta('settings')).toEqual(expect.objectContaining({ section: 'settings', manager: true }))
  })

  it('gives Analytics the settings node or the dashboard node, as its alternatives', () => {
    expect(routeMeta('analytics')).toEqual({
      section: 'analytics',
      manager: true,
      anyNode: [ADMIN_NODES.settings, ADMIN_NODES.dashboard]
    })
  })

  it('gives the Access page its alternative nodes', () => {
    expect(routeMeta('access')).toEqual({ section: 'access', manager: true, anyNode: ACCESS_NODES })
  })

  it('gives a child page the section of its parent and its own node', () => {
    expect(routeMeta('access', 'machines')).toEqual({ section: 'access', manager: true, node: 'ingot.admin.users' })
  })

  it('gives the account page the signed in flag', () => {
    expect(routeMeta('account')).toEqual({ section: 'account', signedIn: true })
  })
})

// The labels and descriptions of the built-in entries, and the shortcuts between them (tasks 3.4 and 4.5). Every test
// sets the registry state it reads, and clears it again, so no test depends on another.
describe('the labels and descriptions of the Administration tree', () => {
  const BUILT_IN_DOMAINS = ['frontend', 'web', 'statistics', 'search', 'javadoc', 'cleanup', 'audit']
  const everything = () => true

  beforeEach(() => {
    setContributions({ version: 1, pages: [], sections: [], enabledPlugins: ['nexus-import'] })
    setSupportBundleOffered(true)
  })

  afterEach(() => {
    setContributions(null)
    setSupportBundleOffered(false)
  })

  const linksWith = (options: Parameters<typeof adminGroups>[1]) => adminGroups(everything, options).flatMap(group => group.links)

  it('gives every built-in entry a label', () => {
    const unlabelled = linksWith({ domains: BUILT_IN_DOMAINS }).filter(link => !link.label).map(link => link.key)

    expect(unlabelled, 'entries without a label').toEqual([])
  })

  it('gives every built-in entry a one-line description', () => {
    const undescribed = linksWith({ domains: BUILT_IN_DOMAINS }).filter(link => !link.description?.trim()).map(link => link.key)

    expect(undescribed, 'entries without a non-empty description').toEqual([])
  })

  it('names the renamed settings domains from the frontend, whatever the schema title says', () => {
    const labels = Object.fromEntries(linksWith({ domains: BUILT_IN_DOMAINS, titleOf: () => 'Schema title' })
      .filter(link => link.to.name === 'settings' && link.to.params !== undefined)
      .map(link => [link.key as string, link.label]))

    expect(labels).toEqual({
      'settings:frontend': 'Branding',
      'settings:web': 'Reverse proxy',
      'settings:search': 'Search index',
      'settings:cleanup': 'Cleanup policies',
      'settings:javadoc': 'Javadoc',
      'settings:audit': 'Audit settings',
    })
  })

  it('takes the schema title and description of a settings domain the frontend does not name', () => {
    const acme = linksWith({
      domains: ['acme'],
      titleOf: () => 'Acme title',
      descriptionOf: () => 'Acme description',
    }).find(link => link.key === 'settings:acme')

    expect(acme).toMatchObject({ label: 'Acme title', description: 'Acme description' })
  })

  it('keeps every key of the tree, in the order of the requirement', () => {
    expect(linksWith({ domains: BUILT_IN_DOMAINS }).map(link => link.key)).toEqual([
      'page:overview', 'page:repositories','settings:cleanup', 'settings:javadoc', 'settings:search',
      'page:users', 'page:machines', 'page:teams', 'page:sso', 'page:ldap', 'page:security', 'page:check',
      'settings:frontend', 'settings:web', 'page:migration',
      'page:audit', 'settings:audit', 'page:console', 'page:maintenance', 'page:jobs', 'page:analytics', 'page:system-info',
    ])
  })
})

describe('shortcuts between the pages of the Administration tree', () => {
  it('links Audit log to Audit settings for a visitor with the settings node', () => {
    expect(shortcutsOf('page:audit', canWith(['ingot.admin.settings']))).toEqual([
      { label: 'Audit settings', to: { name: 'settings', params: { domain: 'audit' } } },
    ])
  })

  it('shows no link from Audit log to Audit settings to a visitor with only the audit node', () => {
    expect(shortcutsOf('page:audit', canWith(['ingot.admin.audit']))).toEqual([])
  })

  it('links Audit settings back to the audit log for a visitor with the audit node', () => {
    expect(shortcutsOf('settings:audit', canWith(['ingot.admin.audit']))).toEqual([
      { label: 'Open the audit log', to: { name: 'audit' } },
    ])
  })

  it('shows no link from Audit settings to the audit log to a visitor with only the settings node', () => {
    expect(shortcutsOf('settings:audit', canWith(['ingot.admin.settings']))).toEqual([])
  })

  it('draws no shortcut on Analytics, because the usage statistics settings are on the page itself', () => {
    expect(shortcutsOf('page:analytics', canWith(['ingot.admin.dashboard', 'ingot.admin.settings']))).toEqual([])
  })

  it('draws no shortcut on Maintenance, whose tabs replace the links to Search index and Cleanup policies', () => {
    expect(shortcutsOf('page:maintenance', canWith(['ingot.admin.maintenance', 'ingot.admin.settings']))).toEqual([])
  })

  it('draws no header shortcut on Search index, whose rebuild link sits in the page content with its explanation', () => {
    expect(shortcutsOf('settings:search', canWith(['ingot.admin.maintenance']))).toEqual([])
  })

  it('offers the rebuild link to the Index tab of Maintenance only to a visitor with the maintenance node', () => {
    expect(indexRebuildLinkOf(canWith(['ingot.admin.maintenance']))).toEqual({ name: 'maintenance', query: { tab: 'index' } })
    expect(indexRebuildLinkOf(canWith(['ingot.admin.settings']))).toBeUndefined()
  })

  it('shows no shortcut on a page without a related page', () => {
    expect(shortcutsOf('page:users', canWith(['*']))).toEqual([])
  })
})

// The Jobs page lists the background runs of the maintenance area and of the instance admin (add-jobs-page, D6, D7).
describe('the Jobs page entry', () => {
  const linksFor = (nodes: string[]) => adminGroups(canUse.bind(null, nodes))
    .flatMap(group => group.links)
    .map(link => link.key)

  it('lists Jobs in the Support group right after Maintenance', () => {
    const keys = linksFor(['ingot.admin.*'])

    expect(keys.indexOf('page:jobs'), 'Jobs is listed').toBeGreaterThan(-1)
    expect(keys.indexOf('page:jobs'), 'Jobs follows Maintenance').toBe(keys.indexOf('page:maintenance') + 1)
  })

  it('lists Jobs for a visitor who holds the maintenance node alone', () => {
    expect(linksFor(['ingot.admin.maintenance'])).toContain('page:jobs')
  })

  it('leaves Jobs out for a visitor who holds neither the maintenance node nor the instance admin node', () => {
    expect(linksFor(['ingot.admin.console'])).not.toContain('page:jobs')
  })

  it('opens the list at /settings/jobs with the maintenance or instance admin node', () => {
    const route = ROUTES.find(entry => entry.name === 'jobs')

    expect(route?.path).toBe('/settings/jobs')
    expect(route?.meta?.anyNode, 'the node alternatives of the Jobs page').toEqual(['ingot.admin.maintenance', 'ingot.admin.*'])
  })

  it('opens one run at /settings/jobs/:id with the same permission', () => {
    const route = ROUTES.find(entry => entry.name === 'job')

    expect(route?.path).toBe('/settings/jobs/:id')
    expect(route?.meta?.anyNode).toEqual(['ingot.admin.maintenance', 'ingot.admin.*'])
  })
})

describe('reader pages', () => {
  const readerManifest = {
    version: 1,
    pages: [
      { plugin: 'docs', id: 'guides', title: 'Guides', blocks: [] },
      { plugin: 'alpha', id: 'notes', title: 'Notes', description: 'Release notes', blocks: [] },
    ],
  }

  // Every test starts without reader pages, so the order of the tests does not change the result.
  beforeEach(() => setReaderPages(null))

  it('lists the reader pages in the order of the manifest', () => {
    setReaderPages(readerManifest)

    expect(readerPages().map(page => `${page.plugin}/${page.id}`), 'the manifest order, not sorted by plugin')
      .toEqual(['docs/guides', 'alpha/notes'])
  })

  it('lists no reader page without a manifest', () => {
    expect(readerPages(), 'no manifest means no reader page').toEqual([])
  })

  it('finds a reader page by its plugin and id', () => {
    setReaderPages(readerManifest)

    expect(readerPageByRoute('alpha', 'notes')?.title, 'the page the route names').toBe('Notes')
    expect(readerPageByRoute('alpha', 'guides'), 'a page id of another plugin').toBeUndefined()
  })

  it('accepts the plugin-reader route as one that has no entry of its own', () => {
    expect(unlistedRoutes(['plugin-reader']), 'the reader route is exempt like settings-plugin').toEqual([])
  })

  it('draws no Plugin pages heading in the rail without a reader page', () => {
    expect(readerRailGroups(), 'no heading and no entry while the manifest has no page').toEqual([])
  })

  it('lists the reader pages under the Plugin pages heading of the rail, in manifest order', () => {
    setReaderPages(readerManifest)

    const groups = readerRailGroups()

    expect(groups.map(group => group.heading), 'one heading, named apart from the Plugins heading of admin pages')
      .toEqual(['Plugin pages'])
    expect(groups[0].pages.map(page => page.key), 'the keys of the reader pages, in manifest order')
      .toEqual(['reader:docs/guides', 'reader:alpha/notes'])
  })

  it('never puts a reader page in the bottom bar or under More', () => {
    setReaderPages(readerManifest)

    const { bar, more } = barLayout(canWith(['ingot.admin.*']))
    const keys = [...bar, ...more.flatMap(group => group.pages)].map(page => page.key)

    expect(keys.filter(key => key.startsWith('reader:')), 'the bar keeps the built-in pages only').toEqual([])
  })

  it('opens the first page of a plugin for an address without a page id', () => {
    setReaderPages(readerManifest)

    expect(readerPageRedirect('alpha', undefined), 'the first page of alpha')
      .toEqual({ name: 'plugin-reader', params: { plugin: 'alpha', page: 'notes' } })
    expect(readerPageRedirect('alpha', 'notes'), 'an address with a page id stays').toBeNull()
    expect(readerPageRedirect('unknown', undefined), 'a plugin without a reader page has no target').toBeNull()
  })
})
