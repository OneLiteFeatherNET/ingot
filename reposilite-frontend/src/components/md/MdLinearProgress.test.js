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
import MdLinearProgress from './MdLinearProgress.vue'

describe('MdLinearProgress', () => {
  it('exposes a progressbar with the value as a percentage', () => {
    const wrapper = mount(MdLinearProgress, { props: { value: 0.5 } })

    expect(wrapper.attributes('role')).toBe('progressbar')
    expect(wrapper.attributes('aria-valuenow')).toBe('50')
  })

  it('sets the bar width from the value', () => {
    const wrapper = mount(MdLinearProgress, { props: { value: 0.25 } })

    expect(wrapper.element.firstElementChild.style.width).toBe('25%')
  })

  it('clamps a value above 1 to 100 percent', () => {
    const wrapper = mount(MdLinearProgress, { props: { value: 3 } })

    expect(wrapper.attributes('aria-valuenow')).toBe('100')
  })

  it('clamps a negative value to 0 percent', () => {
    const wrapper = mount(MdLinearProgress, { props: { value: -0.4 } })

    expect(wrapper.attributes('aria-valuenow')).toBe('0')
  })

  it('omits aria-valuenow while indeterminate', () => {
    const wrapper = mount(MdLinearProgress, { props: { indeterminate: true } })

    expect(wrapper.attributes('aria-valuenow')).toBeUndefined()
  })

  it('labels the progressbar with the label prop', () => {
    const wrapper = mount(MdLinearProgress, { props: { label: 'Uploading artifact' } })

    expect(wrapper.attributes('aria-label')).toBe('Uploading artifact')
  })
})
