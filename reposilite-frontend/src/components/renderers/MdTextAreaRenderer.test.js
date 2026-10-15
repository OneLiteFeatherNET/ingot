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

import { describe, it, expect, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { markRaw } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { createAjv } from '@jsonforms/core'
import { vanillaRenderers } from '@dzikoysk/vue-vanilla'
import { tester as areaTester } from './MdTextAreaRenderer.vue'
import MdTextAreaRenderer from './MdTextAreaRenderer.vue'
import { tester as textTester } from './MdTextFieldRenderer.vue'
import MdTextFieldRenderer from './MdTextFieldRenderer.vue'

const renderers = markRaw([
  { tester: areaTester, renderer: MdTextAreaRenderer },
  { tester: textTester, renderer: MdTextFieldRenderer },
  ...vanillaRenderers
])

const control = { type: 'Control', scope: '#/properties/description', label: 'Description' }

let wrapper

const mountDescription = (data = {}) => {
  wrapper = mount(JsonForms, {
    props: {
      data,
      schema: { type: 'object', properties: { description: { type: 'string', maxLength: 280, default: '' } } },
      uischema: { type: 'VerticalLayout', elements: [control] },
      renderers,
      config: {},
      ajv: markRaw(createAjv({ useDefaults: true }))
    },
    attachTo: document.body
  })
  return wrapper
}

const area = () => wrapper.find('textarea')
const lastData = () => wrapper.emitted('change').at(-1)[0].data

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('MdTextAreaRenderer tester', () => {
  // JSON Forms hands a tester the root schema and resolves the scope itself, so the tests do the same.
  const rootWith = (schema) => ({ type: 'object', properties: { description: schema } })
  const longText = { type: 'string', maxLength: 280 }
  const shortText = { type: 'string' }

  it('takes a string whose limit is longer than a single line, above the text field', () => {
    const root = rootWith(longText)

    expect(areaTester(control, root), 'a long text outranks the text field').toBeGreaterThan(textTester(control, root))
  })

  it('leaves a short string to the text field', () => {
    expect(areaTester(control, rootWith(shortText))).toBe(-1)
  })

  it('leaves an enum to the select, whatever its limit', () => {
    expect(areaTester(control, rootWith({ type: 'string', enum: ['A'], maxLength: 280 }))).toBe(-1)
  })
})

describe('MdTextAreaRenderer', () => {
  it('draws the value as a multi-line field with the character count', () => {
    mountDescription({ description: 'Nightly builds' })

    expect(area().element.value).toBe('Nightly builds')
    expect(wrapper.text()).toContain('14 / 280')
  })

  it('writes the typed text into the data of the form', async () => {
    mountDescription({ description: '' })

    await area().setValue('Released artifacts')

    expect(lastData().description).toBe('Released artifacts')
  })

  it('writes an emptied field as no text, which is what the server default means too', async () => {
    mountDescription({ description: 'Nightly builds' })

    await area().setValue('')

    expect(lastData().description ?? '').toBe('')
  })
})
