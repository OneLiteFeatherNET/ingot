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
import { SECTIONS, MORE_KEY, visibleSections, sectionLabel, isOverflow } from './index-tabs'
import { ROUTES } from '../router/routes'
import { canUse } from '../store/nodeAccess'

// A session answers for a node from its node list, as the session store does.
const canWith = (nodes) => (node) => canUse(nodes, node)
const labelsFor = (nodes) => visibleSections(canWith(nodes)).map(section => section.label)

describe('visibleSections', () => {
  it('offers only overview and search to a visitor who may use no admin node', () => {
    expect(visibleSections(() => false).map(section => section.key)).toEqual(['overview', 'search'])
  })

  it('offers every section to a visitor who may use all admin nodes, in the order of the navigation', () => {
    expect(labelsFor(['ingot.admin.*']))
      .toEqual(['Overview', 'Search', 'Dashboard', 'Access', 'Console', 'Maintenance', 'Audit', 'Settings'])
  })

  it('offers every section to an instance admin with the lone wildcard', () => {
    expect(labelsFor(['*'])).toHaveLength(SECTIONS.length)
  })

  it('hides Maintenance from a user without ingot.admin.maintenance', () => {
    expect(labelsFor(['ingot.admin.audit'])).not.toContain('Maintenance')
  })

  it('shows Maintenance to a user with ingot.admin.maintenance', () => {
    expect(labelsFor(['ingot.admin.maintenance'])).toContain('Maintenance')
  })

  it('hides Access from a user with no access node', () => {
    expect(labelsFor(['ingot.admin.audit'])).not.toContain('Access')
  })

  it('shows Access to a user with only the teams node', () => {
    expect(labelsFor(['ingot.admin.teams'])).toContain('Access')
  })

  it('offers only overview and search to a user with repository nodes but no admin node', () => {
    expect(labelsFor(['ingot.repository.releases.read'])).toEqual(['Overview', 'Search'])
  })
})

describe('search section', () => {
  it('sits next to overview and opens the search route', () => {
    const keys = SECTIONS.map(section => section.key)

    expect(keys.slice(0, 2)).toEqual(['overview', 'search'])
    expect(SECTIONS.find(section => section.key === 'search')?.route).toBe('search')
  })

  it('is open to a visitor who is not a manager', () => {
    expect(SECTIONS.find(section => section.key === 'search')?.manager).toBe(false)
  })

  it('names the section in the document title', () => {
    expect(sectionLabel('search')).toBe('Search')
  })
})

describe('access section', () => {
  it('replaces the Tokens entry', () => {
    const labels = visibleSections(() => true).map(section => section.label)

    expect(labels).toContain('Access')
    expect(labels, 'tokens are machine accounts under Access now').not.toContain('Tokens')
  })

  it('opens the access route', () => {
    expect(SECTIONS.find(section => section.key === 'access')?.route).toBe('access')
  })

  it('names the section in the document title', () => {
    expect(sectionLabel('access')).toBe('Access')
  })
})

describe('section table', () => {
  it('names a route for every section, and that route exists', () => {
    const routeNames = new Set(ROUTES.map(route => route.name))

    for (const section of SECTIONS) {
      expect(routeNames.has(section.route), `the section ${section.key} links to a route that exists`).toBe(true)
    }
  })

  it('marks exactly the manager sections as manager only', () => {
    const managerOnly = SECTIONS.filter(section => section.manager).map(section => section.key)

    expect(managerOnly).toEqual(['dashboard', 'access', 'console', 'maintenance', 'audit', 'settings'])
  })

  it('places the admin pages after console, settings stays last', () => {
    const keys = SECTIONS.map(section => section.key)

    expect(keys.slice(-4)).toEqual(['console', 'maintenance', 'audit', 'settings'])
  })

  it('guards the maintenance route with the manager permission', () => {
    const route = ROUTES.find(entry => entry.name === 'maintenance')

    expect(route?.path).toBe('/maintenance')
    expect(route?.meta).toEqual({ section: 'maintenance', manager: true, node: 'ingot.admin.maintenance' })
  })

  it('names the node of every admin route', () => {
    const nodes = Object.fromEntries(
      ROUTES.filter(route => route.meta?.node).map(route => [route.name, route.meta.node])
    )

    expect(nodes).toMatchObject({
      dashboard: 'ingot.admin.dashboard',
      console: 'ingot.admin.console',
      maintenance: 'ingot.admin.maintenance',
      audit: 'ingot.admin.audit',
      settings: 'ingot.admin.settings',
      'access-users': 'ingot.admin.users',
      'access-machines': 'ingot.admin.tokens',
      'access-teams': 'ingot.admin.teams',
      tokens: 'ingot.admin.tokens',
      token: 'ingot.admin.tokens'
    })
  })

  it('keeps the manager flag on every route that names a node, for legacy sessions', () => {
    const missing = ROUTES.filter(route => (route.meta?.node || route.meta?.anyNode) && !route.meta.manager)

    expect(missing.map(route => route.name)).toEqual([])
  })
})

describe('overflow', () => {
  it('moves the rare admin pages under More and keeps the daily ones in the bar', () => {
    expect(SECTIONS.filter(section => isOverflow(section.key)).map(section => section.key)).toEqual(['console', 'maintenance', 'audit', 'settings'])
    expect(isOverflow('access')).toBe(false)
    expect(isOverflow('search')).toBe(false)
    expect(isOverflow('unknown')).toBe(false)
  })

  it('reserves a key for the More destination that cannot clash with a section', () => {
    expect(SECTIONS.map(section => section.key)).not.toContain(MORE_KEY)
  })
})

describe('sectionLabel', () => {
  it('returns the label the navigation and the views expect', () => {
    expect(sectionLabel('settings')).toBe('Settings')
  })

  it('returns the key when the section is unknown', () => {
    expect(sectionLabel('unknown')).toBe('unknown')
  })
})
