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

import { describe, it, expect } from 'vitest'
import {
  PAGES,
  visiblePages,
  visibleChildren,
  firstAccessRoute,
  accessTabOf,
  pageLabel,
  routeMeta,
  groupedChildren,
  sideNavOf,
  settingsGroupOf,
  groupedSettingsDomains,
  railGroups,
  barLayout
} from './registry'
import { ROUTES } from '../router/routes'
import { ACCESS_NODES, canUse } from '../store/nodeAccess'

// A session answers for a node from its node list, as the session store does.
const canWith = (nodes) => (node) => canUse(nodes, node)
const labelsFor = (nodes) => visiblePages(canWith(nodes)).map(page => page.label)
const keysOf = (items) => items.map(item => item.key)

const ADMIN_PAGE_LABELS = ['Repositories', 'Search', 'Dashboard', 'Analytics', 'Audit log', 'Access', 'Settings', 'Console', 'Maintenance']

describe('registry entries', () => {
  it('gives every entry a key, a route name and a label', () => {
    const incomplete = PAGES.filter(page => !page.key || !page.route || !page.label).map(page => page.key)

    expect(incomplete, 'entries without a key, a route name or a label').toEqual([])
  })

  it('places every entry in a known area', () => {
    const areas = ['work', 'monitor', 'administration', 'troubleshoot', 'none']
    const unknown = PAGES.filter(page => !areas.includes(page.area)).map(page => page.key)

    expect(unknown, 'entries whose area is not one of the four groups or none').toEqual([])
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

describe('visiblePages', () => {
  it('offers only overview and search to a visitor who may use no admin node', () => {
    expect(keysOf(visiblePages(canWith([])))).toEqual(['overview', 'search'])
  })

  it('offers Access, and no other admin page, to a visitor who holds only the users node', () => {
    expect(labelsFor(['ingot.admin.users'])).toEqual(['Repositories', 'Search', 'Access'])
  })

  it('offers the audit log, and no other admin page, to a visitor who holds only the audit node', () => {
    expect(labelsFor(['ingot.admin.audit'])).toEqual(['Repositories', 'Search', 'Audit log'])
  })

  it('offers every admin page to a full administrator, in the rail order', () => {
    expect(labelsFor(['ingot.admin.*'])).toEqual(ADMIN_PAGE_LABELS)
  })

  it('offers every page to an instance administrator with the lone wildcard', () => {
    expect(labelsFor(['*'])).toEqual(ADMIN_PAGE_LABELS)
  })

  it('offers only overview and search to a user with repository nodes but no admin node', () => {
    expect(labelsFor(['ingot.repository.releases.read'])).toEqual(['Repositories', 'Search'])
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

describe('bar order and overflow', () => {
  it('orders the bar as the design lists it', () => {
    const ordered = PAGES
      .filter(page => page.barOrder !== undefined)
      .sort((a, b) => a.barOrder - b.barOrder)
      .map(page => page.key)

    expect(ordered).toEqual(['overview', 'search', 'dashboard', 'access', 'settings', 'analytics', 'audit', 'console', 'maintenance'])
  })

  it('gives no bar order to the account page, which is never in the bar', () => {
    expect(PAGES.find(page => page.key === 'account')?.barOrder).toBeUndefined()
  })

  it('carries no static overflow flag, the bar rule decides what sits under More', () => {
    expect(PAGES.filter(page => 'overflow' in page).map(page => page.key)).toEqual([])
  })

  it('lists the rail pages by group: Work, Monitor, Administration, Troubleshoot', () => {
    expect(keysOf(visiblePages(() => true))).toEqual([
      'overview', 'search', 'dashboard', 'analytics', 'audit', 'access', 'settings', 'console', 'maintenance'
    ])
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

describe('groupedChildren', () => {
  it('groups the Access pages of a full administrator under their headings, in order', () => {
    const groups = groupedChildren('access', canWith(['ingot.admin.*']))

    expect(groups.map(group => [group.heading, group.links.map(link => link.label)])).toEqual([
      ['People', ['Users', 'Machine accounts', 'Teams']],
      ['Sign in', ['SSO', 'LDAP', 'Security']],
      ['Tools', ['Check']]
    ])
  })

  it('gives each link its key and the router location of its route', () => {
    const [people] = groupedChildren('access', canWith(['ingot.admin.*']))

    expect(people.links[2]).toEqual({ key: 'teams', label: 'Teams', to: { name: 'access-teams' } })
  })

  it('leaves out a group without a page the visitor may open', () => {
    const groups = groupedChildren('access', canWith(['ingot.admin.teams']))

    expect(groups.map(group => group.heading), 'a teams-only visitor has no sign in pages').toEqual(['People', 'Tools'])
  })

  it('returns no group for a visitor without a permission', () => {
    expect(groupedChildren('access', canWith([]))).toEqual([])
  })
})

describe('sideNavOf', () => {
  it('names the navigation, the first crumb and the index location from the entry', () => {
    expect(sideNavOf('access')).toEqual({ label: 'Access sections', sectionLabel: 'Access', indexTo: { name: 'access' } })
    expect(sideNavOf('settings')).toEqual({ label: 'Settings sections', sectionLabel: 'Settings', indexTo: { name: 'settings' } })
  })
})

describe('settings domain groups', () => {
  it.each([
    ['maven', 'Repository'],
    ['javadoc', 'Repository'],
    ['cleanup', 'Repository'],
    ['web', 'Instance'],
    ['frontend', 'Instance'],
    ['statistics', 'Data'],
    ['audit', 'Data'],
    ['search', 'Data']
  ])('places the domain %s in %s', (domain, heading) => {
    expect(settingsGroupOf(domain)).toBe(heading)
  })

  it('places a domain the mapping does not name in Plugins', () => {
    expect(settingsGroupOf('my-plugin')).toBe('Plugins')
  })

  it('groups a domain list in the order Repository, Instance, Data, Plugins and keeps the server order inside', () => {
    const groups = groupedSettingsDomains(['search', 'extra', 'cleanup', 'maven', 'web'])

    expect(groups).toEqual([
      { heading: 'Repository', domains: ['cleanup', 'maven'] },
      { heading: 'Instance', domains: ['web'] },
      { heading: 'Data', domains: ['search'] },
      { heading: 'Plugins', domains: ['extra'] }
    ])
  })

  it('leaves out a group that holds no domain', () => {
    expect(groupedSettingsDomains(['maven']).map(group => group.heading)).toEqual(['Repository'])
  })
})

const GROUP_HEADINGS = (groups) => groups.map(group => group.heading)
const labelsOf = (groups) => groups.map(group => group.pages.map(page => page.label))

describe('railGroups', () => {
  it('groups a full administrator into Work, Monitor, Administration and Troubleshoot, in that order', () => {
    const groups = railGroups(canWith(['ingot.admin.*']))

    expect(GROUP_HEADINGS(groups)).toEqual(['Work', 'Monitor', 'Administration', 'Troubleshoot'])
    expect(labelsOf(groups)).toEqual([
      ['Repositories', 'Search'],
      ['Dashboard', 'Analytics', 'Audit log'],
      ['Access', 'Settings'],
      ['Console', 'Maintenance']
    ])
  })

  it('labels the overview route Repositories and the audit route Audit log', () => {
    expect(pageLabel('overview')).toBe('Repositories')
    expect(pageLabel('audit')).toBe('Audit log')
  })

  it('leaves out a group that has no permitted page', () => {
    const groups = railGroups(canWith(['ingot.admin.audit']))

    expect(GROUP_HEADINGS(groups), 'Administration and Troubleshoot are empty').toEqual(['Work', 'Monitor'])
    expect(labelsOf(groups)[1]).toEqual(['Audit log'])
  })

  it('gives an anonymous visitor the Work group alone', () => {
    expect(GROUP_HEADINGS(railGroups(canWith([])))).toEqual(['Work'])
  })
})

describe('barLayout', () => {
  const barLabels = (nodes) => barLayout(canWith(nodes)).bar.map(page => page.label)
  const moreOf = (nodes) => barLayout(canWith(nodes)).more

  it.each([
    ['a full administrator', ['ingot.admin.*'], ['Repositories', 'Search', 'Dashboard', 'Access'], [
      { heading: 'Monitor', labels: ['Analytics', 'Audit log'] },
      { heading: 'Administration', labels: ['Settings'] },
      { heading: 'Troubleshoot', labels: ['Console', 'Maintenance'] }
    ]],
    ['a visitor with the audit node only', ['ingot.admin.audit'], ['Repositories', 'Search', 'Audit log'], []],
    ['an anonymous visitor', [], ['Repositories', 'Search'], []],
    ['a visitor with exactly four permitted entries', ['ingot.admin.console', 'ingot.admin.audit'], ['Repositories', 'Search', 'Audit log', 'Console'], []]
  ])('puts %s in the bar and the rest under More', (_who, nodes, bar, more) => {
    expect(barLabels(nodes), 'the first four permitted entries in bar order').toEqual(bar)
    expect(moreOf(nodes).map(group => ({ heading: group.heading, labels: group.pages.map(page => page.label) })),
      'the leftovers under their group headings').toEqual(more)
  })
})
