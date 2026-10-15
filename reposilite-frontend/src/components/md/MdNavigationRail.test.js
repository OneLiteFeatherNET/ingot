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
import MdNavigationRail from './MdNavigationRail.vue'
import { RAIL_WIDTH_VARIABLE } from '../../helpers/navigation-rail'

describe('MdNavigationRail width variable', () => {
  it('publishes its width on the document root while it is mounted', () => {
    const wrapper = mount(MdNavigationRail, { props: { items: SECTIONS, modelValue: 'overview' } })

    expect(document.documentElement.style.getPropertyValue(RAIL_WIDTH_VARIABLE), 'the rail is 80px wide').toBe('80px')

    wrapper.unmount()
  })

  it('withdraws the variable once it is unmounted', () => {
    const wrapper = mount(MdNavigationRail, { props: { items: SECTIONS, modelValue: 'overview' } })

    wrapper.unmount()

    expect(document.documentElement.style.getPropertyValue(RAIL_WIDTH_VARIABLE)).toBe('')
  })
})

// The five dashboard sections, as the rail is meant to show them.
const SECTIONS = [
  { key: 'overview', label: 'Overview' },
  { key: 'dashboard', label: 'Dashboard' },
  { key: 'tokens', label: 'Tokens' },
  { key: 'console', label: 'Console' },
  { key: 'settings', label: 'Settings' }
]

describe('MdNavigationRail', () => {
  it('renders one entry per destination', () => {
    const wrapper = mount(MdNavigationRail, { props: { items: SECTIONS, modelValue: 'overview' } })

    expect(wrapper.findAll('li')).toHaveLength(5)
  })

  it('names the navigation landmark after the label it is given', () => {
    const wrapper = mount(MdNavigationRail, {
      props: { items: SECTIONS, modelValue: 'overview', label: 'Primary' }
    })

    expect(wrapper.find('nav').attributes('aria-label')).toBe('Primary')
  })

  it('marks only the active destination with aria-current', () => {
    const wrapper = mount(MdNavigationRail, { props: { items: SECTIONS, modelValue: 'tokens' } })

    const current = wrapper.findAll('[aria-current="page"]')

    expect(current, 'exactly one destination may be the current page').toHaveLength(1)
    expect(current[0].text()).toBe('Tokens')
  })

  it('emits update:modelValue with the key of the clicked destination', async () => {
    const wrapper = mount(MdNavigationRail, { props: { items: SECTIONS, modelValue: 'overview' } })

    await wrapper.findAll('button')[3].trigger('click')

    expect(wrapper.emitted('update:modelValue')[0]).toEqual(['console'])
  })

  it('passes the active flag to the icon slot so the icon can fill', () => {
    const wrapper = mount(MdNavigationRail, {
      props: { items: SECTIONS, modelValue: 'dashboard' },
      slots: {
        icon: `<template #icon="{ item, active }"><i :data-key="item.key" :data-active="String(active)" /></template>`
      }
    })

    expect(wrapper.find('i[data-key="dashboard"]').attributes('data-active')).toBe('true')
    expect(wrapper.find('i[data-key="tokens"]').attributes('data-active')).toBe('false')
  })
})
