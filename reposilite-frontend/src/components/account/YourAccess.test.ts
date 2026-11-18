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

const mountSection = (router: Router): VueWrapper => mount(YourAccess, {
  props: { access: accessView(SUMMARY), principalName: 'alice' },
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

  it('opens the how-to page in a new tab, with a name that says so, for an allowed right', async () => {
    wrapper = mountSection(routerBelow('/repo/'))
    await flushPromises()

    const link = wrapper.find('[data-test="allowed-permission"] a')
    expect(link.attributes('href'), 'the external how-to page').toContain('check-access')
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')).toBe('noopener')
    expect(link.attributes('aria-label')).toBe('Explain ingot.admin.users (opens in a new tab)')
  })

  it('opens the how-to page for a denied right too, so every principal gets the same target', async () => {
    wrapper = mountSection(routerBelow('/repo/'))
    await flushPromises()

    const link = wrapper.find('[data-test="denied-permission"] a')
    expect(link.attributes('href'), 'a denied right links to the how-to page').toContain('check-access')
    expect(link.attributes('target'), 'the how-to page opens in a new tab').toBe('_blank')
  })

  it('never routes an Explain link into the dashboard, so the access check is not a target of the account page', async () => {
    wrapper = mountSection(routerBelow('/repo/'))
    await flushPromises()

    const hrefs = wrapper.findAll('a').map(link => link.attributes('href') ?? '')
    expect(hrefs.length, 'the section has Explain links').toBeGreaterThan(0)
    hrefs.forEach(href => expect(href, 'no link to the access check').not.toMatch(/\/access\/check(\?|$)/))
  })

  it('draws the card through MdCard in the low variant', async () => {
    wrapper = mountSection(routerBelow('/repo/'))
    await flushPromises()

    const card = wrapper.find('[data-variant="low"]')
    expect(card.exists(), 'the access card is drawn by MdCard in the low variant').toBe(true)
    expect(card.classes(), 'a flat tinted panel on the page').toContain('bg-surface-container-low')
    expect(card.find('[data-test="team"]').exists(), 'the teams sit inside the card').toBe(true)
  })

  it('takes the default padding and the medium corner of MdCard', async () => {
    wrapper = mountSection(routerBelow('/repo/'))
    await flushPromises()

    const card = wrapper.find('[data-variant="low"]')
    expect(card.classes(), 'the inset is the card padding').toContain('p-4')
    expect(card.classes(), 'the corner is the medium one').toContain('rounded-corner-medium')
    expect(card.classes()).not.toContain('rounded-corner-large')
  })
})
