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
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import NewEntryDialog from './NewEntryDialog.vue'

let wrapper: VueWrapper | undefined

const EXISTING = ['releases', 'snapshots']

const openDialog = async () => {
  wrapper = mount(NewEntryDialog, {
    props: { modelValue: false, noun: 'repository', existingIds: EXISTING },
    attachTo: document.body
  })
  await wrapper?.setProps({ modelValue: true })
  await flushPromises()
}

// The dialog is teleported to the body, so its controls are reached through the document.
const idInput = () => document.getElementById('new-entry-id') as HTMLInputElement
const form = () => document.querySelector('#new-entry-form') as HTMLFormElement
// The helper text is the error only while the field is marked invalid; otherwise it is the hint.
const errorText = () => {
  const invalid = idInput().getAttribute('aria-invalid') === 'true'
  return invalid ? document.getElementById('new-entry-id-helper')?.textContent?.trim() ?? '' : ''
}

const typeId = async (value: string) => {
  idInput().value = value
  idInput().dispatchEvent(new Event('input'))
  await flushPromises()
}

const submit = async () => {
  form().dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

const extraElements: Element[] = []

const modalRoot = () => document.querySelector('.vfm')

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  extraElements.splice(0).forEach(element => element.remove())
  document.querySelectorAll('.vfm').forEach(element => element.remove())
})

describe('NewEntryDialog', () => {
  it('asks only for the id, which is the one field every entry needs', async () => {
    await openDialog()

    expect(document.querySelectorAll('#new-entry-form input')).toHaveLength(1)
    expect(document.querySelector('label[for="new-entry-id"]')?.textContent).toContain('Id')
  })

  it('does not complain before the user has typed or tried to submit', async () => {
    await openDialog()

    expect(errorText()).toBe('')
  })

  it('refuses an empty id when the form is submitted', async () => {
    await openDialog()

    await submit()

    expect(errorText()).toBe('Enter an id.')
    expect(wrapper?.emitted('create')).toBeUndefined()
  })

  it('refuses an id that is already used, ignoring case', async () => {
    await openDialog()
    await typeId('Releases')

    await submit()

    expect(errorText()).toBe('A repository with this id already exists.')
    expect(wrapper?.emitted('create')).toBeUndefined()
  })

  it('refuses an id with characters that are not URL-safe', async () => {
    await openDialog()
    await typeId('my repo/1')

    await submit()

    expect(errorText()).toBe('Use only letters, digits, dots, underscores and hyphens.')
    expect(wrapper?.emitted('create')).toBeUndefined()
  })

  it('marks the id field invalid while it shows an error', async () => {
    await openDialog()
    await typeId('releases')
    await submit()

    expect(idInput().getAttribute('aria-invalid')).toBe('true')
  })

  it('creates the entry with the id and closes the dialog', async () => {
    await openDialog()
    await typeId('mirror-2')

    await submit()

    expect(wrapper?.emitted('create')).toEqual([['mirror-2']])
    expect(wrapper?.emitted('update:modelValue')).toEqual([[false]])
  })

  it('starts empty the next time it opens, so an earlier attempt does not leak into it', async () => {
    await openDialog()
    await typeId('releases')
    await submit()
    await wrapper?.setProps({ modelValue: false })
    await flushPromises()

    await wrapper?.setProps({ modelValue: true })
    await flushPromises()

    expect(idInput().value).toBe('')
    expect(errorText()).toBe('')
  })

  it('is announced as a dialog named by its heading', async () => {
    await openDialog()

    const root = modalRoot()
    expect(root?.getAttribute('role'), 'the modal root carries the dialog role').toBe('dialog')
    const name = document.getElementById(root?.getAttribute('aria-labelledby') ?? '')
    expect(name?.textContent?.trim(), 'the accessible name is the heading').toBe('New repository')
  })

  it('opens in the shared dialog frame, without a field colour of its own', async () => {
    await openDialog()

    const frame = document.querySelector('.vfm__content > div')
    expect(frame?.contains(form()), 'the form is inside the frame').toBe(true)
    expect(frame?.classList.contains('max-w-140'), 'the frame caps the width at the 560px of the shared frame').toBe(true)
    expect(form().className, 'the form sets no field container colour').not.toContain('--md-field-container')
  })

  it('draws the scrim through the dialog frame, not as an inline style', async () => {
    await openDialog()

    const overlay = document.querySelector('.vfm__overlay')
    expect(overlay?.getAttribute('style') ?? '', 'the overlay has no inline colour').not.toContain('color-mix')
  })

  it('puts the focus on the id field when it opens', async () => {
    await openDialog()

    expect(document.activeElement, 'the id field has the focus').toBe(idInput())
  })

  it('closes on Escape and returns the focus to the control that opened it', async () => {
    const opener = document.createElement('button')
    document.body.appendChild(opener)
    extraElements.push(opener)
    opener.focus()
    await openDialog()

    modalRoot()?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(wrapper?.emitted('update:modelValue'), 'Escape asks to close').toEqual([[false]])
    await wrapper?.setProps({ modelValue: false })
    await flushPromises()

    expect(document.activeElement, 'the focus is back on the opener').toBe(opener)
  })

  it('confirms with one filled button and cancels with a text button', async () => {
    await openDialog()

    const buttons = [...form().querySelectorAll('button[data-variant]')]
    const filled = buttons.filter(button => button.getAttribute('data-variant') === 'filled')
    expect(filled.map(button => button.textContent?.trim()), 'one filled button, Create').toEqual(['Create'])
    expect(document.getElementById('new-entry-cancel')?.getAttribute('data-variant'), 'Cancel is a text button').toBe('text')
  })
})
