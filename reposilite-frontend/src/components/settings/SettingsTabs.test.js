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
import SettingsTabs from './SettingsTabs.vue'

const tabs = [
  { key: 'maven', label: 'Maven' },
  { key: 'ingot', label: 'Ingot' },
  { key: 'cdn', label: 'CDN' }
]

const mountTabs = (props = {}) => mount(SettingsTabs, {
  props: { tabs, modelValue: 'maven', label: 'Domains', ...props }
})

const tabButtons = (wrapper) => wrapper.findAll('[role="tab"]')

describe('SettingsTabs', () => {
  it('marks only the tab matching modelValue as selected', () => {
    const wrapper = mountTabs({ modelValue: 'ingot' })

    const selected = tabButtons(wrapper).map(tab => tab.attributes('aria-selected'))

    expect(selected).toEqual(['false', 'true', 'false'])
  })

  it('keeps only the selected tab in the tab order', () => {
    const wrapper = mountTabs({ modelValue: 'cdn' })

    const order = tabButtons(wrapper).map(tab => tab.attributes('tabindex'))

    expect(order).toEqual(['-1', '-1', '0'])
  })

  it('emits the key of a tab that is clicked', () => {
    const wrapper = mountTabs()

    tabButtons(wrapper)[2].trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['cdn']])
  })

  it('moves to the next tab on ArrowRight and wraps around at the end', () => {
    const wrapper = mountTabs({ modelValue: 'cdn' })

    tabButtons(wrapper)[2].trigger('keydown', { key: 'ArrowRight' })

    expect(wrapper.emitted('update:modelValue')).toEqual([['maven']])
  })

  it('moves to the last tab on End', () => {
    const wrapper = mountTabs()

    tabButtons(wrapper)[0].trigger('keydown', { key: 'End' })

    expect(wrapper.emitted('update:modelValue')).toEqual([['cdn']])
  })

  it('renders the label of every tab', () => {
    const wrapper = mountTabs()

    expect(tabButtons(wrapper).map(tab => tab.text())).toEqual(['Maven', 'Ingot', 'CDN'])
  })
})
