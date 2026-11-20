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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { h } from 'vue'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import FactoryResetModal from './FactoryResetModal.vue'

let wrapper: VueWrapper | undefined

const openDialog = async (callback = vi.fn()) => {
  wrapper = mount(FactoryResetModal, {
    props: { callback },
    slots: { button: () => h('button', { type: 'button' }, 'Reset') },
    attachTo: document.body
  })
  const opener = wrapper.find('button')
  ;(opener.element as HTMLElement).focus()
  await opener.trigger('click')
  await flushPromises()
  return opener.element as HTMLElement
}

const modalRoot = () => document.querySelector('.vfm')
const dialogButton = (label: string) =>
  [...(modalRoot()?.querySelectorAll('button') ?? [])].find(button => button.textContent?.trim() === label)

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.querySelectorAll('.vfm').forEach(element => element.remove())
})

describe('FactoryResetModal', () => {
  it('renders the open dialog in the body, not inside the page component', async () => {
    await openDialog()

    const title = document.getElementById('factory-reset-title')
    expect(title, 'the dialog title is in the document').not.toBeNull()
    expect((wrapper?.element as HTMLElement | undefined)?.contains(title), 'the dialog is teleported out of the page').toBe(false)
  })

  it('announces the open dialog as an alert dialog named by its heading', async () => {
    await openDialog()

    const root = modalRoot()
    expect(root?.getAttribute('role'), 'the modal root carries the alertdialog role').toBe('alertdialog')
    const name = document.getElementById(root?.getAttribute('aria-labelledby') ?? '')
    expect(name?.textContent?.trim(), 'the accessible name is the heading').toBe('Reset all to defaults?')
  })

  it('opens in the shared dialog frame', async () => {
    await openDialog()

    const frame = document.querySelector('.vfm__content > div')
    expect(frame?.contains(document.getElementById('factory-reset-title')), 'the heading is inside the frame').toBe(true)
    expect(frame?.classList.contains('max-w-140'), 'the frame caps the width at the 560px of the shared frame').toBe(true)
  })

  it('draws the scrim through the dialog frame, not as an inline style', async () => {
    await openDialog()

    const overlay = document.querySelector('.vfm__overlay')
    expect(overlay, 'the modal overlay is in the document').not.toBeNull()
    expect(overlay?.getAttribute('style') ?? '', 'the overlay has no inline colour').not.toContain('color-mix')
  })

  it('puts the focus on Cancel when it opens, so that Enter does not reset', async () => {
    await openDialog()

    expect(document.activeElement, 'Cancel has the focus').toBe(dialogButton('Cancel'))
  })

  it('closes on Escape and returns the focus to the button that opened it', async () => {
    const opener = await openDialog()

    modalRoot()?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(document.getElementById('factory-reset-title'), 'Escape closes the dialog').toBeNull()
    expect(document.activeElement, 'the focus is back on the opener').toBe(opener)
  })

  it('confirms with one filled error button and cancels with a text button', async () => {
    await openDialog()

    const buttons = [...(modalRoot()?.querySelectorAll('button[data-variant]') ?? [])]
    const filled = buttons.filter(button => button.getAttribute('data-variant') === 'filled')
    expect(filled.map(button => button.textContent?.trim()), 'one filled button, the reset').toEqual(['Reset all to defaults'])
    expect(filled[0]?.className, 'the reset carries the error role').toContain('bg-error')
    expect(dialogButton('Cancel')?.getAttribute('data-variant'), 'Cancel is a text button').toBe('text')
  })

  it('runs the reset and closes when the reset is confirmed', async () => {
    const callback = vi.fn()
    await openDialog(callback)

    dialogButton('Reset all to defaults')?.click()
    await flushPromises()

    expect(callback, 'the reset runs once').toHaveBeenCalledTimes(1)
    expect(document.getElementById('factory-reset-title'), 'the dialog closes').toBeNull()
  })
})
