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

describe('MdNavigationBar links', () => {
  const LINKS = [
    { key: 'overview', label: 'Overview', href: '#/org/repo' },
    { key: 'tokens', label: 'Tokens', href: '#/org/repo?tab=tokens' }
  ]

  const clickWith = (element, init) => {
    const event = new MouseEvent('click', { bubbles: true, cancelable: true, ...init })
    element.dispatchEvent(event)
    return event
  }

  it('keeps the page in place for a plain click, so the section can switch without a reload', () => {
    const wrapper = mount(MdNavigationBar, { props: { items: LINKS, modelValue: 'overview' } })

    const event = clickWith(wrapper.findAll('a')[1].element, { button: 0 })

    expect(event.defaultPrevented).toBe(true)
    expect(wrapper.emitted('update:modelValue')[0]).toEqual(['tokens'])
  })

  it('leaves a modified click to the browser, so the link can open in a new tab', () => {
    const wrapper = mount(MdNavigationBar, { props: { items: LINKS, modelValue: 'overview' } })

    const event = clickWith(wrapper.findAll('a')[1].element, { button: 0, ctrlKey: true })

    expect(event.defaultPrevented).toBe(false)
    expect(wrapper.emitted('update:modelValue'), 'a modified click must not also switch this page').toBeUndefined()
  })

  it('shows the label of every link as its visible text', () => {
    const wrapper = mount(MdNavigationBar, { props: { items: LINKS, modelValue: 'overview' } })

    expect(wrapper.findAll('a').map(link => link.text())).toEqual(['Overview', 'Tokens'])
  })

  it('truncates a label wider than its item, so the label never runs past the edge of a 320 px window', () => {
    const wrapper = mount(MdNavigationBar, { props: { items: TABS, modelValue: 'Overview' } })

    const label = wrapper.find('li span.md-label-medium')
    expect(label.classes(), 'the label is cut with an ellipsis inside its item').toEqual(expect.arrayContaining(['block', 'max-w-full', 'truncate']))
    expect(label.classes(), 'the label does not wrap onto a second line').toContain('whitespace-nowrap')
  })
})

describe('MdNavigationBar widths and short labels', () => {
  it('gives every destination an equal share of the bar, whatever its label is long', () => {
    const items = [
      { key: 'browse', label: 'Browse' },
      { key: 'settings', label: 'Administration' },
    ]
    const wrapper = mount(MdNavigationBar, { props: { items, modelValue: 'browse' } })

    for (const item of wrapper.findAll('li')) {
      expect(item.classes(), 'an item takes an equal share of the width, not the width of its label').toEqual(expect.arrayContaining(['flex-1', 'basis-0']))
      expect(item.classes(), 'an item is not sized by its content').not.toContain('flex-auto')
    }
  })

  it('shows the short label of a destination in the bar in place of its label', () => {
    const items = [{ key: 'settings', label: 'Administration', shortLabel: 'Admin' }]
    const wrapper = mount(MdNavigationBar, { props: { items, modelValue: 'settings' } })

    expect(wrapper.find('li span.md-label-medium').text()).toBe('Admin')
  })

  it('keeps the short label as the accessible name and the full label as its description', () => {
    const items = [{ key: 'settings', label: 'Administration', shortLabel: 'Admin' }]
    const wrapper = mount(MdNavigationBar, { props: { items, modelValue: 'settings' } })

    expect(wrapper.find('button').attributes('aria-label'), 'the name is the visible text, so axe label-content-name-mismatch passes').toBeUndefined()
    expect(wrapper.find('button').attributes('title'), 'the full label is the description').toBe('Administration')
  })

  it('names no accessible label of its own for a destination without a short label', () => {
    const wrapper = mount(MdNavigationBar, { props: { items: TABS, modelValue: 'Overview' } })

    expect(wrapper.find('li span.md-label-medium').text()).toBe('Overview')
    expect(wrapper.find('button').attributes('aria-label')).toBeUndefined()
  })
})
