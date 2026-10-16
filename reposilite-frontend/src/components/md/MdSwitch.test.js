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
import MdSwitch from './MdSwitch.vue'

const mountSwitch = (props = {}, slot = 'Sort newest first') =>
  mount(MdSwitch, { props, slots: { default: slot } })

const track = (wrapper) => wrapper.find('[role="switch"]')
const handle = (wrapper) => track(wrapper).find('[data-part="handle"]')
const stateLayer = (wrapper) => track(wrapper).find('[data-part="state-layer"]')

describe('MdSwitch', () => {
  it('renders role switch on a button element', () => {
    const wrapper = mountSwitch()

    expect(track(wrapper).element.tagName).toBe('BUTTON')
  })

  it('reports aria-checked false when the value is false', () => {
    const wrapper = mountSwitch({ modelValue: false })

    expect(track(wrapper).attributes('aria-checked')).toBe('false')
  })

  it('reports aria-checked true when the value is true', () => {
    const wrapper = mountSwitch({ modelValue: true })

    expect(track(wrapper).attributes('aria-checked')).toBe('true')
  })

  it('emits true when an off switch is clicked', async () => {
    const wrapper = mountSwitch({ modelValue: false })

    await track(wrapper).trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])
  })

  it('emits false when an on switch is clicked', async () => {
    const wrapper = mountSwitch({ modelValue: true })

    await track(wrapper).trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })

  it('does not emit when the switch is disabled', async () => {
    const wrapper = mountSwitch({ modelValue: false, disabled: true })

    await track(wrapper).trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('disables the button element when disabled', () => {
    const wrapper = mountSwitch({ disabled: true })

    expect(track(wrapper).attributes('disabled')).toBeDefined()
  })

  it('uses the id prop for the switch and the label for attribute', () => {
    const wrapper = mountSwitch({ id: 'sort-order' })

    expect(track(wrapper).attributes('id')).toBe('sort-order')
    expect(wrapper.find('label').attributes('for')).toBe('sort-order')
  })

  it('generates an id tied to the label when none is given', () => {
    const wrapper = mountSwitch()

    expect(track(wrapper).attributes('id')).toBeTruthy()
    expect(wrapper.find('label').attributes('for')).toBe(track(wrapper).attributes('id'))
  })

  it('renders the default slot as the label', () => {
    const wrapper = mountSwitch({}, 'Sort newest first')

    expect(wrapper.find('label').text()).toBe('Sort newest first')
  })

  it('passes aria-labelledby through to the switch element', () => {
    const wrapper = mount(MdSwitch, {
      attrs: { 'aria-labelledby': 'store-label' },
      slots: { default: 'Store' }
    })

    expect(track(wrapper).attributes('aria-labelledby')).toBe('store-label')
  })

  it('keeps class on the root element instead of the switch', () => {
    const wrapper = mount(MdSwitch, {
      attrs: { class: 'min-h-16' },
      slots: { default: 'Store' }
    })

    expect(wrapper.classes()).toContain('min-h-16')
    expect(track(wrapper).classes()).not.toContain('min-h-16')
  })

  it('draws the track at 52 by 32 px', () => {
    const wrapper = mountSwitch()

    expect(track(wrapper).classes()).toContain('w-13')
    expect(track(wrapper).classes()).toContain('h-8')
  })

  it('draws a 2px outline on the track when off', () => {
    const wrapper = mountSwitch({ modelValue: false })

    expect(track(wrapper).classes()).toContain('border-2')
    expect(track(wrapper).classes()).toContain('border-outline')
  })

  it('fills the track with primary when on and drops the outline', () => {
    const wrapper = mountSwitch({ modelValue: true })

    expect(track(wrapper).classes()).toContain('bg-primary')
    expect(track(wrapper).classes()).not.toContain('border-outline')
  })

  it('sizes the handle at 16px when off', () => {
    const wrapper = mountSwitch({ modelValue: false })

    expect(handle(wrapper).classes()).toContain('size-4')
  })

  it('sizes the handle at 24px when on, in on-primary', () => {
    const wrapper = mountSwitch({ modelValue: true })

    expect(handle(wrapper).classes()).toContain('size-6')
    expect(handle(wrapper).classes()).toContain('bg-on-primary')
  })

  it('grows the handle to 28px while pressed', () => {
    const wrapper = mountSwitch({ modelValue: false })

    expect(track(wrapper).classes()).toContain('group')
    expect(handle(wrapper).classes()).toContain('group-active:size-7')
  })

  it('sizes the state layer at 40px around the handle', () => {
    const wrapper = mountSwitch()

    expect(stateLayer(wrapper).classes()).toContain('size-10')
  })

  it('uses the MD3 state opacities on the state layer: hover 8, focus 12, pressed 12', () => {
    const wrapper = mountSwitch()

    expect(stateLayer(wrapper).classes()).toContain('group-hover:opacity-[0.08]')
    expect(stateLayer(wrapper).classes()).toContain('group-focus-visible:opacity-[0.12]')
    expect(stateLayer(wrapper).classes()).toContain('group-active:opacity-[0.12]')
  })

  it('draws no state layer while disabled', () => {
    const wrapper = mountSwitch({ disabled: true })

    expect(stateLayer(wrapper).classes()).not.toContain('group-hover:opacity-[0.08]')
    expect(stateLayer(wrapper).classes()).toContain('opacity-0')
  })

  it('sets the disabled track to on-surface at 12 percent when on', () => {
    const wrapper = mountSwitch({ modelValue: true, disabled: true })

    expect(track(wrapper).classes()).toContain('bg-on-surface/12')
  })

  it('sets the disabled handle to on-surface at 38 percent when off', () => {
    const wrapper = mountSwitch({ modelValue: false, disabled: true })

    expect(handle(wrapper).classes()).toContain('bg-on-surface/38')
  })

  it('sets the label to 38 percent on-surface when disabled', () => {
    const wrapper = mountSwitch({ disabled: true })

    expect(wrapper.find('label').classes()).toContain('text-on-surface/38')
  })

  it('uses the touch target class so the hit area reaches 48px', () => {
    const wrapper = mountSwitch()

    expect(track(wrapper).classes()).toContain('md-touch-target')
  })

  it('renders rich slot content inside the label', () => {
    const wrapper = mount(MdSwitch, {
      slots: { default: () => h('span', { 'data-test': 'rich' }, 'Rich') }
    })

    expect(wrapper.find('label [data-test="rich"]').exists()).toBe(true)
  })
})
