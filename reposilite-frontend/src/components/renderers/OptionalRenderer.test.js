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
