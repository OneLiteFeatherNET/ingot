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
import SwitchField from './SwitchField.vue'

const mountSwitch = (props = {}) => mount(SwitchField, {
  props: { id: 'store-input', label: 'Store', ...props }
})

describe('SwitchField', () => {
  it('reports aria-checked false when the value is false', () => {
    const wrapper = mountSwitch({ modelValue: false })

    expect(wrapper.find('[role="switch"]').attributes('aria-checked')).toBe('false')
  })

  it('reports aria-checked true when the value is true', () => {
    const wrapper = mountSwitch({ modelValue: true })

    expect(wrapper.find('[role="switch"]').attributes('aria-checked')).toBe('true')
  })

  it('emits true when an off switch is clicked', () => {
    const wrapper = mountSwitch({ modelValue: false })

    wrapper.find('[role="switch"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])
  })

  it('emits false when an on switch is clicked', () => {
    const wrapper = mountSwitch({ modelValue: true })

    wrapper.find('[role="switch"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })

  it('does not emit when the switch is disabled', () => {
    const wrapper = mountSwitch({ modelValue: false, disabled: true })

    wrapper.find('[role="switch"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('names the switch with the label above it through aria-labelledby', () => {
    const wrapper = mountSwitch()

    const label = wrapper.find('#store-input-label')
    const toggle = wrapper.find('[role="switch"]')

    expect(label.text()).toContain('Store')
    expect(toggle.attributes('id')).toBe('store-input')
    expect(toggle.attributes('aria-labelledby')).toBe('store-input-label')
  })

  it('shows the required marker on the label when the field is required', () => {
    const wrapper = mountSwitch({ required: true })

    expect(wrapper.find('#store-input-label').text()).toContain('*')
  })

  it('shows Enabled for an on switch and Disabled for an off one', () => {
    expect(mountSwitch({ modelValue: true }).text()).toContain('Enabled')
    expect(mountSwitch({ modelValue: false }).text()).toContain('Disabled')
  })
})
