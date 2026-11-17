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
import type { VueWrapper } from '@vue/test-utils'
import { defineComponent, h, reactive } from 'vue'
import type { VNode } from 'vue'
import MdDialog from './MdDialog.vue'

// The dialog is teleported to the body and attached to the document, so focus can move into it and be read back. Each
// test unmounts what it mounted and removes the elements it added, so no test reads another test's focus or DOM.
const mounted: VueWrapper[] = []
const extras: HTMLElement[] = []
afterEach(() => {
  mounted.splice(0).forEach(wrapper => wrapper.unmount())
  extras.splice(0).forEach(element => element.remove())
  document.body.querySelectorAll('.vfm').forEach(element => element.remove())
})

const frame = () => document.querySelector<HTMLElement>('.vfm__content > div')!
const dialogRoot = () => document.querySelector<HTMLElement>('.vfm')!
const focusables = () => [...frame().querySelectorAll<HTMLElement>('button')]

interface DialogOptions {
  open?: boolean
  labelledby?: string
  dismissable?: boolean
  size?: 'sm' | 'md' | 'lg'
  role?: 'dialog' | 'alertdialog'
  body?: () => VNode[]
}

// A page that owns the open state, as the identity views do. `state` lets a test close the dialog from the page side.
const mountDialog = async (options: DialogOptions = {}) => {
  const state = reactive({ open: options.open ?? true })
  const events: boolean[] = []
  const page = defineComponent({
    setup() {
      return () => h(MdDialog, {
        modelValue: state.open,
        labelledby: options.labelledby ?? 'dialog-title',
        dismissable: options.dismissable,
        size: options.size,
        role: options.role,
        'onUpdate:modelValue': (value: boolean) => {
          events.push(value)
          state.open = value
        }
      }, {
        header: () => h('h2', { id: options.labelledby ?? 'dialog-title' }, 'Create token'),
        default: options.body ?? (() => [
          h('button', { type: 'button' }, 'Cancel'),
          h('button', { type: 'button' }, 'Save')
        ])
      })
    }
  })
  const wrapper = mount(page, { attachTo: document.body })
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, state, events }
}

