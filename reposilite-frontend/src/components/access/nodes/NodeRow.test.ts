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
import { defineComponent, h } from 'vue'
import MdList from '../../md/MdList.vue'
import MdListItem from '../../md/MdListItem.vue'
import NodeRow from './NodeRow.vue'

const mountRow = (props = {}) => mount(
  defineComponent({
    render: () => h(MdList, { divided: true }, () => h(NodeRow, { entry: { node: 'ingot.admin.users', allowed: true }, ...props }))
  })
)

describe('NodeRow', () => {
  it('is a list item of the list it sits in', () => {
    const wrapper = mountRow()

    expect(wrapper.findComponent(MdListItem).exists()).toBe(true)
    expect(wrapper.find('ul > li').exists()).toBe(true)
  })

  it('keeps the node, the choice and the remove button in the row', () => {
    const wrapper = mountRow()

    expect(wrapper.find('li code').text()).toBe('ingot.admin.users')
    expect(wrapper.findAll('li [role="radio"]')).toHaveLength(2)
    expect(wrapper.find('li [data-test="remove-node"]').exists()).toBe(true)
  })

  it('names the node on the row so the editor can find and focus it', () => {
    const wrapper = mountRow({ highlighted: true })
    const li = wrapper.find('li')

    expect(li.attributes('data-node')).toBe('ingot.admin.users')
    expect(li.attributes('data-allowed')).toBe('true')
    expect(li.attributes('tabindex')).toBe('-1')
    expect(li.attributes('data-highlighted')).toBe('true')
  })

  it('shows the state as text and no controls in a read only row', () => {
    const wrapper = mountRow({ readonly: true })

    expect(wrapper.find('[data-test="row-state"]').text()).toBe('Allowed')
    expect(wrapper.find('[role="radio"]').exists()).toBe(false)
  })
})
