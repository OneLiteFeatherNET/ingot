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

import { describe, it, expect, afterEach } from 'vitest'
import {
  adminGroups,
  administrationLocation,
  barLayout,
  currentTreeKey,
  pageLabel,
  railGroups,
  reservedDomainIds,
  setContributions,
  sideNavOf,
  unlistedRoutes
} from './registry'
import { ADMIN_NODES } from '../store/nodeAccess'

// `can` answers for a set of nodes. Each test builds its own, so none shares state.
const everything = () => true
const nothing = () => false
const only = (...nodes) => (node) => nodes.includes(node)

const railLabels = (can) => railGroups(can).flatMap(group => group.pages.map(page => page.label))
const headingsOf = (groups) => groups.map(group => group.heading)
const linksOf = (groups, heading) => groups.find(group => group.heading === heading)?.links ?? []
const allLinks = (groups) => groups.flatMap(group => group.links)
const keyOfLabel = (groups, label) => allLinks(groups).find(link => link.label === label)?.key

describe('the breadcrumb of an Administration page', () => {
  it('starts with Administration on every Administration page, not with the page itself', () => {
    for (const key of ['audit', 'console', 'maintenance', 'analytics', 'access', 'settings']) {
      expect(sideNavOf(key).sectionLabel, `the first crumb of ${key}`).toBe('Administration')
    }
  })
})

describe('the four entries of the rail', () => {
  it('shows Browse, Search, Dashboard and Administration to an administrator, in that order', () => {
    expect(railLabels(everything)).toEqual(['Browse', 'Search', 'Dashboard', 'Administration'])
  })

  it('names the browse page Browse, and keeps its route name', () => {
    expect(pageLabel('overview')).toBe('Browse')
  })

  it('shows Browse and Search only to an anonymous visitor', () => {
    expect(railLabels(nothing)).toEqual(['Browse', 'Search'])
  })

  it('shows Administration to a visitor with only the audit node, and sends it to the audit log', () => {
    const can = only(ADMIN_NODES.audit)
    expect(railLabels(can)).toEqual(['Browse', 'Search', 'Administration'])
    expect(administrationLocation(can)).toEqual({ name: 'audit' })
  })

  it('sends an administrator to the hub at /settings', () => {
    expect(administrationLocation(everything)).toEqual({ name: 'settings' })
  })

  it('hides Dashboard from a visitor without the dashboard node', () => {
    expect(railLabels(only(ADMIN_NODES.settings))).toEqual(['Browse', 'Search', 'Administration'])
  })
})

describe('the bottom bar', () => {
  it('holds the four entries and no More item for an administrator', () => {
    const layout = barLayout(everything)
    expect(layout.bar.map(page => page.label)).toEqual(['Browse', 'Search', 'Dashboard', 'Administration'])
    expect(layout.more).toEqual([])
  })
})

describe('the Administration tree', () => {
  it('groups the pages as Repository, Security, System and Support', () => {
    expect(headingsOf(adminGroups(everything, { domains: ['web'] }))).toEqual(['Repository', 'Security', 'System', 'Support'])
  })

  it('leaves out System when the server lists no settings domain outside the groups', () => {
    expect(headingsOf(adminGroups(everything))).toEqual(['Repository', 'Security', 'Support'])
  })

  it('lists Repositories under Repository, and the access pages and audit log under Security', () => {
    const groups = adminGroups(everything)
    expect(linksOf(groups, 'Repository').map(link => link.key)).toEqual(['page:repositories'])
    expect(linksOf(groups, 'Security').map(link => link.key)).toEqual([
      'page:users', 'page:machines', 'page:teams', 'page:sso', 'page:ldap', 'page:security', 'page:check', 'page:audit'
    ])
  })

  it('puts Console, Maintenance and Analytics under Support', () => {
    expect(linksOf(adminGroups(everything), 'Support').map(link => link.key)).toEqual(['page:console', 'page:maintenance', 'page:analytics'])
  })

  it('leaves out a group that has no visible page', () => {
    expect(headingsOf(adminGroups(only(ADMIN_NODES.console)))).toEqual(['Support'])
  })

  it('shows only the pages the visitor may open, and nothing for a visitor with no node', () => {
    expect(adminGroups(nothing)).toEqual([])
  })

  it('gives each link a router location by route name', () => {
    expect(linksOf(adminGroups(everything), 'Security')[0]).toMatchObject({
      key: 'page:users',
      label: 'Users',
      to: { name: 'access-users' }
    })
  })

  it('puts a plugin settings domain under System at its settings address', () => {
    const groups = adminGroups(everything, { domains: ['maven', 'acme-plugin'], titleOf: (domain) => `Title of ${domain}` })
    expect(linksOf(groups, 'System')).toContainEqual({
      key: 'settings:acme-plugin',
      label: 'Title of acme-plugin',
      to: { name: 'settings', params: { domain: 'acme-plugin' } }
    })
  })

  it('puts the maven, javadoc and cleanup domains under Repository', () => {
    const groups = adminGroups(everything, { domains: ['maven', 'javadoc', 'cleanup', 'web'] })
    expect(linksOf(groups, 'Repository').map(link => link.key)).toEqual(['page:repositories', 'settings:maven', 'settings:javadoc', 'settings:cleanup'])
    expect(linksOf(groups, 'System').map(link => link.key)).toEqual(['settings:web'])
  })

  it('lists no settings domain to a visitor without the settings node', () => {
    const groups = adminGroups(only(ADMIN_NODES.audit), { domains: ['maven', 'web'] })
    expect(headingsOf(groups)).toEqual(['Security'])
  })

  it('reserves the page names under /settings against settings domain ids', () => {
    expect(reservedDomainIds(['maven', 'repositories', 'acme-plugin'])).toEqual(['repositories'])
    expect(reservedDomainIds(['maven', 'web'])).toEqual([])
  })
})

