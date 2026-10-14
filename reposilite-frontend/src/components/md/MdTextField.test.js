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
import { h, defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import MdTextField from './MdTextField.vue'

describe('MdTextField', () => {
  it('associates the label with the input through for and id', () => {
    const wrapper = mount(MdTextField, { props: { label: 'Repository' } })

    const input = wrapper.find('input')
    const label = wrapper.find('label')
    expect(input.attributes('id')).toBeTruthy()
    expect(label.attributes('for')).toBe(input.attributes('id'))
  })

  it('gives two fields in the same app different generated ids', () => {
    // Generated ids are unique per app, so both fields are mounted under one parent.
    const Pair = defineComponent({
      render: () => [
        h(MdTextField, { label: 'First' }),
        h(MdTextField, { label: 'Second' })
      ]
    })
    const wrapper = mount(Pair)

    const [first, second] = wrapper.findAll('input').map((input) => input.attributes('id'))
    expect(first).not.toBe(second)
  })

  it('uses the id prop when one is given', () => {
    const wrapper = mount(MdTextField, { props: { label: 'Name', id: 'token-name' } })

    expect(wrapper.find('input').attributes('id')).toBe('token-name')
    expect(wrapper.find('label').attributes('for')).toBe('token-name')
  })

  it('shows the model value in the input', () => {
    const wrapper = mount(MdTextField, { props: { label: 'Name', modelValue: 'ci-token' } })

    expect(wrapper.find('input').element.value).toBe('ci-token')
  })

  it('emits update:modelValue with the typed value', async () => {
    const wrapper = mount(MdTextField, { props: { label: 'Name' } })

    await wrapper.find('input').setValue('deploy')

    expect(wrapper.emitted('update:modelValue')[0]).toEqual(['deploy'])
  })

  it('shows supporting text when there is no error', () => {
    const wrapper = mount(MdTextField, {
      props: { label: 'Name', supportingText: 'Shown in the token list' }
    })

    expect(wrapper.text()).toContain('Shown in the token list')
  })

  it('shows error text and sets aria-invalid in the error state', () => {
    const wrapper = mount(MdTextField, {
      props: { label: 'Name', error: true, errorText: 'Name is required' }
    })

    expect(wrapper.text()).toContain('Name is required')
    expect(wrapper.find('input').attributes('aria-invalid')).toBe('true')
  })

  it('hides the supporting text in the error state', () => {
    const wrapper = mount(MdTextField, {
      props: {
        label: 'Name',
        error: true,
        errorText: 'Name is required',
        supportingText: 'Shown in the token list'
      }
    })

    expect(wrapper.text()).not.toContain('Shown in the token list')
  })

  it('does not set aria-invalid while there is no error', () => {
    const wrapper = mount(MdTextField, { props: { label: 'Name', errorText: 'Name is required' } })

    expect(wrapper.find('input').attributes('aria-invalid')).toBeUndefined()
  })

  it('links the visible helper text through aria-describedby', () => {
    const wrapper = mount(MdTextField, {
      props: { label: 'Name', supportingText: 'Shown in the token list' }
    })

    const helper = wrapper.find('p')
    expect(wrapper.find('input').attributes('aria-describedby')).toBe(helper.attributes('id'))
  })

  it('renders the leading icon slot before the input', () => {
    const wrapper = mount(MdTextField, {
      props: { label: 'Search' },
      slots: { 'leading-icon': () => h('svg', { 'data-test': 'lead' }) }
    })

    expect(wrapper.find('[data-test="lead"]').exists()).toBe(true)
  })

  it('renders the trailing icon slot', () => {
    const wrapper = mount(MdTextField, {
      props: { label: 'Search' },
      slots: { 'trailing-icon': () => h('svg', { 'data-test': 'trail' }) }
    })

    expect(wrapper.find('[data-test="trail"]').exists()).toBe(true)
  })

  it('forwards constraint attributes to the input element', () => {
    const wrapper = mount(MdTextField, {
      props: { label: 'Count' },
      attrs: { min: '1', max: '99', step: '1', pattern: '[0-9]+', maxlength: '2', inputmode: 'numeric' }
    })

    const input = wrapper.find('input')
    expect(input.attributes('min')).toBe('1')
    expect(input.attributes('max')).toBe('99')
    expect(input.attributes('step')).toBe('1')
    expect(input.attributes('pattern')).toBe('[0-9]+')
    expect(input.attributes('maxlength')).toBe('2')
    expect(input.attributes('inputmode')).toBe('numeric')
  })

  it('keeps class and style on the root element instead of the input', () => {
    const wrapper = mount(MdTextField, {
      props: { label: 'Name' },
      attrs: { class: 'w-48', style: 'margin-top: 4px' }
    })

    expect(wrapper.classes()).toContain('w-48')
    expect(wrapper.attributes('style')).toContain('margin-top: 4px')
    expect(wrapper.find('input').classes()).not.toContain('w-48')
    expect(wrapper.find('input').attributes('style')).toBeUndefined()
  })

  it('outlined variant draws the outline border on the input', () => {
    const wrapper = mount(MdTextField, { props: { label: 'Name', variant: 'outlined' } })

    expect(wrapper.find('input').classes()).toContain('border-outline')
  })
})
