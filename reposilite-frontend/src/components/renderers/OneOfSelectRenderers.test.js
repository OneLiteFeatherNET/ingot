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
import { tester as selectTester, oneOfTester, optionsOf } from './MdSelectRenderer.vue'
import MdSelectRenderer from './MdSelectRenderer.vue'
import { tester as constantTester } from './ConstantRenderer.vue'
import ConstantRenderer from './ConstantRenderer.vue'
import { tester as oneOfTester2 } from './OneOfRenderer.vue'
import OneOfRenderer from './OneOfRenderer.vue'

const renderers = markRaw([
  { tester: selectTester, renderer: MdSelectRenderer },
  { tester: oneOfTester, renderer: MdSelectRenderer },
  { tester: constantTester, renderer: ConstantRenderer },
  { tester: oneOfTester2, renderer: OneOfRenderer },
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

// A one-of whose branches are constants, the shape of a plain choice with titles.
const providerChoice = {
  oneOf: [
    { const: 'fs', title: 'Local file system' },
    { const: 's3', title: 'S3 Storage Provider' }
  ]
}

describe('one-of constant choice', () => {
  it('reads the option labels from the branch titles', () => {
    expect(optionsOf(providerChoice)).toEqual([
      { value: 'fs', label: 'Local file system' },
      { value: 's3', label: 'S3 Storage Provider' }
    ])
  })

  it('draws the choice as the outlined select, not the vanilla select with its own chevron', () => {
    const wrapper = mountField(providerChoice, { field: 'fs' })

    expect(wrapper.find('select').classes(), 'the outlined select is the field').toContain('appearance-none')
    expect(wrapper.find('select').classes(), 'no background image may draw a second chevron').toContain('bg-none')
    expect(wrapper.find('label').text()).toBe('Field')
  })

  it('writes the constant of the chosen branch into the data', async () => {
    const wrapper = mountField(providerChoice, { field: 'fs' })

    await wrapper.find('select').setValue('s3')

    expect(lastData(wrapper).field).toBe('s3')
  })
})

describe('const value', () => {
  it('draws a constant as a read-only row with the label and the value as text', () => {
    const wrapper = mountField({ type: 'string', const: 's3' }, { field: 's3' })

    expect(wrapper.find('input').exists(), 'a constant is not an input that looks editable').toBe(false)
    expect(wrapper.get('[data-test="constant-row"]').text()).toContain('Field')
    expect(wrapper.get('[data-test="constant-row"]').text()).toContain('s3')
  })
})
