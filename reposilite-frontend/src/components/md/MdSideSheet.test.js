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

import { describe, it, expect, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h, reactive } from 'vue'
import MdSideSheet from './MdSideSheet.vue'

// The sheet is attached to the document, so that focus can move into it and be read back. Each test unmounts what it
// mounted and removes the opener it made, so no test reads another test's focus.
const mounted = []
const openers = []
afterEach(() => {
  mounted.splice(0).forEach(wrapper => wrapper.unmount())
  openers.splice(0).forEach(element => element.remove())
})

const dialog = () => document.querySelector('[role="dialog"]')
const closeButton = () => dialog().querySelector('[aria-label="Close"]')

// A page that owns the open state, as the Administration frame does. The test reads `state` to close the sheet from the
// page side, as a choice of an entry does.
const mountSheet = async ({ open = true } = {}) => {
  const opener = document.createElement('button')
  opener.textContent = 'Sections'
  document.body.append(opener)
  openers.push(opener)
  opener.focus()

  const state = reactive({ open })
  const page = defineComponent({
    setup() {
      return () => h(MdSideSheet, {
        open: state.open,
        title: 'Administration pages',
        'onUpdate:open': (value) => { state.open = value }
      }, () => [
        h('a', { href: '/access/users' }, 'Users'),
        h('a', { href: '/access/teams' }, 'Teams')
      ])
    }
  })
  const wrapper = mount(page, { attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { state, opener }
}

describe('MdSideSheet', () => {
  it('draws a modal dialog named by its title', async () => {
    await mountSheet()

    expect(dialog(), 'the sheet is a dialog').not.toBeNull()
    expect(dialog().getAttribute('aria-modal'), 'the dialog is modal').toBe('true')
    const name = document.getElementById(dialog().getAttribute('aria-labelledby'))
    expect(name?.textContent.trim(), 'the accessible name is the title').toBe('Administration pages')
  })

  it('carries the md-side-sheet anchor name on the dialog', async () => {
    await mountSheet()

    expect(dialog().getAttribute('data-ingot-component'), 'the anchor name of the component').toBe('md-side-sheet')
  })

  it('draws nothing while it is closed', async () => {
    await mountSheet({ open: false })

    expect(dialog(), 'no dialog in the document').toBeNull()
  })

  it('moves focus into the sheet when it opens, on the first focusable element', async () => {
    await mountSheet()

    expect(dialog().contains(document.activeElement), 'the focus is inside the sheet').toBe(true)
    expect(document.activeElement, 'the first focusable element is the close button').toBe(closeButton())
  })

  it('keeps Tab inside the sheet by moving from the last element to the first', async () => {
    await mountSheet()
    const links = dialog().querySelectorAll('a')
    links[links.length - 1].focus()

    links[links.length - 1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }))

    expect(document.activeElement, 'focus wraps to the first element').toBe(closeButton())
  })

  it('keeps Shift Tab inside the sheet by moving from the first element to the last', async () => {
    await mountSheet()
    const links = dialog().querySelectorAll('a')
    closeButton().focus()

    closeButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true }))

    expect(document.activeElement, 'focus wraps to the last element').toBe(links[links.length - 1])
  })

  it('closes on Escape and returns focus to the element that opened it', async () => {
    const { state, opener } = await mountSheet()

    dialog().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    await flushPromises()

    expect(state.open, 'the sheet asked its page to close').toBe(false)
    expect(dialog(), 'the sheet is closed').toBeNull()
    expect(document.activeElement, 'focus is back on the Sections button').toBe(opener)
  })

  it('closes with the close button and returns focus to the element that opened it', async () => {
    const { state, opener } = await mountSheet()

    closeButton().click()
    await flushPromises()

    expect(state.open, 'the sheet asked its page to close').toBe(false)
    expect(document.activeElement, 'focus is back on the Sections button').toBe(opener)
  })

  it('does not take focus back when the page closes it after a choice', async () => {
    const { state, opener } = await mountSheet()
    const link = dialog().querySelector('a')
    link.focus()

    // The page closes the sheet because an entry was chosen: the page moves the focus to its main content, so the opener
    // must not get it back.
    state.open = false
    await flushPromises()

    expect(document.activeElement, 'the focus is not moved to the opener').not.toBe(opener)
  })

  it('switches off its transition for a visitor who asks for less motion', async () => {
    await mountSheet()

    expect(dialog().classList, 'the transition is off under prefers-reduced-motion').toContain('motion-reduce:transition-none')
  })
})
