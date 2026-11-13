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
  shortcutsOf
} from './registry'
import { ROUTES } from '../router/routes'
import { ACCESS_NODES, canUse } from '../store/nodeAccess'

// A session answers for a node from its node list, as the session store does.
const canWith = (nodes) => (node) => canUse(nodes, node)
const keysOf = (items) => items.map(item => item.key)


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
      ROUTES.filter(route => route.meta?.node).map(route => [route.name, route.meta.node])
    )

    expect(nodes).toMatchObject({
      dashboard: 'ingot.admin.dashboard',
      analytics: 'ingot.admin.dashboard',
      console: 'ingot.admin.console',
      maintenance: 'ingot.admin.maintenance',
      audit: 'ingot.admin.audit',
      settings: 'ingot.admin.settings',
      'access-users': 'ingot.admin.users',
      'access-machines': 'ingot.admin.users',
      'access-teams': 'ingot.admin.teams',
      tokens: 'ingot.admin.users',
      token: 'ingot.admin.users'
    })
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
    ['the dashboard node alone, which opens analytics', ['ingot.admin.dashboard'], { name: 'analytics' }],
    ['the console and dashboard nodes, which open console and not analytics', ['ingot.admin.console', 'ingot.admin.dashboard'], { name: 'console' }],
    ['the audit and console nodes, which open audit log', ['ingot.admin.audit', 'ingot.admin.console'], { name: 'audit' }],
    ['the maintenance and dashboard nodes, which open maintenance', ['ingot.admin.maintenance', 'ingot.admin.dashboard'], { name: 'maintenance' }],
    ['the users and console nodes, which open the users page', ['ingot.admin.users', 'ingot.admin.console'], { name: 'access-users' }],
  ])('opens the page the same as before for %s', (_description, nodes, expected) => {
    expect(administrationLocation(canUse.bind(null, nodes)), `the location for ${nodes.join(', ') || 'no node'}`).toEqual(expected)
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

  it('gives a manager page its node and the manager flag', () => {
    expect(routeMeta('dashboard')).toEqual({ section: 'dashboard', manager: true, node: 'ingot.admin.dashboard' })
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

  const linksWith = (options) => adminGroups(everything, options).flatMap(group => group.links)

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
      .map(link => [link.key, link.label]))

    expect(labels).toEqual({
      'settings:frontend': 'Branding',
      'settings:web': 'Reverse proxy',
      'settings:statistics': 'Usage statistics',
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
      'settings:frontend', 'settings:web', 'settings:statistics', 'page:migration',
      'page:audit', 'settings:audit', 'page:console', 'page:maintenance', 'page:analytics', 'page:system-info', 'page:support',
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

  it('links Analytics to Usage statistics for a visitor with the settings node', () => {
    expect(shortcutsOf('page:analytics', canWith(['ingot.admin.dashboard', 'ingot.admin.settings']))).toEqual([
      { label: 'Usage statistics', to: { name: 'settings', params: { domain: 'statistics' } } },
    ])
  })

  it('links Maintenance to Search index and Cleanup policies, in that order', () => {
    expect(shortcutsOf('page:maintenance', canWith(['ingot.admin.maintenance', 'ingot.admin.settings']))).toEqual([
      { label: 'Search index', to: { name: 'settings', params: { domain: 'search' } } },
      { label: 'Cleanup policies', to: { name: 'settings', params: { domain: 'cleanup' } } },
    ])
  })

  it('links Search index to Maintenance for a visitor with the maintenance node', () => {
    expect(shortcutsOf('settings:search', canWith(['ingot.admin.maintenance']))).toEqual([
      { label: 'Rebuild now on Maintenance', to: { name: 'maintenance' } },
    ])
  })

  it('shows no shortcut on a page without a related page', () => {
    expect(shortcutsOf('page:users', canWith(['*']))).toEqual([])
  })
})
