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
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createRouter, createWebHistory, type Router } from 'vue-router'
import YourAccess from './YourAccess.vue'
import { accessView, type OwnAccessSummary } from './accessView'

const SUMMARY: OwnAccessSummary = {
  principal: { name: 'alice', displayName: 'Alice Example', kind: 'USER', providers: ['local'] },
  instanceAdmin: false,
  teams: [{ name: 'developers', parents: [] }],
  nodes: ['ingot.admin.users'],
  denials: [{ node: 'ingot.admin.teams', overridden: false }],
  grants: [],
  tokenNarrowed: false
}

// The router keeps the base of the dashboard, as it does when the dashboard is served below a sub-path.
const routerBelow = (base: string): Router => createRouter({
  history: createWebHistory(base),
  routes: [
    { path: '/access/check', name: 'access-check', component: { render: () => null } },
    { path: '/:rest(.*)', component: { render: () => null } }
  ]
})

const mountSection = (router: Router, mayCheck: boolean): VueWrapper => mount(YourAccess, {
  props: { access: accessView(SUMMARY), principalName: 'alice', mayCheck },
  global: { plugins: [router] },
  attachTo: document.body
}) as VueWrapper

describe('YourAccess Explain links', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  it('routes the check link through the router, so it keeps the base of a sub-path deployment', async () => {
    const router = routerBelow('/repo/')
    wrapper = mountSection(router, true)
    await flushPromises()

    const link = wrapper.find('[data-test="allowed-permission"] a')
    expect(link.attributes('href'), 'the href carries the base and the question').toBe('/repo/access/check?principal=alice&node=ingot.admin.users')
  })

  it('moves inside the dashboard on click, without a page reload', async () => {
    const router = routerBelow('/repo/')
    wrapper = mountSection(router, true)
    await flushPromises()

    await wrapper.find('[data-test="allowed-permission"] a').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name, 'the router changed the route').toBe('access-check')
    expect(router.currentRoute.value.query).toEqual({ principal: 'alice', node: 'ingot.admin.users' })
  })

  it('opens the how-to page in a new tab, with a name that says so, for a principal who may not check', async () => {
    const router = routerBelow('/repo/')
    wrapper = mountSection(router, false)
    await flushPromises()

    const link = wrapper.find('[data-test="allowed-permission"] a')
    expect(link.attributes('href'), 'the external how-to page').toContain('check-access')
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')).toBe('noopener')
    expect(link.attributes('aria-label')).toBe('Explain ingot.admin.users (opens in a new tab)')
  })

  it('routes a denied right of a checking principal through the router too', async () => {
    const router = routerBelow('/repo/')
    wrapper = mountSection(router, true)
    await flushPromises()

    const link = wrapper.find('[data-test="denied-permission"] a')
    expect(link.attributes('href'), 'a denied right of a checking principal is still a check').toMatch(/^\/repo\/access\/check/)
    expect(link.attributes('target'), 'the check stays in the dashboard').toBeUndefined()
  })
})
