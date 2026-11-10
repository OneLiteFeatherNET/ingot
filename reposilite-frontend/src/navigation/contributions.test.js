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

import { describe, it, expect, beforeEach } from 'vitest'
import {
  setContributions,
  contributedPages,
  contributedPageByRoute,
  contributedRailGroups,
  railGroups,
  barLayout,
  settingsGroupOf,
  unlistedRoutes,
} from './registry'
import { canUse } from '../store/nodeAccess'

const canWith = (nodes) => (node) => canUse(nodes, node)

const manifest = (overrides = {}) => ({
  version: 1,
  pages: [
    { plugin: 'example', id: 'status', title: 'Status', group: 'System', area: 'maintenance', blocks: [] },
    { plugin: 'example', id: 'audit', title: 'Audit', group: 'Security', area: 'audit', blocks: [] },
    { plugin: 'example', id: 'odd', title: 'Odd', group: 'Finance', area: 'settings', blocks: [] },
  ],
  sections: [{ plugin: 'example', domain: 'mirror', label: 'Mirror checks', group: 'Repository' }],
  enabledPlugins: ['example'],
  ...overrides,
})

// Every test starts without contributions, so the order of the tests does not change the result.
beforeEach(() => setContributions({ version: 1, pages: [], sections: [], enabledPlugins: [] }))

describe('contributed pages', () => {
  it('lists a contributed page for a caller who holds its area', () => {
    setContributions(manifest())

    const pages = contributedPages(canWith(['ingot.admin.maintenance']))

    expect(pages.map(page => page.id)).toEqual(['status'])
  })

  it('lists no contributed page for a caller without its area', () => {
    setContributions(manifest())

    expect(contributedPages(canWith(['ingot.admin.users']))).toEqual([])
  })

  it('places a page in the group it names', () => {
    setContributions(manifest())

    const groups = contributedRailGroups(canWith(['ingot.admin.maintenance', 'ingot.admin.audit', 'ingot.admin.settings']))

    expect(groups.map(group => group.heading)).toEqual(['Security', 'System', 'Plugins'])
  })

  it('places a page whose group is unknown in the Plugins category', () => {
    setContributions(manifest())

    const groups = contributedRailGroups(canWith(['ingot.admin.settings']))

    expect(groups).toEqual([{ heading: 'Plugins', pages: [expect.objectContaining({ id: 'odd' })] }])
  })

  it('never puts a contributed page in the bottom bar or in its More sheet', () => {
    setContributions(manifest())
    const everything = canWith(['*', 'ingot.admin.maintenance', 'ingot.admin.audit', 'ingot.admin.settings'])

    const layout = barLayout(everything)
    const keys = [...layout.bar.map(item => item.key), ...layout.more.flatMap(group => group.pages.map(page => page.key))]

    expect(keys.some(key => key.startsWith('example'))).toBe(false)
  })

  it('leaves the built-in pages in their order and places contributions after them', () => {
    setContributions(manifest())
    const can = canWith(['*'])

    const builtin = railGroups(can).flatMap(group => group.pages.map(page => page.key))
    const contributed = contributedRailGroups(can).flatMap(group => group.pages.map(page => page.id))

    expect(builtin[0]).toBe('overview')
    expect(contributed.length).toBeGreaterThan(0)
  })

  it('finds the page of a route by plugin and page id', () => {
    setContributions(manifest())

    expect(contributedPageByRoute('example', 'status')?.title).toBe('Status')
    expect(contributedPageByRoute('example', 'missing')).toBeUndefined()
  })

  it('shows nothing once the contributions are cleared', () => {
    setContributions(manifest())
    setContributions({ version: 1, pages: [], sections: [], enabledPlugins: [] })

    expect(contributedPages(canWith(['*']))).toEqual([])
  })
})

describe('contributed settings sections', () => {
  it('places a domain of a section under the group of the section', () => {
    setContributions(manifest())

    expect(settingsGroupOf('mirror')).toBe('Repository')
  })

  it('keeps a domain without a section in its fallback group', () => {
    setContributions(manifest())

    expect(settingsGroupOf('web')).toBe('Instance')
    expect(settingsGroupOf('unknown-plugin-domain')).toBe('Plugins')
  })
})

describe('route names', () => {
  it('accepts the settings-plugin route as one that has no entry of its own', () => {
    expect(unlistedRoutes(['settings-plugin'])).toEqual([])
  })
})
