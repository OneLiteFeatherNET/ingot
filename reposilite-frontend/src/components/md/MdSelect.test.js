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
import MdSelect from './MdSelect.vue'

const options = [
  { value: 'PUBLIC', label: 'Public' },
  { value: 'HIDDEN', label: 'Hidden' },
  { value: 'PRIVATE', label: 'Private' }
]

const mountSelect = (props = {}) => mount(MdSelect, {
  props: { label: 'Visibility', options, ...props }
})

describe('MdSelect', () => {
  it('associates the label with the select through for and id', () => {
    const wrapper = mountSelect()

    const select = wrapper.find('select')
    expect(wrapper.find('label').attributes('for')).toBe(select.attributes('id'))
  })

  it('lists every option under its label', () => {
    const wrapper = mountSelect()

    expect(wrapper.findAll('option').map(option => option.text())).toEqual(['Public', 'Hidden', 'Private'])
  })

  it('shows the option whose value matches modelValue', () => {
    const wrapper = mountSelect({ modelValue: 'HIDDEN' })

    expect(wrapper.find('select').element.value).toBe('HIDDEN')
  })

  it('emits the value of the option that is chosen', async () => {
    const wrapper = mountSelect({ modelValue: 'PUBLIC' })

    await wrapper.find('select').setValue('PRIVATE')

    expect(wrapper.emitted('update:modelValue')).toEqual([['PRIVATE']])
  })

  it('marks the select invalid and shows the error text instead of the supporting text', () => {
    const wrapper = mountSelect({ error: true, errorText: 'Pick a visibility.', supportingText: 'Who can see it.' })

    expect(wrapper.find('select').attributes('aria-invalid')).toBe('true')
    expect(wrapper.text()).toContain('Pick a visibility.')
    expect(wrapper.text(), 'the supporting text is replaced while the error shows').not.toContain('Who can see it.')
  })

  it('links the supporting text to the select through aria-describedby', () => {
    const wrapper = mountSelect({ supportingText: 'Who can see it.' })

    const helper = wrapper.find('p')
    expect(helper.text()).toBe('Who can see it.')
    expect(wrapper.find('select').attributes('aria-describedby')).toBe(helper.attributes('id'))
  })

  it('cannot be changed while disabled', () => {
    const wrapper = mountSelect({ disabled: true })

    expect(wrapper.find('select').attributes('disabled')).toBeDefined()
  })

  it('shows no option as chosen while modelValue is empty', () => {
    const wrapper = mountSelect({ modelValue: '' })

    expect(wrapper.find('select').element.value).toBe('')
  })
})
