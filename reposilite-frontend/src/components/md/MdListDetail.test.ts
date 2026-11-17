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
import MdListDetail from './MdListDetail.vue'

describe('MdListDetail', () => {
  it('renders the list slot and the detail slot', () => {
    const wrapper = mount(MdListDetail, {
      slots: {
        list: () => h('p', { 'data-test': 'the-list' }, 'List'),
        detail: () => h('p', { 'data-test': 'the-detail' }, 'Detail')
      }
    })

    expect(wrapper.get('[data-test="the-list"]').text()).toBe('List')
    expect(wrapper.get('[data-test="the-detail"]').text()).toBe('Detail')
  })

  it('switches to two columns at the expanded breakpoint by default', () => {
    const wrapper = mount(MdListDetail)

    expect(wrapper.attributes('data-breakpoint')).toBe('expanded')
    expect(wrapper.classes()).toContain('expanded:grid-cols-5')
  })

  it('switches at the medium breakpoint when breakpoint is medium', () => {
    const wrapper = mount(MdListDetail, { props: { breakpoint: 'medium' } })

    expect(wrapper.attributes('data-breakpoint')).toBe('medium')
    expect(wrapper.classes()).toContain('medium:grid-cols-5')
    expect(wrapper.classes()).not.toContain('expanded:grid-cols-5')
  })

  it('stacks the two slots in one column below the breakpoint', () => {
    const wrapper = mount(MdListDetail)

    expect(wrapper.classes()).toContain('grid-cols-1')
  })

  it('carries the md-list-detail anchor on its root', () => {
    expect(mount(MdListDetail).attributes('data-ingot-component')).toBe('md-list-detail')
  })
})
