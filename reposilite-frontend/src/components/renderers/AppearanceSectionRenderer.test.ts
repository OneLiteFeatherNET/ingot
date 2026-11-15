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
import { mount, flushPromises } from '@vue/test-utils'
import { markRaw } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { createAjv } from '@jsonforms/core'
import { vanillaRenderers } from '@dzikoysk/vue-vanilla'
import { tester as appearanceTester, default as AppearanceSectionRenderer } from './AppearanceSectionRenderer.vue'
import { tester as sectionTester, default as SectionRenderer } from './SectionRenderer.vue'
import { tester as objectTester, default as ObjectRenderer } from './ObjectRenderer.vue'
import { tester as colourTester, default as MdColourFieldRenderer } from './MdColourFieldRenderer.vue'
import { tester as textTester, default as MdTextFieldRenderer } from './MdTextFieldRenderer.vue'

const renderers = markRaw([
  { tester: appearanceTester, renderer: AppearanceSectionRenderer },
  { tester: colourTester, renderer: MdColourFieldRenderer },
  { tester: textTester, renderer: MdTextFieldRenderer },
  { tester: sectionTester, renderer: SectionRenderer },
  { tester: objectTester, renderer: ObjectRenderer },
  ...vanillaRenderers
])

const schema = {
  type: 'object',
  properties: {
    appearance: {
      type: 'object',
      title: 'Appearance',
      properties: {
        seedColor: { type: 'string', title: 'Seed colour' },
        footerText: { type: 'string', title: 'Footer text' }
      }
    }
  }
}

const mountSettings = (data) => mount(JsonForms, {
  props: {
    data,
    schema,
    uischema: { type: 'VerticalLayout', elements: [{ type: 'Control', scope: '#/properties/appearance' }] },
    renderers,
    config: { showUnfocusedDescription: true },
    ajv: markRaw(createAjv({ useDefaults: true }))
  }
})

describe('AppearanceSectionRenderer', () => {
  it('is used for the Appearance group only', () => {
    expect(appearanceTester({ type: 'Group', elements: [] }, schema.properties.appearance)).toBe(5)
    expect(appearanceTester({ type: 'Group', elements: [] }, { type: 'object', title: 'Maven' })).toBe(-1)
  })

  it('titles the group Appearance and puts the preview under its fields', async () => {
    const wrapper = mountSettings({ appearance: { seedColor: '#006a6a' } })
    await flushPromises()

    const group = wrapper.find('[data-test="appearance-section"]')
    expect(group.find('[data-test="section"] h3').text()).toBe('Appearance')
    const children = [...group.element.children].map(element => element.getAttribute('data-test'))
    expect(children, 'the preview comes after the section of fields').toEqual(['section', 'appearance-preview'])
  })

  it('shows the colour fields of the group as colour fields', async () => {
    const wrapper = mountSettings({ appearance: { seedColor: '#006a6a' } })
    await flushPromises()

    expect(wrapper.find('[data-test="colour-swatch"]').exists()).toBe(true)
    expect(wrapper.find('input[type="color"]').element.value).toBe('#006a6a')
  })

  it('draws the other fields of the group as before', async () => {
    const wrapper = mountSettings({ appearance: { seedColor: '#006a6a', footerText: 'Hosted here' } })
    await flushPromises()

    const values = wrapper.findAll('input').map(input => input.element.value)
    expect(values, 'the footer text stays a plain text field').toContain('Hosted here')
  })
})
