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
