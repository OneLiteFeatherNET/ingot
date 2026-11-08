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
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import SideNavLayout from './SideNavLayout.vue'

const shell = { render: () => null }

const routes = [
  { name: 'access', path: '/access', component: shell },
  { name: 'access-users', path: '/access/users', component: shell },
  { name: 'access-machines', path: '/access/machines', component: shell },
  { name: 'access-teams', path: '/access/teams', component: shell },
  { name: 'access-sso', path: '/access/sso', component: shell },
  { name: 'access-ldap', path: '/access/ldap', component: shell }
]

// The fixture is built per test, so no test can change what another one reads.
const fixtureGroups = () => [
  {
    heading: 'Accounts',
    links: [
      { key: 'users', label: 'Users', to: { name: 'access-users' } },
      { key: 'machines', label: 'Machine accounts', to: { name: 'access-machines' } },
      { key: 'teams', label: 'Teams', to: { name: 'access-teams' } }
    ]
  },
  {
    heading: 'Sign in',
    links: [
      { key: 'sso', label: 'SSO', to: { name: 'access-sso' } },
      { key: 'ldap', label: 'LDAP', to: { name: 'access-ldap' } }
    ]
  }
]

const SIDE_NAV = '[aria-label="Access sections"]'

describe('SideNavLayout', () => {
  // A fresh router per test, on memory history, opened at the address of the page under test.
  const mountLayout = async (props = {}, url = '/access/users') => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push(url)
    await router.isReady()
    return mount(SideNavLayout, {
      props: {
        label: 'Access sections',
        sectionLabel: 'Access',
        groups: fixtureGroups(),
        indexTo: { name: 'access' },
        ...props
      },
      slots: { default: '<p class="page-body">Page body</p>' },
      global: { plugins: [router] }
    })
  }

  it('renders the links of each group under the heading of that group', async () => {
    const wrapper = await mountLayout({ current: 'users' })

    const groups = wrapper.findAll('[role="group"]')

    expect(groups.map(group => group.attributes('aria-label')), 'one labelled group per heading').toEqual(['Accounts', 'Sign in'])
    expect(groups[0].findAll('a').map(link => link.text())).toEqual(['Users', 'Machine accounts', 'Teams'])
    expect(groups[1].findAll('a').map(link => link.text())).toEqual(['SSO', 'LDAP'])
  })

  it('marks only the current link with aria-current page', async () => {
    const wrapper = await mountLayout({ current: 'machines' })

    const marks = wrapper.findAll(`${SIDE_NAV} a`).map(link => link.attributes('aria-current') ?? null)

    expect(marks, 'the link of the open page is the only one marked').toEqual([null, 'page', null, null, null])
  })

  it('makes each link a real link to the address of its route', async () => {
    const wrapper = await mountLayout({ current: 'users' })

    const hrefs = wrapper.findAll(`${SIDE_NAV} a`).map(link => link.attributes('href'))

    expect(hrefs, 'every link is an address the browser can open in a new tab').toEqual([
      '/access/users', '/access/machines', '/access/teams', '/access/sso', '/access/ldap'
    ])
  })

  it('shows the side navigation and the page side by side on wide widths', async () => {
    const wrapper = await mountLayout({ current: 'users', narrow: false })

    expect(wrapper.find(SIDE_NAV).exists(), 'the side navigation is shown').toBe(true)
    expect(wrapper.find('.page-body').exists(), 'the page is shown').toBe(true)
  })

  it('shows the page without the side navigation on narrow widths when a page is open', async () => {
    const wrapper = await mountLayout({ current: 'users', narrow: true })

    expect(wrapper.find(SIDE_NAV).exists(), 'the side navigation is replaced by the back arrow').toBe(false)
    expect(wrapper.find('.page-body').exists(), 'the page is shown').toBe(true)
  })

  it('shows the index of the groups on narrow widths when no page is open', async () => {
    const wrapper = await mountLayout({ current: undefined, narrow: true })

    const indexLinks = wrapper.findAll(`${SIDE_NAV} a`).map(link => link.text())

    expect(indexLinks, 'the index lists the links of every group').toEqual(['Users', 'Machine accounts', 'Teams', 'SSO', 'LDAP'])
    expect(wrapper.find('.page-body').exists(), 'no page is drawn under the index').toBe(false)
  })

  it('gives a back arrow on narrow widths that returns to the index', async () => {
    const wrapper = await mountLayout({ current: 'users', narrow: true })

    const back = wrapper.find('[aria-label="Back to Access"]')

    expect(back.exists(), 'a page below the index has a back arrow').toBe(true)
    expect(back.attributes('href'), 'the arrow leads to the index address').toBe('/access')
  })

  it('shows no back arrow on wide widths', async () => {
    const wrapper = await mountLayout({ current: 'users', narrow: false })

    expect(wrapper.find('[aria-label="Back to Access"]').exists()).toBe(false)
  })

  it('names the section and the open page in the breadcrumb', async () => {
    const wrapper = await mountLayout({ current: 'teams', narrow: false })

    const crumbs = wrapper.findAll('[aria-label="Breadcrumb"] li').map(item => item.text())

    expect(crumbs, 'the breadcrumb reads section then page').toEqual(['Access', 'Teams'])
  })

  it('shows no breadcrumb when the open page is not one of the links', async () => {
    const wrapper = await mountLayout({ current: 'check', narrow: false })

    expect(wrapper.find('[aria-label="Breadcrumb"]').exists(), 'a page outside the groups has no crumb of its own').toBe(false)
  })
})