describe('MdDialog', () => {
  it('teleports the dialog to the body, outside the wrapper it was mounted in', async () => {
    const { wrapper } = await mountDialog()

    expect(frame(), 'the dialog frame is in the document').not.toBeNull()
    expect(wrapper.find('.vfm').exists(), 'the frame is not inside the mounted wrapper').toBe(false)
  })

  it('carries the dialog-shell anchor on the frame', async () => {
    await mountDialog()

    expect(frame().getAttribute('data-ingot-component'), 'the anchor name is on the frame element').toBe('dialog-shell')
  })

  it('keeps the 24px inset around the frame and the dialog layer on the overlay', async () => {
    await mountDialog()

    expect(frame().closest('.px-6'), 'the frame sits inside the 24px side inset').not.toBeNull()
    expect(frame().closest('.z-md-dialog'), 'the overlay takes the dialog stacking layer').not.toBeNull()
  })

  it('draws the lg size by default, and sm and md when asked, all above the 280px floor', async () => {
    await mountDialog()
    expect(frame().classList.contains('max-w-140'), 'lg is the default and caps the frame at 560px').toBe(true)
    expect(frame().classList.contains('min-w-70'), 'the frame never goes below 280px').toBe(true)
    document.body.querySelectorAll('.vfm').forEach(element => element.remove())

    for (const [size, cap] of [['sm', 'max-w-90'], ['md', 'max-w-120']] as const) {
      await mountDialog({ size })
      expect(frame().classList.contains(cap), `the ${size} size caps the frame with ${cap}`).toBe(true)
      expect(frame().classList.contains('min-w-70'), `the ${size} size keeps the 280px floor`).toBe(true)
      document.body.querySelectorAll('.vfm').forEach(element => element.remove())
    }
  })

  it('is a dialog role by default, and an alertdialog when role is set', async () => {
    await mountDialog()
    expect(frame().getAttribute('role'), 'the default role is dialog').toBe('dialog')
    document.body.querySelectorAll('.vfm').forEach(element => element.remove())

    await mountDialog({ role: 'alertdialog' })
    expect(frame().getAttribute('role'), 'the role prop sets alertdialog').toBe('alertdialog')
  })

  it('names the dialog by the heading of the header slot through the labelledby id', async () => {
    await mountDialog({ labelledby: 'token-title' })

    const name = document.getElementById(dialogRoot().getAttribute('aria-labelledby')!)
    expect(name?.textContent.trim(), 'the accessible name is the header heading').toBe('Create token')
  })

  it('gives two open dialogs different accessible names', async () => {
    await mountDialog({ labelledby: 'first-title' })
    await mountDialog({ labelledby: 'second-title' })

    const names = [...document.querySelectorAll<HTMLElement>('.vfm')]
      .map(element => element.getAttribute('aria-labelledby'))
    expect(names, 'each dialog points at its own heading').toEqual(['first-title', 'second-title'])
  })

  it('moves focus to the element with data-autofocus when it opens', async () => {
    await mountDialog({
      body: () => [
        h('button', { type: 'button' }, 'Cancel'),
        h('button', { type: 'button', 'data-autofocus': '' }, 'Save')
      ]
    })

    expect(document.activeElement?.textContent, 'the data-autofocus button has focus').toBe('Save')
  })

  it('moves focus to the first focusable element when nothing asks for focus', async () => {
    await mountDialog()

    expect(document.activeElement?.textContent, 'the first button has focus').toBe('Cancel')
  })

  it('moves Tab from the last focusable element back to the first', async () => {
    await mountDialog()
    const first = focusables()[0]
    const last = focusables().at(-1)!
    last.focus()

    frame().dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))

    expect(document.activeElement, 'Tab stays inside the dialog and wraps to the first element').toBe(first)
  })

  it('moves Shift+Tab from the first focusable element to the last', async () => {
    await mountDialog()
    const first = focusables()[0]
    const last = focusables().at(-1)!
    first.focus()

    frame().dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true }))

    expect(document.activeElement, 'Shift+Tab stays inside the dialog and wraps to the last element').toBe(last)
  })

  it('returns focus to the opener when the dialog closes', async () => {
    const opener = document.createElement('button')
    opener.textContent = 'New token'
    document.body.append(opener)
    extras.push(opener)
    opener.focus()

    const { state } = await mountDialog()
    state.open = false
    await flushPromises()

    expect(document.activeElement, 'focus is back on the button that opened the dialog').toBe(opener)
  })

  it('returns focus to the nearest ancestor when the opener was removed while the dialog was open', async () => {
    const container = document.createElement('section')
    container.tabIndex = -1
    const opener = document.createElement('button')
    container.append(opener)
    document.body.append(container)
    extras.push(container)
    opener.focus()

    const { state } = await mountDialog()
    opener.remove()
    state.open = false
    await flushPromises()

    expect(document.activeElement, 'focus falls back to the nearest ancestor that is still in the page').toBe(container)
  })

  it('closes on Escape when dismissable', async () => {
    const { events } = await mountDialog({ dismissable: true })

    frame().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(events, 'Escape asks the page to close the dialog').toEqual([false])
  })

  it('stays open on Escape when not dismissable', async () => {
    const { events, state } = await mountDialog({ dismissable: false })

    frame().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(events, 'a dialog that must be answered does not close on Escape').toEqual([])
    expect(state.open, 'the dialog is still open').toBe(true)
  })

  it('paints the overlay with the scrim utility and no inline style', async () => {
    await mountDialog()

    const overlay = document.querySelector<HTMLElement>('.vfm__overlay')
    expect(overlay?.classList.contains('bg-scrim/md-scrim!'), 'the overlay carries the important bg-scrim/md-scrim utility').toBe(true)
    expect(overlay!.getAttribute('style') ?? '', 'the overlay has no color-mix inline style').not.toContain('color-mix')
  })
})
