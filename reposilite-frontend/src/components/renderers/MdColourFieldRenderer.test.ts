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

import { describe, it, expect, vi } from 'vitest'
import { mount, type DOMWrapper } from '@vue/test-utils'
import { markRaw } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { createAjv } from '@jsonforms/core'
import type { ControlElement, JsonSchema7, UISchemaElement } from '@jsonforms/core'
import { vanillaRenderers } from '@dzikoysk/vue-vanilla'
import { tester as colourTester } from './MdColourFieldRenderer.vue'
import MdColourFieldRenderer from './MdColourFieldRenderer.vue'
import { tester as textTester } from './MdTextFieldRenderer.vue'
import MdTextFieldRenderer from './MdTextFieldRenderer.vue'

const renderers = markRaw([
  { tester: colourTester, renderer: MdColourFieldRenderer },
  { tester: textTester, renderer: MdTextFieldRenderer },
  ...vanillaRenderers
])

const mountColour = (name: string, schema: JsonSchema7, data: Record<string, unknown>) => mount(JsonForms, {
  props: {
    data,
    schema: { type: 'object', properties: { [name]: schema } },
    uischema: { type: 'VerticalLayout', elements: [{ type: 'Control', scope: `#/properties/${name}`, label: 'Seed colour' }] } as UISchemaElement,
    renderers,
    config: { showUnfocusedDescription: true },
    ajv: markRaw(createAjv({ useDefaults: true }))
  }
})

const seed = (data: { value?: string } = {}) => mountColour('seedColor', { type: 'string' }, { seedColor: data.value })
type Seeded = ReturnType<typeof seed>

const lastData = (wrapper: Seeded) => (wrapper.emitted('change') as Array<[{ data: Record<string, unknown> }]>).at(-1)![0].data
const textInput = (wrapper: Seeded) => wrapper.find('input[type="text"]') as DOMWrapper<HTMLInputElement>
const picker = (wrapper: Seeded) => wrapper.find('input[type="color"]') as DOMWrapper<HTMLInputElement>
const clearButton = (wrapper: Seeded) => wrapper.findAll('button').find(button => button.text() === 'Clear') as DOMWrapper<HTMLButtonElement>

describe('MdColourFieldRenderer tester', () => {
  const schema: JsonSchema7 = { type: 'string' }
  const context = { rootSchema: schema, config: undefined }
  const control = (scope: string) => ({ type: 'Control', scope }) as ControlElement

  it('takes the colour settings by name, above the text field', () => {
    const colour = colourTester(control('#/properties/appearance/properties/primaryColor'), schema, context)

    expect(colour, 'a colour field outranks the text field').toBeGreaterThan(textTester(control('#/properties/x'), schema, context))
  })

  it('takes every named colour setting', () => {
    for (const name of ['seedColor', 'primaryColor', 'secondaryColor', 'tertiaryColor', 'errorColor']) {
      expect(colourTester(control(`#/properties/${name}`), schema, context), name).toBeGreaterThan(-1)
    }
  })

  it('takes a string that declares the colour format, whatever its name', () => {
    expect(colourTester(control('#/properties/accent'), { type: 'string', format: 'color' }, context)).toBeGreaterThan(-1)
  })

  it('leaves other strings to the text field', () => {
    expect(colourTester(control('#/properties/title'), schema, context)).toBe(-1)
  })
})

describe('MdColourFieldRenderer', () => {
  it('draws the colour as a labelled text field holding the value', () => {
    const wrapper = seed({ value: '#006a6a' })

    expect(textInput(wrapper).element.value).toBe('#006a6a')
    expect(wrapper.find('label').text()).toBe('Seed colour')
  })

  it('shows the colour in a swatch in front of the text', () => {
    const wrapper = seed({ value: '#006a6a' })

    expect(wrapper.find('[data-test="colour-swatch"]').attributes('data-colour')).toBe('#006a6a')
  })

  it('writes a typed colour into the data', async () => {
    const wrapper = seed({ value: '' })

    await textInput(wrapper).setValue('#8a1c1c')

    expect(lastData(wrapper).seedColor).toBe('#8a1c1c')
  })

  it('reports a value that is not #RRGGBB and keeps it, so the settings cannot be saved', async () => {
    const wrapper = seed({ value: '#006a6a' })

    await textInput(wrapper).setValue('teal')

    expect(wrapper.text()).toContain('Use a colour in the form #RRGGBB')
    expect(textInput(wrapper).attributes('aria-invalid')).toBe('true')
    expect(lastData(wrapper).seedColor, 'the typed text is kept for the validity check').toBe('teal')
  })

  it('rejects the three digit form, which the editor does not accept', async () => {
    const wrapper = seed({ value: '' })

    await textInput(wrapper).setValue('#abc')

    expect(wrapper.text()).toContain('Use a colour in the form #RRGGBB')
  })

  it('accepts an emptied field, which means not set, and removes the value from the data', async () => {
    const wrapper = seed({ value: '#006a6a' })

    await textInput(wrapper).setValue('')

    expect(wrapper.text()).not.toContain('Use a colour in the form #RRGGBB')
    expect(lastData(wrapper).seedColor).toBeUndefined()
  })

  it('shows the colour of a picked value in the text field', async () => {
    const wrapper = seed({ value: '' })

    await picker(wrapper).setValue('#00aa33')

    expect(textInput(wrapper).element.value).toBe('#00aa33')
    expect(lastData(wrapper).seedColor).toBe('#00aa33')
  })

  it('shows the colour of the text in the native picker', async () => {
    const wrapper = seed({ value: '#ABCDEF' })

    expect(picker(wrapper).element.value, 'the picker takes lower case').toBe('#abcdef')

    await textInput(wrapper).setValue('#123456')

    expect(picker(wrapper).element.value).toBe('#123456')
  })

  it('keeps the native picker at black while the text is not a colour', async () => {
    const wrapper = seed({ value: '' })

    await textInput(wrapper).setValue('teal')

    expect(picker(wrapper).element.value).toBe('#000000')
  })

  it('hides the native picker from assistive technology and from the tab order', () => {
    const wrapper = seed({ value: '#006a6a' })

    expect(picker(wrapper).attributes('aria-hidden')).toBe('true')
    expect(picker(wrapper).attributes('tabindex')).toBe('-1')
  })

  it('opens the native picker from its icon button', async () => {
    const wrapper = seed({ value: '#006a6a' })
    const open = vi.spyOn(picker(wrapper).element, 'click').mockImplementation(() => {})

    await wrapper.find('button[aria-label="Choose a colour for Seed colour"]').trigger('click')

    expect(open).toHaveBeenCalledTimes(1)
  })

  it('clears the colour with the Clear action', async () => {
    const wrapper = seed({ value: '#006a6a' })

    await clearButton(wrapper).trigger('click')

    expect(lastData(wrapper).seedColor).toBeUndefined()
  })

  it('disables Clear while there is nothing to clear', () => {
    const wrapper = seed({ value: '' })

    expect(clearButton(wrapper).attributes('disabled')).toBeDefined()
  })
})
