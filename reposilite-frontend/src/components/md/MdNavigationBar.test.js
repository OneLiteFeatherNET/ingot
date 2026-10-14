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
import MdNavigationBar from './MdNavigationBar.vue'

// The five dashboard tabs, as the navigation bar is meant to show them.
const TABS = [
  { key: 'Overview', label: 'Overview' },
  { key: 'Dashboard', label: 'Dashboard' },
  { key: 'Tokens', label: 'Tokens' },
  { key: 'Console', label: 'Console' },
  { key: 'Settings', label: 'Settings' }
]

describe('MdNavigationBar', () => {
  it('renders one entry per destination', () => {
    const wrapper = mount(MdNavigationBar, { props: { items: TABS, modelValue: 'Overview' } })

    expect(wrapper.findAll('li')).toHaveLength(5)
  })

  it('labels the navigation landmark', () => {
    const wrapper = mount(MdNavigationBar, {
      props: { items: TABS, modelValue: 'Overview', label: 'Dashboard sections' }
    })

    expect(wrapper.find('nav').attributes('aria-label')).toBe('Dashboard sections')
  })

  it('marks the active destination with aria-current', () => {
    const wrapper = mount(MdNavigationBar, { props: { items: TABS, modelValue: 'Tokens' } })

    const current = wrapper.findAll('[aria-current="page"]')
    expect(current).toHaveLength(1)
    expect(current[0].text()).toBe('Tokens')
  })

  it('emits update:modelValue with the key of the clicked destination', async () => {
    const wrapper = mount(MdNavigationBar, { props: { items: TABS, modelValue: 'Overview' } })

    await wrapper.findAll('button')[3].trigger('click')

    expect(wrapper.emitted('update:modelValue')[0]).toEqual(['Console'])
  })

  it('renders a link for a destination that has an href', () => {
    const items = [{ key: 'docs', label: 'Docs', href: '/docs' }]
    const wrapper = mount(MdNavigationBar, { props: { items, modelValue: 'docs' } })

    expect(wrapper.find('a').attributes('href')).toBe('/docs')
    expect(wrapper.find('button').exists()).toBe(false)
  })
})
