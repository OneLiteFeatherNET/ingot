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
import AdministrationFrame from './AdministrationFrame.vue'

const shell = { render: () => null }

const routes = [
  { name: 'access-users', path: '/access/users', component: shell },
  { name: 'audit', path: '/audit', component: shell },
  { name: 'settings', path: '/settings/:domain?', component: shell }
]

// The fixture is built per test, so no test can change what another one reads.
const groups = () => [
  {
    heading: 'Security',
    links: [
      { key: 'page:users', label: 'Users', to: { name: 'access-users' } },
      { key: 'page:audit', label: 'Audit log', to: { name: 'audit' } }
    ]
  },
  {
    heading: 'System',
    links: [{ key: 'settings:audit', label: 'Audit settings', to: { name: 'settings', params: { domain: 'audit' } } }]
  }
]

const mountFrame = async (props, url = '/audit') => {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(url)
  await router.isReady()
  return mount(AdministrationFrame, {
    props: {
      description: 'Who did what on this instance.',
      label: 'Administration',
      sectionLabel: 'Administration',
      groups: groups(),
      indexTo: { name: 'settings' },
      ...props
    },
    slots: { default: '<p data-test="page">page content</p>' },
    global: { plugins: [router] }
  })
}

describe('AdministrationFrame', () => {
  it('draws one heading named Administration, whatever the page', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    expect(wrapper.findAll('h1').map(heading => heading.text()), 'the one h1 of the frame').toEqual(['Administration'])
  })

  it('draws the description of the page under the heading', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', description: 'Who did what on this instance.' })

    expect(wrapper.text()).toContain('Who did what on this instance.')
  })

  it('draws the page content beside the side navigation', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    expect(wrapper.find('[data-test="page"]').exists(), 'the slot is drawn').toBe(true)
    expect(wrapper.find('[aria-label="Administration"]').exists(), 'the side navigation is drawn').toBe(true)
  })

  it('marks only the current entry with aria-current page', async () => {
    const wrapper = await mountFrame({ current: 'settings:audit' }, '/settings/audit')

    const marked = wrapper.findAll('[aria-label="Administration"] a').filter(link => link.attributes('aria-current') === 'page')
    expect(marked.map(link => link.text()), 'exactly one entry is current').toEqual(['Audit settings'])
  })

  it('names the current entry in the last crumb of the breadcrumb', async () => {
    const wrapper = await mountFrame({ current: 'settings:audit' }, '/settings/audit')

    const crumbs = wrapper.findAll('[aria-label="Breadcrumb"] li').map(crumb => crumb.text())
    expect(crumbs, 'the first crumb is the section, the last one the page shown').toEqual(['Administration', 'Audit settings'])
  })

  it('draws no side navigation on a page without entries, such as the hub', async () => {
    const wrapper = await mountFrame({ groups: [], current: undefined }, '/settings')

    expect(wrapper.find('[aria-label="Administration"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="page"]').exists(), 'the page is still drawn').toBe(true)
  })
})
