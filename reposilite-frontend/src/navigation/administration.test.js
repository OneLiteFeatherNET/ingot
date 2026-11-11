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
  adminGroups,
  administrationLocation,
  barLayout,
  pageLabel,
  railGroups,
  reservedDomainIds,
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
    expect(linksOf(groups, 'Repository').map(link => link.key)).toEqual(['repositories'])
    expect(linksOf(groups, 'Security').map(link => link.key)).toEqual([
      'users', 'machines', 'teams', 'sso', 'ldap', 'security', 'check', 'audit'
    ])
  })

  it('puts Console, Maintenance and Analytics under Support', () => {
    expect(linksOf(adminGroups(everything), 'Support').map(link => link.key)).toEqual(['console', 'maintenance', 'analytics'])
  })

  it('leaves out a group that has no visible page', () => {
    expect(headingsOf(adminGroups(only(ADMIN_NODES.console)))).toEqual(['Support'])
  })

  it('shows only the pages the visitor may open, and nothing for a visitor with no node', () => {
    expect(adminGroups(nothing)).toEqual([])
  })

  it('gives each link a router location by route name', () => {
    expect(linksOf(adminGroups(everything), 'Security')[0]).toMatchObject({
      key: 'users',
      label: 'Users',
      to: { name: 'access-users' }
    })
  })

  it('puts a plugin settings domain under System at its settings address', () => {
    const groups = adminGroups(everything, { domains: ['maven', 'acme-plugin'], titleOf: (domain) => `Title of ${domain}` })
    expect(linksOf(groups, 'System')).toContainEqual({
      key: 'acme-plugin',
      label: 'Title of acme-plugin',
      to: { name: 'settings', params: { domain: 'acme-plugin' } }
    })
  })

  it('puts the maven, javadoc and cleanup domains under Repository', () => {
    const groups = adminGroups(everything, { domains: ['maven', 'javadoc', 'cleanup', 'web'] })
    expect(linksOf(groups, 'Repository').map(link => link.key)).toEqual(['repositories', 'maven', 'javadoc', 'cleanup'])
    expect(linksOf(groups, 'System').map(link => link.key)).toEqual(['web'])
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

describe('route coverage of the new pages', () => {
  it('covers the repository routes, so the suite accepts them in the route table', () => {
    expect(unlistedRoutes(['settings-repositories', 'settings-repository'])).toEqual([])
  })
})
