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
import UnsupportedRenderer, { tester as unsupportedTester } from './UnsupportedRenderer.vue'

// The order the settings view uses: the fallback comes after every renderer that can draw a field.
const renderers = markRaw([
  ...vanillaRenderers,
  { tester: unsupportedTester, renderer: UnsupportedRenderer }
])

const FALLBACK = 'This section cannot be edited here, use Download as JSON'

const mountField = (schema, data = {}) => mount(JsonForms, {
  props: {
    data,
    schema: { type: 'object', properties: { field: schema } },
    uischema: { type: 'VerticalLayout', elements: [{ type: 'Control', scope: '#/properties/field', label: 'Field' }] },
    renderers,
    config: { showUnfocusedDescription: true },
    ajv: markRaw(createAjv({ useDefaults: true }))
  }
})

describe('unsupported schema fallback', () => {
  it('shows the fallback text, with the label of the field, for a field no renderer can draw', () => {
    const wrapper = mountField({ type: 'null' })

    expect(wrapper.text(), 'the fallback must not be silent').toContain(FALLBACK)
    expect(wrapper.text(), 'the label names the field').toContain('Field')
  })

  it('does not replace a field the form can draw', () => {
    const wrapper = mountField({ type: 'string' }, { field: 'value' })

    expect(wrapper.find('input').element.value).toBe('value')
    expect(wrapper.text()).not.toContain(FALLBACK)
  })
})
