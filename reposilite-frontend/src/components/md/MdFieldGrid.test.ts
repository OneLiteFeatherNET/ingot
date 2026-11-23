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
import MdFieldGrid from './MdFieldGrid.vue'

describe('MdFieldGrid', () => {
  it('lays its fields out in one column below the expanded window class', () => {
    const wrapper = mount(MdFieldGrid)

    expect(wrapper.classes(), 'the grid must be a single column by default').toContain('grid-cols-1')
  })

  it('lays its fields out in two columns from the expanded window class', () => {
    const wrapper = mount(MdFieldGrid)

    expect(wrapper.classes(), 'the grid must take two columns from the expanded window').toContain('expanded:grid-cols-2')
  })

  it('does not key its columns to the sm breakpoint', () => {
    const wrapper = mount(MdFieldGrid)

    expect(wrapper.classes().some((name) => name.startsWith('sm:')), 'window classes, not sm:, drive the columns').toBe(false)
  })

  it('renders the default slot as the fields of the grid', () => {
    const wrapper = mount(MdFieldGrid, {
      slots: { default: () => h('input', { 'data-test': 'host' }) }
    })

    expect(wrapper.find('[data-test="host"]').exists(), 'the slot content must sit inside the grid').toBe(true)
  })

  it('passes a caller class through to the grid', () => {
    const wrapper = mount(MdFieldGrid, { attrs: { class: 'mt-4' } })

    expect(wrapper.classes(), 'a caller class must land on the grid root').toContain('mt-4')
  })
})
