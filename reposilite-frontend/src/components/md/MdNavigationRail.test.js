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
import { mount, flushPromises } from '@vue/test-utils'
import MdNavigationRail from './MdNavigationRail.vue'
import { RAIL_WIDTH_VARIABLE } from '../../helpers/navigation-rail'

describe('MdNavigationRail width variable', () => {
  it('publishes its width on the document root while it is mounted', () => {
    const wrapper = mount(MdNavigationRail, { props: { items: SECTIONS, modelValue: 'overview' } })

    expect(document.documentElement.style.getPropertyValue(RAIL_WIDTH_VARIABLE), 'the rail is 96px wide, the Material 3 rail width').toBe('96px')

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

  it('draws the focus ring inside each destination so the window edge cannot clip it', () => {
    const wrapper = mount(MdNavigationRail, { props: { items: SECTIONS, modelValue: 'overview' } })

    for (const entry of wrapper.findAll('a, button')) {
      expect(entry.classes(), `${entry.text()} uses the inset focus ring`).toContain('md-focus-ring-inset')
    }
  })

  it('leaves no focus on a destination that a pointer click selected', () => {
    const wrapper = mount(MdNavigationRail, {
      props: { items: SECTIONS, modelValue: 'overview' },
      attachTo: document.body
    })
    const destination = wrapper.findAll('a, button')[1].element
    destination.focus()

    destination.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, detail: 1 }))

    expect(document.activeElement, 'the clicked destination does not keep the focus ring').not.toBe(destination)

    wrapper.unmount()
  })

  it('keeps the focus on a destination that a keyboard activation selected', () => {
    const wrapper = mount(MdNavigationRail, {
      props: { items: SECTIONS, modelValue: 'overview' },
      attachTo: document.body
    })
    const destination = wrapper.findAll('a, button')[1].element
    destination.focus()

    destination.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, detail: 0 }))

    expect(document.activeElement, 'keyboard focus stays where the keyboard put it').toBe(destination)

    wrapper.unmount()
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

  it('keeps every label on one line, so no word is ever broken', () => {
    const wrapper = mount(MdNavigationRail, {
      props: { items: [{ key: 'settings', label: 'Administration' }], modelValue: 'settings' }
    })

    const label = wrapper.find('a, button').findAll('span').at(-1)
    expect(label.text(), 'the label text').toBe('Administration')
    expect(label.classes(), 'a label is one line and is cut with an ellipsis').toEqual(
      expect.arrayContaining(['whitespace-nowrap', 'truncate'])
    )
    expect(label.classes(), 'a label may not break inside a word').not.toContain('break-words')
  })

  it('gives a label that does not fit a title, so the full name can be read on hover', async () => {
    const widths = stubTextWidths({ Administration: 120 })
    const wrapper = mount(MdNavigationRail, {
      props: { items: [{ key: 'settings', label: 'Administration' }, { key: 'console', label: 'Console' }], modelValue: 'settings' }
    })
    await flushPromises()

    const [long, short] = wrapper.findAll('a, button').map(entry => entry.findAll('span').at(-1).element)
    expect(long.getAttribute('title'), 'the cut label names itself in full').toBe('Administration')
    expect(short.getAttribute('title'), 'a label that fits has no title').toBeNull()
    widths.restore()
  })

  it('draws the state layer on the active indicator, not on the whole entry', () => {
    const wrapper = mount(MdNavigationRail, { props: { items: SECTIONS, modelValue: 'tokens' } })

    for (const entry of wrapper.findAll('a, button')) {
      const indicator = entry.findAll('span')[0]
      expect(indicator.classes(), `${entry.text()} has its hover layer on the indicator pill`).toEqual(
        expect.arrayContaining(['md-state-layer', 'rounded-corner-full'])
      )
    }
  })

  it('draws the top slot above the entries, in the corner the top bar leaves free', () => {
    const wrapper = mount(MdNavigationRail, {
      props: { items: SECTIONS, modelValue: 'overview' },
      slots: { top: '<a data-test="corner" href="/">Home</a>' }
    })

    const corner = wrapper.find('[data-test="corner"]')
    expect(corner.exists(), 'the corner content is drawn').toBe(true)
    expect(corner.element.compareDocumentPosition(wrapper.find('li').element) & Node.DOCUMENT_POSITION_FOLLOWING,
      'the entries follow the corner').toBeTruthy()
  })

  it('keeps the full label as the accessible name of a shortened entry', () => {
    const wrapper = mount(MdNavigationRail, {
      props: { items: [{ key: 'settings', label: 'Administration' }], modelValue: 'settings' }
    })

    expect(wrapper.find('button').text(), 'the entry is named by its full label').toContain('Administration')
  })
})

// jsdom lays nothing out, so the width of each label is stated for the test: text wider than the stated
// width is cut, as the browser would cut it.
function stubTextWidths(widths) {
  const original = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollWidth')
  Object.defineProperty(HTMLElement.prototype, 'scrollWidth', {
    configurable: true,
    get() { return widths[this.textContent] ?? 0 }
  })
  Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
    configurable: true,
    get() { return 88 }
  })
  return {
    restore() {
      if (original) Object.defineProperty(HTMLElement.prototype, 'scrollWidth', original)
      else delete HTMLElement.prototype.scrollWidth
      delete HTMLElement.prototype.clientWidth
    }
  }
}
