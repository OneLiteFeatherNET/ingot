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
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'
import { ROUTES } from '../../router/routes'
import { PAGES, adminGroups, railGroups, setSupportBundleOffered } from '../../navigation/registry'
import { canUse } from '../../store/nodeAccess'

const CONSOLE = 'ingot.admin.console'
const canWith = (nodes: unknown) => (node: unknown) => canUse(nodes, node)
const linksOf = (groups: ReturnType<typeof adminGroups>, heading: string) => groups.find(group => group.heading === heading)?.links ?? []
const keysOf = (links: { key: string | undefined }[]) => links.map(link => link.key)

// The offer of the instance is shared state of the registry, so every test starts without it.
afterEach(() => setSupportBundleOffered(false))

describe('support pages in the Administration tree', () => {
  it('lists one page for the system information and the support bundle in the Support group for a principal with the console area', () => {
    setSupportBundleOffered(true)

    expect(keysOf(linksOf(adminGroups(canWith([CONSOLE])), 'Monitoring and support'))).toEqual(['page:console', 'page:system-info'])
  })

  it('names the one page System and support, with its label and its route', () => {
    setSupportBundleOffered(true)

    expect(linksOf(adminGroups(canWith([CONSOLE])), 'Monitoring and support').slice(1)).toEqual([
      {
        key: 'page:system-info',
        label: 'System and support',
        description: 'What this instance runs on and what a maintainer needs to look at. Secrets are withheld or masked, and the page only reads.',
        keywords: 'version, java, environment, zip, debug',
        to: { name: 'system-info' },
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

  it('redirects the old support address to the system information page, and keeps the route name', () => {
    expect(ROUTES.find(route => route.name === 'support')?.path).toBe('/settings/support')
    expect(ROUTES.find(route => route.name === 'support')?.redirect).toEqual({ name: 'system-info' })
  })

  it('keeps the query and the fragment of the old address when it redirects', async () => {
    const system = ROUTES.find(route => route.name === 'system-info')
    const support = ROUTES.find(route => route.name === 'support')
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ ...system, component: { render: () => null } } as RouteRecordRaw, support as RouteRecordRaw],
    })

    await router.push('/settings/support?tab=plugins#support-cluster')

    expect(router.currentRoute.value.fullPath).toBe('/settings/system-info?tab=plugins#support-cluster')
  })

  it('guards the system information route with the console node and the manager flag', () => {
    expect(ROUTES.find(route => route.name === 'system-info')?.meta).toEqual({ section: 'settings', manager: true, node: CONSOLE })
  })

  it('places both support names before the generic settings route, which would take the names as domains', () => {
    const indexOf = (name: string) => ROUTES.findIndex(route => route.name === name)

    expect(indexOf('system-info')).toBeLessThan(indexOf('settings'))
    expect(indexOf('support')).toBeLessThan(indexOf('settings'))
  })
})
