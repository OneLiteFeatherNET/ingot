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
import { tester as switchTester } from './BooleanSwitchRenderer.vue'
import BooleanSwitchRenderer from './BooleanSwitchRenderer.vue'

const renderers = markRaw([
  { tester: textTester, renderer: MdTextFieldRenderer },
  { tester: switchTester, renderer: BooleanSwitchRenderer },
  ...vanillaRenderers
])

const mountField = (key, schema, data) => mount(JsonForms, {
  props: {
    data,
    schema: { type: 'object', properties: { [key]: schema } },
    uischema: { type: 'VerticalLayout', elements: [{ type: 'Control', scope: `#/properties/${key}`, label: 'Field' }] },
    renderers,
    config: { showUnfocusedDescription: true },
    ajv: markRaw(createAjv({ useDefaults: true }))
  }
})

describe('schema descriptions', () => {
  it('renders a br tag in a text field description as a line break, not as markup', () => {
    const wrapper = mountField('proxy', {
      type: 'string',
      description: 'Examples: <br/> HTTP 127.0.0.1:1081 <br/> SOCKS 127.0.0.1:1080'
    }, { proxy: 'x' })

    const helper = wrapper.find('p').element
    expect(helper.textContent.trim()).toBe('Examples:\nHTTP 127.0.0.1:1081\nSOCKS 127.0.0.1:1080')
    expect(wrapper.find('br').exists()).toBe(false)
    expect(helper.innerHTML).not.toContain('&lt;br')
  })

  it('renders a br tag in a switch description as a line break', () => {
    const wrapper = mountField('enabled', {
      type: 'boolean',
      description: 'First line<br/>Second line'
    }, { enabled: true })

    const helper = wrapper.find('p').element
    expect(helper.textContent.trim()).toBe('First line\nSecond line')
    expect(wrapper.find('br').exists()).toBe(false)
  })
})
