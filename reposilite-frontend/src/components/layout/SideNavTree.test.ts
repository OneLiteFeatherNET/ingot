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
import SideNavTree from './SideNavTree.vue'

const shell = { render: () => null }

const routes = [
  { name: 'access-users', path: '/access/users', component: shell },
  { name: 'access-sso', path: '/access/sso', component: shell },
  { name: 'access-audit', path: '/access/audit', component: shell }
]

// The fixture is built per test, so no test can change what another one reads.
const fixtureGroups = () => [
  {
    heading: 'Accounts',
    links: [{ key: 'users', label: 'Users', to: { name: 'access-users' } }]
  },
  {
    heading: 'Sign in',
    links: [{ key: 'sso', label: 'SSO', to: { name: 'access-sso' } }]
  },
  {
    heading: 'Monitoring and support',
    links: [{ key: 'audit', label: 'Audit log', to: { name: 'access-audit' } }]
  }
]

describe('SideNavTree', () => {
  const mountTree = async (groups = fixtureGroups()) => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/access/users')
    await router.isReady()
    return mount(SideNavTree, {
      props: { label: 'Access sections', groups, current: 'users', described: false },
      global: { plugins: [router] }
    })
  }

  // The wrapper of a group is the element that holds its heading and its list.
  const groupWrappers = (wrapper) => wrapper.findAll('ul[aria-labelledby]').map(list => list.element.parentElement)
  const headingOf = (wrapper, text) => wrapper.findAll('p').find(paragraph => paragraph.text() === text)

  it('draws each group heading in the title-small style in the primary colour', async () => {
    const wrapper = await mountTree()

    const heading = headingOf(wrapper, 'Accounts')

    expect(heading.classes(), 'the heading uses the title-small type role').toContain('md-title-small')
    expect(heading.classes(), 'the heading is set in the primary colour').toContain('text-primary')
    expect(heading.classes(), 'the heading is no longer a label-sized grey').not.toContain('md-label-medium')
  })

  it('keeps the id of each group heading as the name of its list', async () => {
    const wrapper = await mountTree()

    const names = wrapper.findAll('ul[aria-labelledby]').map(list => wrapper.get(`#${list.attributes('aria-labelledby')}`).text())

    expect(names, 'every list is named by the visible text of its heading').toEqual(['Accounts', 'Sign in', 'Monitoring and support'])
  })

  it('draws no divider above the first group', async () => {
    const wrapper = await mountTree()

    const [first] = groupWrappers(wrapper)

    expect(first.classList, 'the first group starts the tree without a rule').not.toContain('border-t')
  })

  it('draws a divider in the outline-variant colour above every group after the first', async () => {
    const wrapper = await mountTree()

    const [, second, third] = groupWrappers(wrapper)

    expect(second.classList, 'the second group has a rule above it').toContain('border-t')
    expect(second.classList, 'the rule uses the outline-variant colour').toContain('border-outline-variant')
    expect(third.classList, 'the third group has a rule above it').toContain('border-t')
  })

  it('adds no extra item for the divider, so a screen reader reads the same list of groups', async () => {
    const wrapper = await mountTree()

    expect(wrapper.findAll('hr, [role="separator"]'), 'the divider is decorative, not a separator element').toEqual([])
  })
})
