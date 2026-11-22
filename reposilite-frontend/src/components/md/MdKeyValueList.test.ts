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
import MdKeyValueList from './MdKeyValueList.vue'

const rows = [
  { label: 'Name', value: 'ingot' },
  { label: 'Version', value: '1.0.0' }
]

describe('MdKeyValueList', () => {
  it('renders a definition list with a term and a description for each item', () => {
    const wrapper = mount(MdKeyValueList, { props: { items: rows } })

    expect((wrapper.element as HTMLElement).tagName).toBe('DL')
    expect(wrapper.findAll('dt').map(term => term.text())).toEqual(['Name', 'Version'])
    expect(wrapper.findAll('dd').map(detail => detail.text())).toEqual(['ingot', '1.0.0'])
  })

  it('keeps each term with its own value', () => {
    const wrapper = mount(MdKeyValueList, { props: { items: rows } })

    const firstRow = wrapper.get('[data-test="key-value-row"]')

    expect(firstRow.findAll('dt, dd').map(cell => cell.text())).toEqual(['Name', 'ingot'])
  })

  it('renders rows authored in the default slot after the items', () => {
    const wrapper = mount(MdKeyValueList, {
      props: { items: rows },
      slots: { default: () => [h('dt', 'Owner'), h('dd', 'team-core')] }
    })

    expect(wrapper.findAll('dt').map(term => term.text())).toEqual(['Name', 'Version', 'Owner'])
  })

  it('lays every row on one two-column grid template', () => {
    const wrapper = mount(MdKeyValueList, { props: { items: rows } })

    expect(wrapper.classes()).toContain('grid')
    expect(wrapper.classes()).toContain('grid-cols-2')
    expect(wrapper.findAll('[data-test="key-value-row"]').every(row => row.classes().includes('contents'))).toBe(true)
  })

  it('puts the testId of an item on its value cell as data-test', () => {
    const wrapper = mount(MdKeyValueList, {
      props: { items: [{ label: 'Status', value: 'Active', testId: 'status' }, { label: 'Name', value: 'ingot' }] }
    })

    expect(wrapper.get('[data-test="status"]').element.tagName, 'the value cell carries the hook').toBe('DD')
    expect(wrapper.get('[data-test="status"]').text()).toBe('Active')
    expect(wrapper.findAll('dd')[1].attributes('data-test'), 'an item without a testId has no hook').toBeUndefined()
  })

  it('carries the md-key-value-list anchor on its root', () => {
    expect(mount(MdKeyValueList, { props: { items: rows } }).attributes('data-ingot-component')).toBe('md-key-value-list')
  })
})
