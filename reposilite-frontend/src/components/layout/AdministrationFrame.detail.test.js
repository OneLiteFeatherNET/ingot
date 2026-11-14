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
  { name: 'access-user', path: '/access/users/:name', component: shell },
  { name: 'settings', path: '/settings/:domain?', component: shell }
]

// The tree of one list page, with the description the list shows under its heading. The fixture is built per test.
const usersGroups = () => [
  {
    id: 'Security',
    heading: 'Access',
    links: [{
      key: 'page:users',
      label: 'Users',
      description: 'People who sign in, with their permissions and tokens.',
      to: { name: 'access-users' }
    }]
  }
]

// The label of a crumb: its text without the separator after it, which is decorative and aria-hidden.
const crumbLabel = (crumb) => {
  const copy = crumb.element.cloneNode(true)
  copy.querySelectorAll('[aria-hidden="true"]').forEach(node => node.remove())
  return copy.textContent.trim()
}

const mountDetail = async (props = {}) => {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/access/users/alice')
  await router.isReady()
  return mount(AdministrationFrame, {
    props: {
      label: 'Administration',
      sectionLabel: 'Administration',
      groups: usersGroups(),
      indexTo: { name: 'settings' },
      current: 'page:users',
      title: 'alice',
      detail: true,
      description: undefined,
      ...props
    },
    slots: { default: '<p data-test="page">page content</p>' },
    global: { plugins: [router] }
  })
}

const headerLinksTo = (wrapper, path) => wrapper.findAll('header a').filter(link => link.attributes('href') === path)

// A detail page names its item in the one heading. Its list is its way back: the back arrow below 1240 pixels, the list
// crumb beside the page from 1240 pixels. The header never offers a second way back, and no list description is reused.
describe('AdministrationFrame on a detail page', () => {
  it('draws no list description under the heading of a detail page', async () => {
    const wrapper = await mountDetail()

    expect(wrapper.get('h1').text(), 'the heading is the item name').toBe('alice')
    expect(wrapper.text(), 'the description of the list is not reused on the detail').not.toContain('People who sign in, with their permissions and tokens.')
  })

  it('draws the description given for the item under the heading of a detail page', async () => {
    const wrapper = await mountDetail({ description: 'A user of this instance.' })

    expect(wrapper.text()).toContain('A user of this instance.')
  })

  it('draws the list as the back arrow below 1240 pixels, and no arrow to the index', async () => {
    const wrapper = await mountDetail({ narrow: true })

    expect(wrapper.find('[aria-label="Back to Administration"]').exists(), 'no arrow to the index').toBe(false)
    const back = wrapper.get('[aria-label="Back to Users"]')
    expect(back.attributes('href'), 'the arrow leads to the list').toBe('/access/users')
  })

  it('draws the list crumb as plain text beside the arrow below 1240 pixels, so the arrow is the only link to the list', async () => {
    const wrapper = await mountDetail({ narrow: true })

    const crumbs = wrapper.findAll('nav li')
    expect(crumbs.map(crumbLabel), 'only the list crumb is drawn').toEqual(['Users'])
    expect(crumbs[0].find('a').exists(), 'the crumb is not a second link').toBe(false)
  })

  it('draws exactly one link to the list in the header below 1240 pixels', async () => {
    const wrapper = await mountDetail({ narrow: true })

    expect(headerLinksTo(wrapper, '/access/users'), 'the arrow').toHaveLength(1)
  })

  it('draws exactly one link to the list in the header from 1240 pixels, as the list crumb', async () => {
    const wrapper = await mountDetail({ narrow: false })

    expect(headerLinksTo(wrapper, '/access/users'), 'the list crumb').toHaveLength(1)
    expect(wrapper.find('[aria-label="Back to Users"]').exists(), 'no arrow on a wide page').toBe(false)
  })
})
