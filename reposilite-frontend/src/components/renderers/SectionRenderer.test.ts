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
import type { JsonFormsRendererRegistryEntry, JsonSchema7, UISchemaElement } from '@jsonforms/core'
import { vanillaRenderers } from '@dzikoysk/vue-vanilla'
import { tester as objectTester, default as ObjectRenderer } from './ObjectRenderer.vue'
import { tester as sectionTester, default as SectionRenderer } from './SectionRenderer.vue'
import { tester as textTester, default as MdTextFieldRenderer } from './MdTextFieldRenderer.vue'

const rendererOf = (tester: unknown, renderer: unknown): JsonFormsRendererRegistryEntry => ({ tester, renderer } as JsonFormsRendererRegistryEntry)

const renderers = markRaw([
  rendererOf(objectTester, ObjectRenderer),
  rendererOf(sectionTester, SectionRenderer),
  rendererOf(textTester, MdTextFieldRenderer),
  ...(vanillaRenderers as JsonFormsRendererRegistryEntry[])
])

const mountForm = (schema: JsonSchema7, uischema: UISchemaElement, data: Record<string, unknown>) => mount(JsonForms, {
  props: {
    data,
    schema,
    uischema,
    renderers,
    config: { showUnfocusedDescription: true },
    ajv: markRaw(createAjv({ useDefaults: true }))
  }
})

const rootGroupSchema: JsonSchema7 = {
  type: 'object',
  properties: {
    name: { type: 'string', title: 'Name' }
  }
}

const rootGroupLayout = {
  type: 'VerticalLayout',
  elements: [{ type: 'Group', label: 'General', elements: [{ type: 'Control', scope: '#/properties/name' }] }]
} as UISchemaElement

const nestedObjectSchema: JsonSchema7 = {
  type: 'object',
  properties: {
    outer: {
      type: 'object',
      title: 'Outer',
      properties: {
        inner: {
          type: 'object',
          title: 'Inner',
          properties: {
            name: { type: 'string', title: 'Name' }
          }
        }
      }
    }
  }
}

const nestedObjectLayout = {
  type: 'VerticalLayout',
  elements: [{ type: 'Control', scope: '#/properties/outer' }]
} as UISchemaElement

describe('SectionRenderer', () => {
  it('draws a section at the root of the form as a level 2 heading in the title-medium role', () => {
    const wrapper = mountForm(rootGroupSchema, rootGroupLayout, { name: 'releases' })

    const heading = wrapper.get('[data-test="section"] h2')
    expect(heading.text(), 'the root section keeps its label').toBe('General')
    expect(heading.classes(), 'a root section title uses md-title-medium').toContain('md-title-medium')
    expect(wrapper.find('[data-test="section"] h3').exists(), 'the root section has no level 3 heading').toBe(false)
  })

  it('draws a section inside an object as a level 3 heading in the title-small role', () => {
    const wrapper = mountForm(nestedObjectSchema, nestedObjectLayout, { outer: { inner: { name: 'releases' } } })

    const heading = wrapper.get('[data-test="section"] h3')
    expect(heading.text(), 'the nested section keeps its label').toBe('Outer')
    expect(heading.classes(), 'a nested section title uses md-title-small').toContain('md-title-small')
  })

  it('clamps sections nested two objects deep at level 3 and never draws a level 4 heading', () => {
    const wrapper = mountForm(nestedObjectSchema, nestedObjectLayout, { outer: { inner: { name: 'releases' } } })

    expect(wrapper.find('h4').exists(), 'no heading is level 4 however deep the sections nest').toBe(false)
    expect(wrapper.findAll('h3').map(heading => heading.text()), 'both nested sections are level 3').toEqual(['Outer', 'Inner'])
  })
})
