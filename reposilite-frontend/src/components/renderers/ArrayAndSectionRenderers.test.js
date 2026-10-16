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
import { tester as arrayTester } from './ArrayListRenderer.vue'
import ArrayListRenderer from './ArrayListRenderer.vue'
import { tester as sectionTester } from './SectionRenderer.vue'
import SectionRenderer from './SectionRenderer.vue'
import { tester as textTester } from './MdTextFieldRenderer.vue'
import MdTextFieldRenderer from './MdTextFieldRenderer.vue'
import { tester as selectTester } from './MdSelectRenderer.vue'
import MdSelectRenderer from './MdSelectRenderer.vue'

const renderers = markRaw([
  { tester: arrayTester, renderer: ArrayListRenderer },
  { tester: sectionTester, renderer: SectionRenderer },
  { tester: textTester, renderer: MdTextFieldRenderer },
  { tester: selectTester, renderer: MdSelectRenderer },
  ...vanillaRenderers
])

const mountForm = (schema, uischema, data) => mount(JsonForms, {
  props: {
    data,
    schema,
    uischema,
    renderers,
    config: { showUnfocusedDescription: true },
    ajv: markRaw(createAjv({ useDefaults: true }))
  }
})

const lastData = (wrapper) => wrapper.emitted('change').at(-1)[0].data

const listSchema = {
  type: 'object',
  properties: {
    allowedGroups: {
      type: 'array',
      title: 'Allowed Groups',
      items: { type: 'string', title: 'Group' }
    }
  }
}

const listLayout = {
  type: 'VerticalLayout',
  elements: [{ type: 'Control', scope: '#/properties/allowedGroups' }]
}

const mirrorSchema = {
  type: 'object',
  properties: {
    mirrors: {
      type: 'array',
      title: 'Mirrors',
      items: {
        type: 'object',
        title: 'Mirror',
        properties: {
          link: { type: 'string', title: 'Link' }
        }
      }
    }
  }
}

const mirrorLayout = {
  type: 'VerticalLayout',
  elements: [{ type: 'Control', scope: '#/properties/mirrors' }]
}

describe('array of plain values', () => {
  it('draws one row per value, each with its own text field', () => {
    const wrapper = mountForm(listSchema, listLayout, { allowedGroups: ['com.example', 'org.demo'] })

    const fields = wrapper.findAll('input')
    expect(fields.map(field => field.element.value)).toEqual(['com.example', 'org.demo'])
  })

  it('adds an empty row when Add is pressed, so the data grows by one', async () => {
    const wrapper = mountForm(listSchema, listLayout, { allowedGroups: ['com.example'] })

    await wrapper.findAll('button').find(button => button.text().startsWith('Add')).trigger('click')

    expect(lastData(wrapper).allowedGroups).toHaveLength(2)
  })

  it('removes the row whose remove button is pressed, and keeps the others', async () => {
    const wrapper = mountForm(listSchema, listLayout, { allowedGroups: ['com.example', 'org.demo'] })

    await wrapper.findAll('button[aria-label="Remove group 1"]').at(0).trigger('click')

    expect(lastData(wrapper).allowedGroups).toEqual(['org.demo'])
  })

  it('writes an edited text into its own position in the array', async () => {
    const wrapper = mountForm(listSchema, listLayout, { allowedGroups: ['com.example', 'org.demo'] })

    await wrapper.findAll('input').at(1).setValue('org.other')

    expect(lastData(wrapper).allowedGroups).toEqual(['com.example', 'org.other'])
  })

  it('shows a plain empty message instead of a list when there are no entries', () => {
    const wrapper = mountForm(listSchema, listLayout, { allowedGroups: [] })

    expect(wrapper.text()).toContain('No entries yet.')
    expect(wrapper.findAll('input')).toHaveLength(0)
  })
})

describe('array of objects', () => {
  it('draws each object as an expandable item with its fields inside', () => {
    const wrapper = mountForm(mirrorSchema, mirrorLayout, { mirrors: [{ link: 'https://jitpack.io' }] })

    expect(wrapper.find('details').exists()).toBe(true)
    expect(wrapper.find('details input').element.value).toBe('https://jitpack.io')
  })

  it('removes an object item and its data', async () => {
    const wrapper = mountForm(mirrorSchema, mirrorLayout, {
      mirrors: [{ link: 'https://jitpack.io' }, { link: 'https://repo.example' }]
    })

    await wrapper.findAll('button[aria-label="Remove mirror 1"]').at(0).trigger('click')

    expect(lastData(wrapper).mirrors).toEqual([{ link: 'https://repo.example' }])
  })
})

describe('group section', () => {
  it('draws a group as a section with its label as a heading, not as a fieldset', () => {
    const schema = { type: 'object', properties: { name: { type: 'string', title: 'Name' } } }
    const layout = {
      type: 'VerticalLayout',
      elements: [{ type: 'Group', label: 'General', elements: [{ type: 'Control', scope: '#/properties/name' }] }]
    }

    const wrapper = mountForm(schema, layout, { name: 'releases' })

    expect(wrapper.find('fieldset').exists(), 'the fieldset with the notched legend is gone').toBe(false)
    expect(wrapper.get('[data-test="section"] h3').text()).toBe('General')
  })
})
