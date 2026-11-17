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
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MdList from './MdList.vue'
import MdListItem from './MdListItem.vue'

const shell = { render: () => null }

// The router is built per test with the dashboard base, so a link that is not a RouterLink would lose the base.
const routerAt = (base: string) =>
  createRouter({
    history: createMemoryHistory(base),
    routes: [{ name: 'repository', path: '/repositories/:id', component: shell }]
  })

describe('MdList', () => {
  it('renders a ul whose items are li elements', () => {
    const wrapper = mount(MdList, {
      slots: { default: () => [h(MdListItem, () => 'First'), h(MdListItem, () => 'Second')] }
    })

    expect(wrapper.element.tagName).toBe('UL')
    expect(wrapper.findAll('li')).toHaveLength(2)
  })

  it('renders a link item as an anchor to the route under the dashboard base', async () => {
    const router = routerAt('/repo/')
    await router.push('/repositories/releases')
    const wrapper = mount(MdList, {
      slots: { default: () => h(MdListItem, { to: { name: 'repository', params: { id: 'snapshots' } } }, () => 'Snapshots') },
      global: { plugins: [router] }
    })

    expect(wrapper.get('a').attributes('href')).toBe('/repo/repositories/snapshots')
  })

  it('renders a button item with type button', () => {
    const wrapper = mount(MdList, {
      slots: { default: () => h(MdListItem, { button: true }, () => 'Delete') }
    })

    expect(wrapper.get('button').attributes('type')).toBe('button')
  })

  it('shows the leading and trailing slots around the item text', () => {
    const wrapper = mount(MdList, {
      slots: {
        default: () =>
          h(MdListItem, null, {
            leading: () => h('span', { 'data-test': 'leading' }, 'L'),
            default: () => h('span', 'Headline'),
            trailing: () => h('span', { 'data-test': 'trailing' }, 'T')
          })
      }
    })

    expect(wrapper.get('[data-test="leading"]').text()).toBe('L')
    expect(wrapper.get('[data-test="trailing"]').text()).toBe('T')
  })

  it('draws a divider rule under each item except the first', () => {
    const wrapper = mount(MdList, {
      props: { divided: true },
      slots: { default: () => [h(MdListItem, () => 'First'), h(MdListItem, () => 'Second')] }
    })

    expect(wrapper.attributes('data-divided')).toBe('true')
    expect(wrapper.findAll('[data-test="item-rule"]')).toHaveLength(2)
  })

  it('places the rule at the 72px inset when inset is 72', () => {
    const wrapper = mount(MdList, {
      props: { divided: true, inset: 72 },
      slots: { default: () => [h(MdListItem, () => 'First'), h(MdListItem, () => 'Second')] }
    })

    expect(wrapper.findAll('[data-test="item-rule"]')[0].classes()).toContain('start-md-inset-72')
  })

  it('exposes a listbox of options when selectable', () => {
    const wrapper = mount(MdList, {
      props: { selectable: true },
      slots: { default: () => [h(MdListItem, () => 'Maven'), h(MdListItem, () => 'Npm')] }
    })

    expect(wrapper.attributes('role')).toBe('listbox')
    expect(wrapper.findAll('[role="option"]')).toHaveLength(2)
  })

  it('carries the md-list anchor on its root', () => {
    expect(mount(MdList).attributes('data-ingot-component')).toBe('md-list')
  })
})

describe('MdListItem', () => {
  it('reports the selected option through aria-selected', () => {
    const wrapper = mount(MdList, {
      props: { selectable: true },
      slots: { default: () => [h(MdListItem, { selected: true }, () => 'Maven')] }
    })

    expect(wrapper.get('[role="option"]').attributes('aria-selected')).toBe('true')
  })

  it('emits select when a selectable option is clicked', async () => {
    const wrapper = mount(MdList, {
      props: { selectable: true },
      slots: { default: () => [h(MdListItem, () => 'Maven')] }
    })

    await wrapper.get('[role="option"]').trigger('click')

    expect(wrapper.findComponent(MdListItem).emitted('select')).toHaveLength(1)
  })

  it('emits select when Enter is pressed on a selectable option', async () => {
    const wrapper = mount(MdList, {
      props: { selectable: true },
      slots: { default: () => [h(MdListItem, () => 'Maven')] }
    })

    await wrapper.get('[role="option"]').trigger('keydown', { key: 'Enter' })

    expect(wrapper.findComponent(MdListItem).emitted('select')).toHaveLength(1)
  })

  it('leaves the Enter key of a button row to the browser when the list is not selectable', () => {
    const wrapper = mount(MdList, {
      slots: { default: () => h(MdListItem, { button: true }, () => 'Delete') }
    })
    const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })

    wrapper.get('button').element.dispatchEvent(enter)

    expect(enter.defaultPrevented).toBe(false)
  })

  it('carries the md-list-item anchor on its root', () => {
    const wrapper = mount(MdList, { slots: { default: () => h(MdListItem, () => 'Item') } })

    expect(wrapper.get('li').attributes('data-ingot-component')).toBe('md-list-item')
  })
})
