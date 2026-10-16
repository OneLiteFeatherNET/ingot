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
import { JsonForms } from '@jsonforms/vue'
import { vanillaRenderers } from '@dzikoysk/vue-vanilla'
import { tester as booleanSwitchTester, default as BooleanSwitchRenderer } from './BooleanSwitchRenderer.vue'
import { tester as optionalTester, default as OptionalRenderer } from './OptionalRenderer.vue'
import { tester as objectTester, default as ObjectRenderer } from './ObjectRenderer.vue'
import { tester as arrayTester, default as ArrayListRenderer } from './ArrayListRenderer.vue'
import { tester as sectionTester, default as SectionRenderer } from './SectionRenderer.vue'

const schema = {
  type: 'object',
  properties: {
    proxiedCredentials: {
      title: 'Proxied credentials',
      type: ['object', 'null'],
      properties: {
        username: { type: 'string' }
      }
    }
  }
}

const uischema = {
  type: 'VerticalLayout',
  elements: [{ type: 'Control', scope: '#/properties/proxiedCredentials' }]
}

const renderers = [
  { tester: booleanSwitchTester, renderer: BooleanSwitchRenderer },
  { tester: optionalTester, renderer: OptionalRenderer },
  ...vanillaRenderers
]

const mountForm = (data = {}) => mount(JsonForms, {
  props: { data, schema, uischema, renderers }
})

describe('OptionalRenderer', () => {
  it('claims an optional object property above the vanilla rank', () => {
    const control = { type: 'Control', scope: '#/properties/proxiedCredentials' }

    expect(optionalTester(control, schema)).toBe(2)
  })

  it('offers no native checkbox for the optional toggle', () => {
    const wrapper = mountForm()

    expect(wrapper.find('input[type="checkbox"]').exists(), 'the optional toggle must not be a native checkbox').toBe(false)
  })

  it('shows the optional toggle as a material switch that is off while the value is absent', () => {
    const wrapper = mountForm()

    expect(wrapper.find('[role="switch"]').attributes('aria-checked')).toBe('false')
  })

  it('labels the optional switch with the property title', () => {
    const wrapper = mountForm()
    const switchButton = wrapper.find('[role="switch"]')
    const label = wrapper.find(`[id="${switchButton.attributes('aria-labelledby')}"]`)

    expect(label.exists(), 'the switch must be named by a label').toBe(true)
    expect(label.text()).toContain('Proxied credentials')
  })

  it('reveals the nested fields when the optional switch is turned on', async () => {
    const wrapper = mountForm()

    await wrapper.find('[role="switch"]').trigger('click')

    expect(wrapper.find('[role="switch"]').attributes('aria-checked')).toBe('true')
    expect(wrapper.find('fieldset input').exists(), 'the nested fields must appear').toBe(true)
  })

  it('starts on when the data already holds the optional value', () => {
    const wrapper = mountForm({ proxiedCredentials: { username: 'admin' } })

    expect(wrapper.find('[role="switch"]').attributes('aria-checked')).toBe('true')
  })
})

describe('optional object under its switch row', () => {
  // The production set: the section and object renderers decide how the nested fields are drawn.
  const fullRenderers = [
    { tester: booleanSwitchTester, renderer: BooleanSwitchRenderer },
    { tester: optionalTester, renderer: OptionalRenderer },
    { tester: sectionTester, renderer: SectionRenderer },
    { tester: objectTester, renderer: ObjectRenderer },
    ...vanillaRenderers
  ]
  const mountFull = (data = {}) => mount(JsonForms, {
    props: { data, schema, uischema, renderers: fullRenderers }
  })

  it('shows the object title once, on the switch row, and not again above its fields', async () => {
    const wrapper = mountFull()

    await wrapper.find('[role="switch"]').trigger('click')

    expect(wrapper.text().split('Proxied credentials').length - 1, 'the title must appear only on the switch row').toBe(1)
    expect(wrapper.find('h3').exists(), 'no heading may repeat the title').toBe(false)
  })

  it('draws the fields as a sub-group without a border box', async () => {
    const wrapper = mountFull()

    await wrapper.find('[role="switch"]').trigger('click')

    expect(wrapper.find('.border-1').exists(), 'the fields must not sit in a bordered box').toBe(false)
    expect(wrapper.find('fieldset').exists(), 'the fields must not sit in a fieldset').toBe(false)
  })

  it('hides the fields again when the optional switch is turned off', async () => {
    const wrapper = mountFull({ proxiedCredentials: { username: 'admin' } })

    await wrapper.find('[role="switch"]').trigger('click')

    expect(wrapper.find('[role="switch"]').attributes('aria-checked')).toBe('false')
    expect(wrapper.find('input').exists(), 'the fields must be gone while the object is disabled').toBe(false)
  })
})

describe('nested object inside an array item', () => {
  const schema = {
    type: 'object',
    properties: {
      mirrors: {
        type: 'array',
        title: 'Mirrors',
        items: {
          type: 'object',
          title: 'Mirror',
          properties: {
            auth: {
              type: 'object',
              title: 'Auth',
              properties: { login: { type: 'string', title: 'Login' } }
            }
          }
        }
      }
    }
  }
  const uischema = {
    type: 'VerticalLayout',
    elements: [{ type: 'Control', scope: '#/properties/mirrors' }]
  }
  const arrayRenderers = [
    { tester: arrayTester, renderer: ArrayListRenderer },
    { tester: booleanSwitchTester, renderer: BooleanSwitchRenderer },
    { tester: optionalTester, renderer: OptionalRenderer },
    { tester: sectionTester, renderer: SectionRenderer },
    { tester: objectTester, renderer: ObjectRenderer },
    ...vanillaRenderers
  ]

  it('draws the nested heading inside the card with no bordered box in between', () => {
    const wrapper = mount(JsonForms, {
      props: { data: { mirrors: [{ auth: { login: 'admin' } }] }, schema, uischema, renderers: arrayRenderers }
    })

    const heading = wrapper.get('details h3')
    expect(heading.text()).toBe('Auth')
    expect(heading.classes()).toContain('md-title-small')
    let node = heading.element.parentElement
    while (node && node.tagName !== 'DETAILS') {
      expect(node.className.toString(), `no wrapper between the heading and the card may draw a border (${node.tagName})`).not.toMatch(/\bborder(-1)?\b/)
      node = node.parentElement
    }
  })
})
