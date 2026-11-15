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
import MdCard from './MdCard.vue'

describe('MdCard', () => {
  it('renders its default slot content', () => {
    const wrapper = mount(MdCard, { slots: { default: () => h('p', 'Artifact details') } })

    expect(wrapper.text()).toBe('Artifact details')
  })

  it('defaults to the filled variant', () => {
    const wrapper = mount(MdCard)

    expect(wrapper.attributes('data-variant')).toBe('filled')
    expect(wrapper.classes()).toContain('bg-surface-container-highest')
  })

  it('draws the outline-variant border for the outlined variant', () => {
    const wrapper = mount(MdCard, { props: { variant: 'outlined' } })

    expect(wrapper.classes()).toContain('border-outline-variant')
  })

  it('elevated variant uses elevation level 1', () => {
    const wrapper = mount(MdCard, { props: { variant: 'elevated' } })

    expect(wrapper.classes()).toContain('shadow-elevation-1')
  })
})
