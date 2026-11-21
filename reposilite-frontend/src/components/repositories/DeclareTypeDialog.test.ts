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

import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import DeclareTypeDialog from './DeclareTypeDialog.vue'

let wrapper: VueWrapper | undefined
let opener: HTMLButtonElement | undefined

const openDialog = async () => {
  opener = document.createElement('button')
  opener.textContent = 'Declare type'
  document.body.append(opener)
  opener.focus()
  wrapper = mount(DeclareTypeDialog, {
    props: {
      modelValue: false,
      typeLabel: 'Proxy',
      notice: 'Two empty entries are removed.',
      'onUpdate:modelValue': (value: boolean) => wrapper?.setProps({ modelValue: value })
    },
    attachTo: document.body
  }) as VueWrapper
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
}

const modalRoot = () => document.querySelector('.vfm')
const dialogButton = (label: string) =>
  [...(modalRoot()?.querySelectorAll('button') ?? [])].find(button => button.textContent?.trim() === label)

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  opener?.remove()
  opener = undefined
  document.querySelectorAll('.vfm').forEach(element => element.remove())
})

describe('DeclareTypeDialog', () => {
  it('announces the open dialog as an alert dialog named by its heading', async () => {
    await openDialog()

    const root = modalRoot()
    expect(root?.getAttribute('role'), 'the modal root carries the alertdialog role').toBe('alertdialog')
    const name = document.getElementById(root?.getAttribute('aria-labelledby') ?? '')
    expect(name?.textContent?.trim(), 'the accessible name is the heading').toBe('Declare Proxy?')
    const description = document.getElementById(root?.getAttribute('aria-describedby') ?? '')
    expect(description?.textContent, 'the description carries the notice').toContain('Two empty entries are removed.')
  })

  it('opens in the shared dialog frame', async () => {
    await openDialog()

    const frame = document.querySelector('.vfm__content > div')
    expect(frame?.contains(document.getElementById('declare-type-title')), 'the heading is inside the frame').toBe(true)
    expect(frame?.querySelector('[data-test="declare-type-dialog"]'), 'the form of the dialog sits in the frame').not.toBeNull()
    expect(frame?.classList.contains('max-w-120'), 'the frame takes the medium width of the shared frame').toBe(true)
  })

  it('draws the scrim through the dialog frame, not as an inline style', async () => {
    await openDialog()

    const overlay = document.querySelector('.vfm__overlay')
    expect(overlay, 'the modal overlay is in the document').not.toBeNull()
    expect(overlay?.getAttribute('style') ?? '', 'the overlay has no inline colour').not.toContain('color-mix')
  })

  it('puts the focus on Cancel when it opens, so that Enter does not declare', async () => {
    await openDialog()

    expect(document.activeElement, 'Cancel has the focus').toBe(dialogButton('Cancel'))
  })

  it('closes on Escape and returns the focus to the button that opened it', async () => {
    await openDialog()

    modalRoot()?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(document.getElementById('declare-type-title'), 'Escape closes the dialog').toBeNull()
    expect(document.activeElement, 'the focus is back on the opener').toBe(opener)
  })

  it('confirms with one filled button and cancels with a text button', async () => {
    await openDialog()

    const buttons = [...(modalRoot()?.querySelectorAll('button[data-variant]') ?? [])]
    const filled = buttons.filter(button => button.getAttribute('data-variant') === 'filled')
    expect(filled.map(button => button.textContent?.trim()), 'one filled button, the declaration').toEqual(['Declare Proxy'])
    expect(dialogButton('Cancel')?.getAttribute('data-variant'), 'Cancel is a text button').toBe('text')
  })

  it('emits confirm and closes when the declaration is confirmed', async () => {
    await openDialog()

    dialogButton('Declare Proxy')?.click()
    await flushPromises()

    expect(wrapper?.emitted('confirm'), 'the declaration is reported once').toHaveLength(1)
    expect(document.getElementById('declare-type-title'), 'the dialog closes').toBeNull()
  })
})
