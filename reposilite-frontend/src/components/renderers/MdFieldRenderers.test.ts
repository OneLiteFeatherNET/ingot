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
import { markRaw } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { createAjv } from '@jsonforms/core'
import { vanillaRenderers } from '@dzikoysk/vue-vanilla'
import { tester as textTester } from './MdTextFieldRenderer.vue'
import MdTextFieldRenderer from './MdTextFieldRenderer.vue'
import { tester as selectTester } from './MdSelectRenderer.vue'
import MdSelectRenderer from './MdSelectRenderer.vue'

const renderers = markRaw([
  { tester: textTester, renderer: MdTextFieldRenderer },
  { tester: selectTester, renderer: MdSelectRenderer },
  ...vanillaRenderers
])

const mountField = (schema, data) => mount(JsonForms, {
  props: {
    data,
    schema: { type: 'object', properties: { field: schema } },
    uischema: { type: 'VerticalLayout', elements: [{ type: 'Control', scope: '#/properties/field', label: 'Field' }] },
    renderers,
    config: { showUnfocusedDescription: true },
    ajv: markRaw(createAjv({ useDefaults: true }))
  }
})

const lastData = (wrapper) => wrapper.emitted('change').at(-1)[0].data

describe('MdTextFieldRenderer', () => {
  it('draws a text string as an outlined text field labelled by the property', () => {
    const wrapper = mountField({ type: 'string', description: 'The name.' }, { field: 'releases' })

    const input = wrapper.find('input')
    expect(input.element.value).toBe('releases')
    expect(wrapper.find('label').text()).toBe('Field')
    expect(wrapper.text()).toContain('The name.')
  })

  it('writes the typed text into the data', async () => {
    const wrapper = mountField({ type: 'string' }, { field: 'releases' })

    await wrapper.find('input').setValue('snapshots')

    expect(lastData(wrapper).field).toBe('snapshots')
  })

  it('removes an emptied text from the data, so the property is left out when saved', async () => {
    const wrapper = mountField({ type: 'string' }, { field: 'releases' })

    await wrapper.find('input').setValue('')

    expect(lastData(wrapper).field).toBeUndefined()
  })

  it('reads a number as a number, not as text', async () => {
    const wrapper = mountField({ type: 'integer' }, { field: 3 })

    expect(wrapper.find('input').attributes('type')).toBe('number')
    await wrapper.find('input').setValue('15')

    expect(lastData(wrapper).field).toBe(15)
  })
})

describe('MdSelectRenderer', () => {
  const visibility = { type: 'string', enum: ['PUBLIC', 'PRIVATE'] }

  it('draws an enum as a select with one option per value', () => {
    const wrapper = mountField(visibility, { field: 'PUBLIC' })

    expect(wrapper.findAll('option').map(option => option.text())).toEqual(['PUBLIC', 'PRIVATE'])
    expect(wrapper.find('select').element.value).toBe('PUBLIC')
  })

  it('writes the chosen enum value into the data', async () => {
    const wrapper = mountField(visibility, { field: 'PUBLIC' })

    await wrapper.find('select').setValue('PRIVATE')

    expect(lastData(wrapper).field).toBe('PRIVATE')
  })

  it('keeps an enum that is not a string as the value it was', async () => {
    const wrapper = mountField({ enum: [1, 2] }, { field: 1 })

    await wrapper.find('select').setValue('2')

    expect(lastData(wrapper).field).toBe(2)
  })
})
