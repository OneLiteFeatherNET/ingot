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
import { setContributions, adminGroups, barLayout, unlistedRoutes } from './registry'
import { canUse, INSTANCE_ADMIN_NODE } from '../store/nodeAccess'
import { ROUTES } from '../router/routes'

// What the session answers for an instance admin and for a visitor who may only open the dashboard.
const instanceAdmin = (node) => canUse(['ingot.admin.*'], node)
const dashboardOnly = (node) => canUse(['ingot.admin.dashboard'], node)

const manifest = (enabledPlugins, pages = []) => ({ pages, sections: [], enabledPlugins })

// The keys of the links of the Administration tree the visitor may open, with their group heading.
const treeLinksOf = (can) => adminGroups(can, { domains: [] }).flatMap(group => group.links.map(link => ({ ...link, heading: group.heading })))
const treeKeysOf = (can) => treeLinksOf(can).map(link => link.key)

// Each test sets the manifest it needs, so no test sees the manifest of another.
beforeEach(() => setContributions(null))

describe('the Migration entry of the navigation', () => {
  it('lists Migration in the Instance group of the Administration tree for an instance admin when nexus-import is enabled', () => {
    setContributions(manifest(['nexus-import']))

    const migration = treeLinksOf(instanceAdmin).find(link => link.key === 'page:migration')

    expect(migration, 'Migration is listed in the tree').toBeDefined()
    expect(migration.heading, 'and sits in the Instance group').toBe('Instance')
    expect(migration.to).toEqual({ name: 'settings-migration' })
  })

  it('lists no Migration entry when nexus-import is not enabled', () => {
    setContributions(manifest([]))

    expect(treeKeysOf(instanceAdmin), 'the entry is absent without the plugin').not.toContain('page:migration')
  })

  it('lists no Migration entry to a visitor without the instance admin permission', () => {
    setContributions(manifest(['nexus-import']))

    expect(treeKeysOf(dashboardOnly), 'the entry needs the instance admin').not.toContain('page:migration')
  })

  it('lists Migration while the plugin UI switch is off, because enabledPlugins is always reported', () => {
    setContributions(manifest(['nexus-import'], []))

    expect(treeKeysOf(instanceAdmin)).toContain('page:migration')
  })

  it('keeps Migration out of the bottom bar', () => {
    setContributions(manifest(['nexus-import']))

    const layout = barLayout(instanceAdmin)

    expect(layout.bar.map(page => page.key)).not.toContain('page:migration')
    expect(layout.more.flatMap(group => group.pages.map(page => page.key))).not.toContain('page:migration')
  })

  it('names the route of the entry in the route table, so the registry suite keeps covering it', () => {
    expect(unlistedRoutes(ROUTES.map(route => route.name))).toEqual([])
  })
})

describe('the settings domains', () => {
  it('does not use the name migration for any bundled settings domain', () => {
    // The maven domain is edited on the Repositories page, so it has no link of its own in the tree.
    const bundled = ['javadoc', 'cleanup', 'web', 'frontend', 'statistics', 'audit', 'search']

    const domainLinks = adminGroups(instanceAdmin, { domains: [...bundled, 'maven'] })
      .flatMap(group => group.links)
      .filter(link => link.to.name === 'settings')

    expect(domainLinks.map(link => link.to.params.domain).sort()).toEqual([...bundled].sort())
    expect(domainLinks.map(link => link.to.params.domain)).not.toContain('migration')
  })
})

describe('the route of the Migration page', () => {
  it('uses the instance admin node, so the route guard and the page agree with the server', () => {
    const route = ROUTES.find(entry => entry.name === 'settings-migration')

    expect(route.path).toBe('/settings/migration/:step?')
    expect(route.meta.node).toBe(INSTANCE_ADMIN_NODE)
    expect(route.meta.manager).toBe(true)
  })

  it('is placed ahead of the generic settings route, which would take migration as a domain', () => {
    const names = ROUTES.map(route => route.name)

    expect(names, 'the Migration route exists').toContain('settings-migration')
    expect(names.indexOf('settings-migration')).toBeLessThan(names.indexOf('settings'))
  })

  it('keeps every existing settings address as it was', () => {
    const settings = ROUTES.find(route => route.name === 'settings')
    const plugin = ROUTES.find(route => route.name === 'settings-plugin')
    const entry = ROUTES.find(route => route.name === 'settings-entry')

    expect(settings.path).toBe('/settings/:domain?')
    expect(plugin.path).toBe('/settings/plugins/:plugin/:page?')
    expect(entry.path).toBe('/settings/:domain/:collection/:entry')
  })
})
