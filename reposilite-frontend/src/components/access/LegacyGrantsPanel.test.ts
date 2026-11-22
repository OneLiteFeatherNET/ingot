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
import LegacyGrantsPanel from './LegacyGrantsPanel.vue'

const grants = [
  { path: '/releases', read: true, write: false },
  { path: '/snapshots', read: true, write: true }
]

describe('LegacyGrantsPanel', () => {
  it('lists the routes in a divided md-list', () => {
    const wrapper = mount(LegacyGrantsPanel, { props: { grants } })

    const list = wrapper.find('ul[data-divided]')
    expect(list.exists(), 'the routes are an md-list').toBe(true)
    expect(list.attributes('data-divided'), 'the list draws the rule between rows').toBe('true')
  })

  it('draws every route as an md-list-item and no hand-built divider', () => {
    const wrapper = mount(LegacyGrantsPanel, { props: { grants } })

    expect(wrapper.findAll('li[data-test="legacy-grant"] [data-test="list-item-row"]'), 'one list item per route').toHaveLength(2)
    expect(wrapper.findAll('[data-test="item-rule"]'), 'the list item draws the rule').toHaveLength(2)
    expect(wrapper.html(), 'no divide-y utility is left').not.toContain('divide-y')
  })

  it('says so when the token had no routes', () => {
    const wrapper = mount(LegacyGrantsPanel, { props: { grants: [] } })

    expect(wrapper.text(), 'the empty text shows').toContain('The token had no routes.')
    expect(wrapper.find('ul[data-divided]').exists(), 'no empty list').toBe(false)
  })
})
