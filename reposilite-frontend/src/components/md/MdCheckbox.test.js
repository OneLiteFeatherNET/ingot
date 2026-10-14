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
import MdCheckbox from './MdCheckbox.vue'

const mountCheckbox = (props = {}, slot = 'Generate checksums') =>
  mount(MdCheckbox, { props, slots: { default: slot } })

const input = (wrapper) => wrapper.find('input[type="checkbox"]')
const box = (wrapper) => wrapper.find('[data-part="box"]')
const stateLayer = (wrapper) => wrapper.find('[data-part="state-layer"]')

describe('MdCheckbox', () => {
  it('renders a real checkbox input for the semantics', () => {
    const wrapper = mountCheckbox()

    expect(input(wrapper).exists()).toBe(true)
  })

  it('reflects the model value in the checked state of the input', () => {
    const wrapper = mountCheckbox({ modelValue: true })

    expect(input(wrapper).element.checked).toBe(true)
  })

  it('emits true when an unchecked box is checked', async () => {
    const wrapper = mountCheckbox({ modelValue: false })

    await input(wrapper).setValue(true)

    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])
  })

  it('emits false when a checked box is cleared', async () => {
    const wrapper = mountCheckbox({ modelValue: true })

    await input(wrapper).setValue(false)

    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })

  it('disables the input element when disabled', () => {
    const wrapper = mountCheckbox({ disabled: true })

    expect(input(wrapper).attributes('disabled')).toBeDefined()
  })

  it('ties the label to the input through for and id', () => {
    const wrapper = mountCheckbox({ id: 'checksums' })

    expect(input(wrapper).attributes('id')).toBe('checksums')
    expect(wrapper.find('label').attributes('for')).toBe('checksums')
  })

  it('generates an id tied to the label when none is given', () => {
    const wrapper = mountCheckbox()

    expect(input(wrapper).attributes('id')).toBeTruthy()
    expect(wrapper.find('label').attributes('for')).toBe(input(wrapper).attributes('id'))
  })

  it('renders the default slot as the label', () => {
    const wrapper = mountCheckbox({}, 'Generate default checksums')

    expect(wrapper.find('label').text()).toBe('Generate default checksums')
  })

  it('draws the box at 18px', () => {
    const wrapper = mountCheckbox()

    expect(box(wrapper).classes()).toContain('size-[18px]')
  })

  it('sizes the state layer at 40px, the size of its container', () => {
    const wrapper = mountCheckbox()

    expect(stateLayer(wrapper).element.parentElement.className).toContain('size-10')
    expect(stateLayer(wrapper).classes()).toContain('inset-0')
  })

  it('draws a 2px outline when unchecked', () => {
    const wrapper = mountCheckbox({ modelValue: false })

    expect(box(wrapper).classes()).toContain('border-2')
    expect(box(wrapper).classes()).toContain('border-outline')
    expect(box(wrapper).attributes('data-state')).toBe('unchecked')
  })

  it('fills the box with the primary container and shows the check mark when checked', () => {
    const wrapper = mountCheckbox({ modelValue: true })

    expect(box(wrapper).classes()).toContain('bg-primary')
    expect(box(wrapper).classes()).toContain('border-primary')
    expect(box(wrapper).attributes('data-state')).toBe('checked')
    expect(box(wrapper).find('svg').exists()).toBe(true)
    expect(box(wrapper).find('svg').classes()).toContain('text-on-primary')
  })

  it('shows a dash instead of a check mark when indeterminate', () => {
    const wrapper = mountCheckbox({ modelValue: false, indeterminate: true })

    expect(box(wrapper).attributes('data-state')).toBe('indeterminate')
    expect(box(wrapper).find('svg').exists()).toBe(false)
    expect(input(wrapper).element.indeterminate).toBe(true)
  })

  it('draws the outline in the error role when error is set', () => {
    const wrapper = mountCheckbox({ modelValue: false, error: true })

    expect(box(wrapper).classes()).toContain('border-error')
    expect(input(wrapper).attributes('aria-invalid')).toBe('true')
  })

  it('fills the box with the error role when checked in the error state', () => {
    const wrapper = mountCheckbox({ modelValue: true, error: true })

    expect(box(wrapper).classes()).toContain('bg-error')
    expect(box(wrapper).classes()).not.toContain('bg-primary')
  })

  it('sets an unchecked disabled outline to on-surface at 38 percent', () => {
    const wrapper = mountCheckbox({ modelValue: false, disabled: true })

    expect(box(wrapper).classes()).toContain('border-on-surface/38')
  })

  it('sets a checked disabled box to on-surface at 38 percent', () => {
    const wrapper = mountCheckbox({ modelValue: true, disabled: true })

    expect(box(wrapper).classes()).toContain('bg-on-surface/38')
    expect(box(wrapper).find('svg').classes()).toContain('text-surface')
  })

  it('shows the focus state layer while the input has keyboard focus', () => {
    const wrapper = mountCheckbox()

    expect(stateLayer(wrapper).classes()).toContain('peer-focus-visible:opacity-[0.12]')
  })

  it('uses the MD3 hover and pressed opacities on the state layer', () => {
    const wrapper = mountCheckbox()

    expect(stateLayer(wrapper).classes()).toContain('peer-hover:opacity-[0.08]')
    expect(stateLayer(wrapper).classes()).toContain('peer-active:opacity-[0.12]')
  })

  it('draws no state layer while disabled', () => {
    const wrapper = mountCheckbox({ disabled: true })

    expect(stateLayer(wrapper).classes()).not.toContain('peer-hover:opacity-[0.08]')
    expect(stateLayer(wrapper).classes()).toContain('opacity-0')
  })

  it('keeps class on the root element instead of the input', () => {
    const wrapper = mount(MdCheckbox, {
      attrs: { class: 'mt-2' },
      slots: { default: 'Generate checksums' }
    })

    expect(wrapper.classes()).toContain('mt-2')
    expect(input(wrapper).classes()).not.toContain('mt-2')
  })
})
