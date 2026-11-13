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

import { afterEach, describe, it, expect } from 'vitest'
import { ROUTES } from '../../router/routes'
import { PAGES, adminGroups, railGroups, setSupportBundleOffered } from '../../navigation/registry'
import { canUse } from '../../store/nodeAccess'

const CONSOLE = 'ingot.admin.console'
const canWith = (nodes) => (node) => canUse(nodes, node)
const linksOf = (groups, heading) => groups.find(group => group.heading === heading)?.links ?? []
const keysOf = (links) => links.map(link => link.key)

// The offer of the instance is shared state of the registry, so every test starts without it.
afterEach(() => setSupportBundleOffered(false))

describe('support pages in the Administration tree', () => {
  it('lists both support pages in the Support group for a principal with the console area', () => {
    setSupportBundleOffered(true)

    expect(keysOf(linksOf(adminGroups(canWith([CONSOLE])), 'Monitoring and support'))).toEqual(['page:console', 'page:system-info', 'page:support'])
  })

  it('names each page with its label and its route', () => {
    setSupportBundleOffered(true)

    expect(linksOf(adminGroups(canWith([CONSOLE])), 'Monitoring and support').slice(1)).toEqual([
      {
        key: 'page:system-info',
        label: 'System information',
        description: 'What this instance runs on. Secrets are withheld or masked, and the page only reads.',
        to: { name: 'system-info' },
      },
      {
        key: 'page:support',
        label: 'Support bundle',
        description: 'What a maintainer needs to look at this instance, as one zip.',
        to: { name: 'support' },
      },
    ])
  })

  it('hides both support pages while the instance does not offer them, whatever the nodes', () => {
    setSupportBundleOffered(false)

    expect(keysOf(linksOf(adminGroups(canWith([CONSOLE])), 'Monitoring and support'))).toEqual(['page:console'])
  })

  it('hides both support pages from a principal without the console area', () => {
    setSupportBundleOffered(true)

    expect(linksOf(adminGroups(canWith(['ingot.admin.users'])), 'Monitoring and support')).toEqual([])
  })

  it('adds no top level page to the rail, so the rail pages stay as they were', () => {
    const pageKeys = PAGES.map(page => page.key)

    expect(pageKeys).not.toContain('system-info')
    expect(pageKeys).not.toContain('support')
    expect(railGroups(canWith([CONSOLE])).flatMap(group => group.pages.map(page => page.key))).not.toContain('support')
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
