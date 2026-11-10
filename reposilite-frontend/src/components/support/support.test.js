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
import { ROUTES } from '../../router/routes'
import { visibleChildren, groupedChildren } from '../../navigation/registry'
import { canUse } from '../../store/nodeAccess'
import { supportLinks } from './support'

const CONSOLE = 'ingot.admin.console'
const canWith = (nodes) => (node) => canUse(nodes, node)
const keysOf = (items) => items.map(item => item.key)

describe('support pages in the navigation registry', () => {
  it('lists both support pages under Settings for a principal with the console area', () => {
    expect(keysOf(visibleChildren('settings', canWith([CONSOLE])))).toEqual(['system-info', 'support'])
  })

  it('places both support pages in the Support group of Settings', () => {
    const groups = groupedChildren('settings', canWith([CONSOLE]))

    expect(groups.map(group => group.heading)).toEqual(['Support'])
    expect(groups[0].links.map(link => link.to)).toEqual([{ name: 'system-info' }, { name: 'support' }])
  })

  it('hides both support pages from a principal without the console area', () => {
    expect(keysOf(visibleChildren('settings', canWith(['ingot.admin.users'])))).toEqual([])
  })

  it('adds no top level page to the rail, so the rail pages stay as they were', () => {
    const railKeys = ['overview', 'search', 'dashboard', 'analytics', 'audit', 'access', 'settings', 'console', 'maintenance', 'account']

    expect(railKeys).not.toContain('system-info')
    expect(railKeys).not.toContain('support')
  })
})

describe('support routes', () => {
  it('routes the system information page to its own address under settings', () => {
    expect(ROUTES.find(route => route.name === 'system-info')?.path).toBe('/settings/system-info')
  })

  it('routes the support page to its own address under settings', () => {
    expect(ROUTES.find(route => route.name === 'support')?.path).toBe('/settings/support')
  })

  it('guards both support routes with the console node and the manager flag', () => {
    const expected = { section: 'settings', manager: true, node: CONSOLE }

    expect(ROUTES.find(route => route.name === 'system-info')?.meta).toEqual(expected)
    expect(ROUTES.find(route => route.name === 'support')?.meta).toEqual(expected)
  })

  it('places both support addresses before the generic settings route, which would take the names as domains', () => {
    const indexOf = (name) => ROUTES.findIndex(route => route.name === name)

    expect(indexOf('system-info')).toBeLessThan(indexOf('settings'))
    expect(indexOf('support')).toBeLessThan(indexOf('settings'))
  })
})

describe('supportLinks', () => {
  it('offers both pages to a principal with the console area when the feature is on', () => {
    expect(supportLinks(canWith([CONSOLE]), true).map(link => link.key)).toEqual(['system-info', 'support'])
  })

  it('offers no page when the feature is off, whatever the nodes', () => {
    expect(supportLinks(canWith([CONSOLE, '*']), false)).toEqual([])
  })

  it('offers no page without the console area', () => {
    expect(supportLinks(canWith(['ingot.admin.maintenance']), true)).toEqual([])
  })

  it('names each page with its label and route', () => {
    expect(supportLinks(canWith([CONSOLE]), true)).toEqual([
      { key: 'system-info', label: 'System information', to: { name: 'system-info' } },
      { key: 'support', label: 'Support bundle', to: { name: 'support' } },
    ])
  })
})
