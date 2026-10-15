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

const mountField = (key, schema, data = { [key]: 'hunter2' }) => mount(JsonForms, {
  props: {
    data,
    schema: { type: 'object', properties: { [key]: schema } },
    uischema: { type: 'VerticalLayout', elements: [{ type: 'Control', scope: `#/properties/${key}`, label: 'Field' }] },
    renderers,
    config: { showUnfocusedDescription: true },
    ajv: markRaw(createAjv({ useDefaults: true }))
  }
})

const toggleOf = (wrapper) => wrapper.find('button[aria-label]')

describe('MdTextFieldRenderer secrets', () => {
  it('masks a property whose key names a secret', () => {
    const wrapper = mountField('secretKey', { type: 'string' })

    expect(wrapper.find('input').attributes('type')).toBe('password')
  })

  it('masks a property whose key names a password, whatever its case', () => {
    const wrapper = mountField('Proxy_PASSWORD', { type: 'string' }, { Proxy_PASSWORD: 'pw' })

    expect(wrapper.find('input').attributes('type')).toBe('password')
  })

  it('masks a string with the password format whatever its key', () => {
    const wrapper = mountField('token', { type: 'string', format: 'password' }, { token: 'abc' })

    expect(wrapper.find('input').attributes('type')).toBe('password')
  })

  it('leaves an ordinary string visible and offers no toggle for it', () => {
    const wrapper = mountField('bucket', { type: 'string' }, { bucket: 'releases' })

    expect(wrapper.find('input').attributes('type')).toBe('text')
    expect(toggleOf(wrapper).exists()).toBe(false)
  })

  it('offers a toggle named Show secret, unpressed, while the secret is masked', () => {
    const wrapper = mountField('secretKey', { type: 'string' })

    const toggle = wrapper.find('button[aria-label="Show secret"]')
    expect(toggle.exists()).toBe(true)
    expect(toggle.attributes('aria-pressed')).toBe('false')
  })

  it('reveals the secret and names the toggle Hide secret, pressed, when clicked', async () => {
    const wrapper = mountField('secretKey', { type: 'string' })

    await wrapper.find('button[aria-label="Show secret"]').trigger('click')

    expect(wrapper.find('input').attributes('type')).toBe('text')
    const toggle = wrapper.find('button[aria-label="Hide secret"]')
    expect(toggle.exists()).toBe(true)
    expect(toggle.attributes('aria-pressed')).toBe('true')
  })

  it('masks the secret again on a second click', async () => {
    const wrapper = mountField('secretKey', { type: 'string' })

    await wrapper.find('button[aria-label="Show secret"]').trigger('click')
    await wrapper.find('button[aria-label="Hide secret"]').trigger('click')

    expect(wrapper.find('input').attributes('type')).toBe('password')
    expect(wrapper.find('button[aria-label="Show secret"]').attributes('aria-pressed')).toBe('false')
  })

  it('keeps the toggle reachable to assistive technology', () => {
    const wrapper = mountField('secretKey', { type: 'string' })

    expect(toggleOf(wrapper).element.closest('[aria-hidden="true"]')).toBeNull()
  })

  it('masks an access key and an endpoint with the same toggle', () => {
    for (const key of ['accessKey', 'endpoint']) {
      const wrapper = mountField(key, { type: 'string' }, { [key]: 'value' })

      expect(wrapper.find('input').attributes('type'), key).toBe('password')
      expect(toggleOf(wrapper).exists(), key).toBe(true)
    }
  })

  it('leaves the bucket, the region and the id visible', () => {
    for (const key of ['bucketName', 'region', 'id']) {
      const wrapper = mountField(key, { type: 'string' }, { [key]: 'value' })

      expect(wrapper.find('input').attributes('type'), key).toBe('text')
    }
  })

  it('asks the browser not to fill a masked secret', () => {
    const wrapper = mountField('secretKey', { type: 'string' })

    expect(wrapper.find('input').attributes('autocomplete')).toBe('new-password')
  })

  it('turns off spellcheck for a masked secret', () => {
    const wrapper = mountField('secretKey', { type: 'string' })

    expect(wrapper.find('input').attributes('spellcheck')).toBe('false')
  })

  it('leaves autocomplete and spellcheck alone for an ordinary string', () => {
    const wrapper = mountField('bucket', { type: 'string' }, { bucket: 'releases' })

    expect(wrapper.find('input').attributes('autocomplete')).toBeUndefined()
    expect(wrapper.find('input').attributes('spellcheck')).not.toBe('false')
  })

  it('masks again when the control is bound to another property', async () => {
    const schema = { type: 'object', properties: { secretKey: { type: 'string' }, accessKey: { type: 'string' } } }
    const data = { secretKey: 'hunter2', accessKey: 'AKIA' }
    const wrapper = mount(JsonForms, {
      props: {
        data,
        schema,
        uischema: { type: 'Control', scope: '#/properties/secretKey', label: 'Field' },
        renderers,
        config: { showUnfocusedDescription: true },
        ajv: markRaw(createAjv({ useDefaults: true }))
      }
    })
    await wrapper.find('button[aria-label="Show secret"]').trigger('click')
    expect(wrapper.find('input').attributes('type')).toBe('text')

    await wrapper.setProps({ uischema: { type: 'Control', scope: '#/properties/accessKey', label: 'Field' } })

    expect(wrapper.find('input').attributes('type')).toBe('password')
    expect(wrapper.find('button[aria-label="Show secret"]').exists()).toBe(true)
  })

  it('still writes the typed secret into the data', async () => {
    const wrapper = mountField('secretKey', { type: 'string' })

    await wrapper.find('input').setValue('new-value')

    expect(wrapper.emitted('change').at(-1)[0].data.secretKey).toBe('new-value')
  })
})
