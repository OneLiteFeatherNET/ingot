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
  })

  it('draws the filled card on surface-container-low with a one pixel outline border', () => {
    const wrapper = mount(MdCard)

    expect(wrapper.classes(), 'the default card is the surface-container-low role of the surface table').toContain('bg-surface-container-low')
    expect(wrapper.classes(), 'the default card keeps its edge with the outline role').toContain('border-outline')
    expect(wrapper.classes(), 'the default card is not the highest surface').not.toContain('bg-surface-container-highest')
  })

  it('draws the outlined card on surface with the outline border', () => {
    const wrapper = mount(MdCard, { props: { variant: 'outlined' } })

    expect(wrapper.classes()).toContain('bg-surface')
    expect(wrapper.classes()).toContain('border-outline')
  })

  it('elevated variant uses elevation level 1', () => {
    const wrapper = mount(MdCard, { props: { variant: 'elevated' } })

    expect(wrapper.classes()).toContain('shadow-elevation-1')
  })

  it('elevated variant has no outline border, because its elevation draws the edge', () => {
    const wrapper = mount(MdCard, { props: { variant: 'elevated' } })

    expect(wrapper.classes(), 'the elevated card has no border').not.toContain('border-outline')
    expect(wrapper.classes(), 'the elevated card is the surface-container-low role').toContain('bg-surface-container-low')
  })

  it('low variant uses the surface-container-low background', () => {
    const wrapper = mount(MdCard, { props: { variant: 'low' } })

    expect(wrapper.attributes('data-variant')).toBe('low')
    expect(wrapper.classes()).toContain('bg-surface-container-low')
  })

  it('low variant has no shadow and no border', () => {
    const wrapper = mount(MdCard, { props: { variant: 'low' } })

    expect(wrapper.classes(), 'the low card lies flat on the page').not.toContain('shadow-elevation-1')
    expect(wrapper.classes(), 'the low card has no outline').not.toContain('border-outline-variant')
  })

  it('low variant keeps the medium corner radius and default padding of the hand-built cards', () => {
    const wrapper = mount(MdCard, { props: { variant: 'low' } })

    expect(wrapper.classes()).toContain('rounded-corner-medium')
    expect(wrapper.classes()).toContain('p-4')
  })

  it('keeps the padding of the default card when no padding is given', () => {
    const wrapper = mount(MdCard)

    expect(wrapper.classes()).toContain('p-4')
  })

  it('removes the padding when padding is none and keeps the corner radius', () => {
    const wrapper = mount(MdCard, { props: { padding: 'none' } })

    expect(wrapper.classes(), 'a card without padding must not carry p-4').not.toContain('p-4')
    expect(wrapper.classes(), 'a card without padding keeps the medium corner radius').toContain('rounded-corner-medium')
  })
})

describe('MdCard tone, edge and danger', () => {
  it.each([
    ['low', 'bg-surface-container-low'],
    ['container', 'bg-surface-container'],
    ['high', 'bg-surface-container-high'],
    ['highest', 'bg-surface-container-highest']
  ])('tone %s draws the %s surface role', (tone, role) => {
    const wrapper = mount(MdCard, { props: { tone: tone as 'low' } })

    expect(wrapper.classes(), `the ${tone} tone is the ${role} role`).toContain(role)
  })

  it('tone highest replaces the surface role of the default card', () => {
    const wrapper = mount(MdCard, { props: { tone: 'highest' } })

    expect(wrapper.classes(), 'the highest tone is not the low surface of the default card').not.toContain('bg-surface-container-low')
  })

  it('edge none draws the filled card without its outline border', () => {
    const wrapper = mount(MdCard, { props: { edge: 'none' } })

    expect(wrapper.classes(), 'the card with no edge has no outline border').not.toContain('border-outline')
    expect(wrapper.classes(), 'the card with no edge has no elevation').not.toContain('shadow-elevation-1')
  })

  it('edge outline draws the outline border on an elevated card', () => {
    const wrapper = mount(MdCard, { props: { variant: 'elevated', edge: 'outline' } })

    expect(wrapper.classes(), 'the outlined edge draws the outline border').toContain('border-outline')
    expect(wrapper.classes(), 'the outlined edge replaces the elevation').not.toContain('shadow-elevation-1')
  })

  it('edge elevation replaces the outline border of an outlined card', () => {
    const wrapper = mount(MdCard, { props: { variant: 'outlined', edge: 'elevation' } })

    expect(wrapper.classes(), 'the elevated edge draws elevation level 1').toContain('shadow-elevation-1')
    expect(wrapper.classes(), 'the elevated edge has no outline border').not.toContain('border-outline')
  })

  it('draws the danger tone with the error border instead of the outline border', () => {
    const wrapper = mount(MdCard, { props: { tone: 'danger' } })

    expect(wrapper.classes(), 'the danger card borders in the error role').toContain('border-error')
    expect(wrapper.classes(), 'the danger card has no outline border').not.toContain('border-outline')
  })

  it('keeps the surface, padding and radius of the default card for the danger tone', () => {
    const wrapper = mount(MdCard, { props: { tone: 'danger' } })

    expect(wrapper.classes()).toEqual(expect.arrayContaining(['bg-surface-container-low', 'p-4', 'rounded-corner-medium']))
  })

  it('leaves the default card without a tone or an edge unchanged', () => {
    const wrapper = mount(MdCard)

    expect(wrapper.classes(), 'the default card keeps its outline border').toContain('border-outline')
    expect(wrapper.classes(), 'the default card keeps the low surface').toContain('bg-surface-container-low')
    expect(wrapper.classes(), 'the default card has no error border').not.toContain('border-error')
  })
})
