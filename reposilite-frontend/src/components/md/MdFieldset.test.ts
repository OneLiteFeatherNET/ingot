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
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import MdFieldset from './MdFieldset.vue'

describe('MdFieldset', () => {
  it('draws its root as a fieldset', () => {
    const wrapper = mount(MdFieldset, { props: { legend: 'Access' } })

    expect((wrapper.element as HTMLElement).tagName, 'the group of controls must be a native fieldset').toBe('FIELDSET')
  })

  it('draws the title as a legend and not as a span', () => {
    const wrapper = mount(MdFieldset, { props: { legend: 'Access' } })

    expect(wrapper.get('legend').text(), 'the title must be the legend of the fieldset').toBe('Access')
    expect(wrapper.find('span').exists(), 'no span may stand in for the legend').toBe(false)
  })

  it('sets the legend in the title-small type role', () => {
    const wrapper = mount(MdFieldset, { props: { legend: 'Access' } })

    expect(wrapper.get('legend').classes(), 'the legend must use md-title-small').toContain('md-title-small')
  })

  it('renders the default slot as the controls of the group', () => {
    const wrapper = mount(MdFieldset, {
      props: { legend: 'Access' },
      slots: { default: () => h('input', { 'data-test': 'access-read', type: 'radio' }) }
    })

    expect(wrapper.find('fieldset [data-test="access-read"]').exists(), 'the slot content must sit inside the fieldset').toBe(true)
  })

  it('carries the md-fieldset anchor on its root', () => {
    const wrapper = mount(MdFieldset, { props: { legend: 'Access' } })

    expect(wrapper.attributes('data-ingot-component')).toBe('md-fieldset')
  })

  it('passes attributes such as data-test through to the fieldset', () => {
    const wrapper = mount(MdFieldset, { props: { legend: 'Access' }, attrs: { 'data-test': 'artifact-limit' } })

    expect(wrapper.attributes('data-test'), 'a caller hook must land on the fieldset').toBe('artifact-limit')
  })
})
