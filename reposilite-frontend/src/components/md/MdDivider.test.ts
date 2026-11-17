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
import MdDivider from './MdDivider.vue'

describe('MdDivider', () => {
  it('exposes one separator role', () => {
    const wrapper = mount(MdDivider)

    expect(wrapper.attributes('role')).toBe('separator')
  })

  it('draws a single rule and no text when it has no label', () => {
    const wrapper = mount(MdDivider)

    expect(wrapper.findAll('[data-test="divider-rule"]')).toHaveLength(1)
    expect(wrapper.text()).toBe('')
  })

  it('names the separator with its label and shows the label as text', () => {
    const wrapper = mount(MdDivider, { props: { label: 'or' } })

    expect(wrapper.attributes('aria-label')).toBe('or')
    expect(wrapper.get('[data-test="divider-label"]').text()).toBe('or')
  })

  it('draws a rule on each side of the label', () => {
    const wrapper = mount(MdDivider, { props: { label: 'or' } })

    expect(wrapper.findAll('[data-test="divider-rule"]')).toHaveLength(2)
  })

  it('has no inset by default', () => {
    const wrapper = mount(MdDivider)

    expect(wrapper.attributes('data-inset')).toBe('none')
    expect(wrapper.classes()).not.toContain('ps-md-inset-16')
    expect(wrapper.classes()).not.toContain('ps-md-inset-72')
  })

  it('uses the 16px inset token when inset is 16', () => {
    const wrapper = mount(MdDivider, { props: { inset: 16 } })

    expect(wrapper.attributes('data-inset')).toBe('16')
    expect(wrapper.classes()).toContain('ps-md-inset-16')
  })

  it('uses the 72px inset token when inset is 72, to clear a leading avatar', () => {
    const wrapper = mount(MdDivider, { props: { inset: 72 } })

    expect(wrapper.attributes('data-inset')).toBe('72')
    expect(wrapper.classes()).toContain('ps-md-inset-72')
  })

  it('carries the md-divider anchor on its root', () => {
    expect(mount(MdDivider).attributes('data-ingot-component')).toBe('md-divider')
  })
})
