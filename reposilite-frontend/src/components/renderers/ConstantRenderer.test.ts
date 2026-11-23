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
import type { UISchemaElement } from '@jsonforms/core'
import { rendererOf, vanilla } from './testRenderers'
import ConstantRenderer, { tester as constantTester } from './ConstantRenderer.vue'

const renderers = markRaw([...vanilla, rendererOf(constantTester, ConstantRenderer)])

const mountConstant = () => mount(JsonForms, {
  props: {
    data: {},
    schema: { type: 'object', properties: { kind: { type: 'string', const: 'maven' } } },
    uischema: { type: 'VerticalLayout', elements: [{ type: 'Control', scope: '#/properties/kind', label: 'Kind' }] } as UISchemaElement,
    renderers,
    ajv: markRaw(createAjv({ useDefaults: true }))
  }
})

describe('constant renderer', () => {
  it('shows the label and the fixed value', () => {
    const wrapper = mountConstant()

    expect(wrapper.text()).toContain('Kind')
    expect(wrapper.text()).toContain('maven')
  })

  it('draws the constant as a list row of the shared list, with no inset of its own', () => {
    const wrapper = mountConstant()

    const row = wrapper.find('[data-test="constant-row"]')
    expect(row.element.tagName, 'the constant is a list item').toBe('LI')
    expect(row.element.closest('ul'), 'the row sits in a list').not.toBeNull()
    expect(row.classes(), 'no hand-written inset on the row').not.toContain('pl-4')
  })
})
