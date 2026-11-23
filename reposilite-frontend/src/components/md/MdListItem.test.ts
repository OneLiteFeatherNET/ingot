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
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MdList from './MdList.vue'
import MdListItem from './MdListItem.vue'

describe('MdListItem selection', () => {
  it('marks the selected row with aria-current true and no other row', () => {
    const wrapper = mount(MdList, {
      props: { selectable: true },
      slots: {
        default: () => [
          h(MdListItem, { selected: true }, () => 'Maven'),
          h(MdListItem, { selected: false }, () => 'Npm'),
          h(MdListItem, { selected: false }, () => 'Docker')
        ]
      }
    })

    const current = wrapper.findAll('[aria-current="true"]')
    expect(current, 'exactly one row is the current row').toHaveLength(1)
    expect(current[0].text(), 'the current row is the selected one').toBe('Maven')
  })

  it('draws no aria-current on a row that is not selected', () => {
    const wrapper = mount(MdList, {
      props: { selectable: true },
      slots: { default: () => [h(MdListItem, { selected: false }, () => 'Npm')] }
    })

    expect(wrapper.get('li').attributes('aria-current'), 'an unselected row is not the current row').toBeUndefined()
  })
})

describe('MdListItem row geometry', () => {
  it('gives a two-line row a 56 pixel minimum height by default', () => {
    const wrapper = mount(MdList, {
      slots: { default: () => h(MdListItem, () => 'Two lines of text') }
    })

    expect(wrapper.get('[data-test="list-item-row"]').classes(), 'a two-line row is at least 56 pixels high').toContain('min-h-14')
  })

  it('gives a one-line row a 48 pixel minimum height', () => {
    const wrapper = mount(MdList, {
      slots: { default: () => h(MdListItem, { lines: 1 }, () => 'One line') }
    })

    const row = wrapper.get('[data-test="list-item-row"]').classes()
    expect(row, 'a one-line row is at least 48 pixels high').toContain('min-h-12')
    expect(row, 'a one-line row is not the two-line height').not.toContain('min-h-14')
  })
})

describe('MdListItem state layer', () => {
  it('draws no state layer on a plain text row, since nothing happens on a click', () => {
    const wrapper = mount(MdList, {
      slots: { default: () => h(MdListItem, () => 'Read only') }
    })

    expect(wrapper.get('[data-test="list-item-row"]').classes(), 'a plain row does not react to hover').not.toContain('md-state-layer')
  })

  it('draws the state layer on a button row', () => {
    const wrapper = mount(MdList, {
      slots: { default: () => h(MdListItem, { button: true }, () => 'Open') }
    })

    expect(wrapper.get('[data-test="list-item-row"]').classes(), 'a button row shows hover, focus and press').toContain('md-state-layer')
  })

  it('draws the state layer on an option of a selectable list', () => {
    const wrapper = mount(MdList, {
      props: { selectable: true },
      slots: { default: () => h(MdListItem, () => 'Maven') }
    })

    expect(wrapper.get('[data-test="list-item-row"]').classes(), 'an option shows hover, focus and press').toContain('md-state-layer')
  })
})

describe('MdListItem selected link row', () => {
  const mountLinks = async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/:name', component: { render: () => null } }]
    })
    await router.push('/maven')
    await router.isReady()
    const wrapper = mount(MdList, {
      global: { plugins: [router] },
      slots: {
        default: () => [
          h(MdListItem, { to: '/maven', selected: true }, () => 'Maven'),
          h(MdListItem, { to: '/npm', selected: false }, () => 'Npm')
        ]
      }
    })
    await flushPromises()
    return wrapper
  }

  it('marks the link of the selected row as current, and nothing else', async () => {
    const wrapper = await mountLinks()

    const current = wrapper.findAll('[aria-current="true"]')
    expect(current, 'exactly one element is current').toHaveLength(1)
    expect(current[0].element.tagName, 'the link carries the state, so it is announced with the link').toBe('A')
    expect(current[0].text(), 'the current link is the selected row').toBe('Maven')
  })

  it('draws the selected container on the selected link row only', async () => {
    const wrapper = await mountLinks()

    const rows = wrapper.findAll('li')
    expect(rows[0].classes(), 'the selected row takes the secondary container').toContain('bg-secondary-container')
    expect(rows[1].classes(), 'an unselected row keeps the list surface').not.toContain('bg-secondary-container')
  })
})

describe('MdListItem tight gap', () => {
  it('keeps 16 pixels between the slots by default', () => {
    const wrapper = mount(MdList, {
      slots: { default: () => h(MdListItem, () => 'Text') }
    })

    const row = wrapper.get('[data-test="list-item-row"]').classes()
    expect(row, 'the slots are 16 pixels apart').toContain('gap-4')
    expect(row, 'the gap does not narrow on a phone').not.toContain('gap-2')
  })

  it('narrows the gap to 8 pixels below the medium window when the row is tight', () => {
    const wrapper = mount(MdList, {
      slots: { default: () => h(MdListItem, { tight: true }, () => 'Text') }
    })

    const row = wrapper.get('[data-test="list-item-row"]').classes()
    expect(row, 'a tight row has 8 pixels between the slots on a phone').toContain('gap-2')
    expect(row, 'a tight row has 16 pixels again from the medium window').toContain('medium:gap-4')
    expect(row, 'a tight row does not keep the wide gap on a phone').not.toContain('gap-4')
  })
})
