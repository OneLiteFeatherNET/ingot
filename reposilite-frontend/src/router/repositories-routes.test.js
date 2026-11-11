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
import { createMemoryHistory } from 'vue-router'
import { createAppRouter } from './create'
import { legacyLocation } from './legacy'
import { ROUTES } from './routes'
import { ADMIN_NODES } from '../store/nodeAccess'

// A fresh router per test, on memory history. The defaults describe an anonymous visitor.
const shell = { render: () => null }
const makeRouter = (options = {}) => createAppRouter({
  history: createMemoryHistory(),
  component: shell,
  legacy: legacyLocation,
  siteTitle: 'Ingot Repository',
  ...options
})

const administrator = () => true

describe('the repository routes', () => {
  it('resolves /settings/repositories to the list, before the generic settings route', async () => {
    const router = makeRouter({ can: administrator, isManager: administrator })

    await router.push('/settings/repositories')

    expect(router.currentRoute.value.name).toBe('settings-repositories')
    expect(router.currentRoute.value.meta).toMatchObject({ section: 'settings', manager: true, node: ADMIN_NODES.settings })
  })

  it('resolves /settings/repositories/:id to the detail with the id as a parameter', async () => {
    const router = makeRouter({ can: administrator, isManager: administrator })

    await router.push('/settings/repositories/releases')

    expect(router.currentRoute.value.name).toBe('settings-repository')
    expect(router.currentRoute.value.params.id).toBe('releases')
    expect(router.currentRoute.value.meta.section).toBe('settings')
  })

  it('reads an id with dots, underscores and hyphens as one id', async () => {
    const router = makeRouter({ can: administrator, isManager: administrator })

    await router.push('/settings/repositories/team.a_b-c')

    expect(router.currentRoute.value.params.id).toBe('team.a_b-c')
  })

  it('redirects the old address of a repository to the detail, and keeps the query', async () => {
    const router = makeRouter({ can: administrator, isManager: administrator })

    await router.push('/settings/maven/repositories/releases?tab=access')

    expect(router.currentRoute.value.fullPath).toBe('/settings/repositories/releases?tab=access')
    expect(router.currentRoute.value.name).toBe('settings-repository')
  })

  it('redirects the old wizard address to the new list, and keeps the query', async () => {
    const router = makeRouter({ can: administrator, isManager: administrator })

    await router.push('/settings/maven?new=repository')

    expect(router.currentRoute.value.fullPath).toBe('/settings/repositories?new=repository')
    expect(router.currentRoute.value.name).toBe('settings-repositories')
  })

  it('does not redirect an entry of another collection', async () => {
    const router = makeRouter({ can: administrator, isManager: administrator })

    await router.push('/settings/cleanup/policies/nightly')

    expect(router.currentRoute.value.fullPath).toBe('/settings/cleanup/policies/nightly')
    expect(router.currentRoute.value.name).toBe('settings-entry')
  })

  it('does not redirect the maven domain page', async () => {
    const router = makeRouter({ can: administrator, isManager: administrator })

    await router.push('/settings/maven')

    expect(router.currentRoute.value.fullPath).toBe('/settings/maven')
  })
})

describe('the route names of the dashboard before this change', () => {
  // Every name and path that existed before the Administration regrouping. Each must still resolve to the same name.
  const before = [
    ['overview', '/'],
    ['search', '/search'],
    ['account', '/account'],
    ['dashboard', '/dashboard'],
    ['analytics', '/analytics'],
    ['access', '/access'],
    ['access-users', '/access/users'],
    ['access-user', '/access/users/:name'],
    ['access-machines', '/access/machines'],
    ['access-machine', '/access/machines/:name'],
    ['access-check', '/access/check'],
    ['access-teams', '/access/teams'],
    ['access-team', '/access/teams/:name'],
    ['access-sso', '/access/sso'],
    ['access-ldap', '/access/ldap'],
    ['access-security', '/access/security'],
    ['console', '/console'],
    ['maintenance', '/maintenance'],
    ['audit', '/audit'],
    ['settings', '/settings/:domain?'],
    ['settings-entry', '/settings/:domain/:collection/:entry'],
  ]

  it.each(before)('keeps the route %s at %s', (name, path) => {
    const route = ROUTES.find(candidate => candidate.name === name)
    expect(route, `route ${name}`).toBeDefined()
    expect(route.path).toBe(path)
  })
})

describe('the settings address with one page open to the visitor', () => {
  it('forwards /settings to the one page the visitor may open', async () => {
    const audit = (node) => node === ADMIN_NODES.audit
    const router = makeRouter({ can: audit, isManager: () => false })

    await router.push('/settings')

    expect(router.currentRoute.value.fullPath).toBe('/audit')
  })
})