describe('the keys of the Administration tree', () => {
  it('gives no two entries the same key, when a settings domain has the id of a page', () => {
    const groups = adminGroups(everything, { domains: ['audit', 'users', 'security', 'maven'], titleOf: (domain) => domain })
    const keys = allLinks(groups).map(link => link.key)
    expect(new Set(keys).size, `the keys ${keys.join(', ')} are unique`).toBe(keys.length)
  })

  it('gives no two entries the same key, when a plugin page shares its id with a page of the tree', () => {
    setContributions({
      version: 1,
      pages: [{ plugin: 'acme', id: 'users', title: 'Acme users', group: 'Security', area: 'users', blocks: [] }],
      sections: [],
      enabledPlugins: ['acme'],
    })
    try {
      const keys = allLinks(adminGroups(everything, { domains: ['users'] })).map(link => link.key)
      expect(new Set(keys).size, `the keys ${keys.join(', ')} are unique`).toBe(keys.length)
    } finally {
      setContributions(null)
    }
  })

  it('marks the Audit settings domain on /settings/audit, and not the Audit log entry', () => {
    const groups = adminGroups(everything, { domains: ['audit'], titleOf: (domain) => (domain === 'audit' ? 'Audit settings' : domain) })
    expect(currentTreeKey('settings', 'audit'), 'the current entry of /settings/audit').toBe(keyOfLabel(groups, 'Audit settings'))
    expect(keyOfLabel(groups, 'Audit log')).not.toBe(currentTreeKey('settings', 'audit'))
  })

  it('marks the Audit log entry on /audit, and no settings domain', () => {
    const groups = adminGroups(everything, { domains: ['audit'], titleOf: (domain) => (domain === 'audit' ? 'Audit settings' : domain) })
    expect(currentTreeKey('audit'), 'the current entry of /audit').toBe(keyOfLabel(groups, 'Audit log'))
  })

  it('marks no entry on the settings hub, which is no entry of the tree', () => {
    expect(currentTreeKey('settings')).toBeUndefined()
  })

  it('marks the page of a detail route with the page it belongs to', () => {
    expect(currentTreeKey('settings-repository')).toBe('page:repositories')
  })
})

describe('route coverage of the new pages', () => {
  it('covers the repository routes, so the suite accepts them in the route table', () => {
    expect(unlistedRoutes(['settings-repositories', 'settings-repository'])).toEqual([])
  })
})

describe('plugin pages in the Administration tree', () => {
  afterEach(() => setContributions(null))

  const contributed = (group) => ({
    version: 1,
    pages: [{ plugin: 'example', id: 'status', title: 'Status', group, area: 'maintenance', blocks: [] }],
    sections: [],
    enabledPlugins: ['example'],
  })

  it('lists a plugin page under the group its manifest names', () => {
    setContributions(contributed('Support'))
    expect(linksOf(adminGroups(everything), 'Support').map(link => link.label)).toContain('Status')
  })

  it('links a plugin page to its settings address', () => {
    setContributions(contributed('Support'))
    expect(linksOf(adminGroups(everything), 'Support')).toContainEqual(expect.objectContaining({
      label: 'Status',
      to: { name: 'settings-plugin', params: { plugin: 'example', page: 'status' } },
    }))
  })

  it('puts a page whose group is unknown in a Plugins group at the end of the tree', () => {
    setContributions(contributed('Odd'))
    const groups = adminGroups(everything)
    expect(headingsOf(groups).at(-1)).toBe('Plugins')
    expect(linksOf(groups, 'Plugins').map(link => link.label)).toEqual(['Status'])
  })

  it('leaves a plugin page out of the tree for a visitor without its area', () => {
    setContributions(contributed('Support'))
    expect(linksOf(adminGroups(only(ADMIN_NODES.audit)), 'Support')).toEqual([])
  })
})
