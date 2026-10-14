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
import SegmentedOption from './SegmentedOption.vue'

const mountOption = (props = {}) => mount(SegmentedOption, {
  props,
  slots: { default: 'Open' }
})

describe('SegmentedOption', () => {
  it('reports the selected state through aria-pressed', () => {
    const wrapper = mountOption({ on: true })

    expect(wrapper.find('button').attributes('aria-pressed')).toBe('true')
  })

  it('shows the check mark only on the selected segment', () => {
    const selected = mountOption({ on: true })
    const unselected = mountOption({ on: false })

    expect(selected.find('svg').exists(), 'the selected segment has a check mark').toBe(true)
    expect(unselected.find('svg').exists(), 'an unselected segment has no check mark').toBe(false)
  })

  it('is 40px tall', () => {
    const wrapper = mountOption()

    expect(wrapper.find('button').classes(), 'the segment height is h-10').toContain('h-10')
  })

  it('extends its hit area to the 48px minimum with an after pseudo element', () => {
    const wrapper = mountOption()

    expect(wrapper.find('button').classes(), 'the ::after target grows the box by 4px above and below').toContain('after:-inset-y-1')
  })
})
