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
import DeleteEntryDialog from './DeleteEntryDialog.vue'

let wrapper: VueWrapper | undefined
const extraElements: Element[] = []

const openDialog = async (id = 'releases') => {
  wrapper = mount(DeleteEntryDialog, {
    props: { modelValue: false, id },
    attachTo: document.body
  })
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
}

const requireElement = <T extends HTMLElement>(id: string): T => {
  const element = document.getElementById(id)
  if (!element) throw new Error(`no element with the id ${id}`)
  return element as T
}
const confirmButton = () => requireElement('delete-entry-confirm')
// The heading is found through the alertdialog that names it, because the heading id is generated per instance.
const dialogHeading = () => {
  const root = document.querySelector('[role="alertdialog"]')
  return document.getElementById(root?.getAttribute('aria-labelledby') ?? '')
}
const typedInput = () => requireElement<HTMLInputElement>('delete-entry-confirm-input')
const form = (): HTMLFormElement => requireElement<HTMLFormElement>('delete-entry-form')

const typeText = async (value: string) => {
  typedInput().value = value
  typedInput().dispatchEvent(new Event('input'))
  await flushPromises()
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  extraElements.splice(0).forEach(element => element.remove())
  document.querySelectorAll('.vfm').forEach(element => element.remove())
})

describe('DeleteEntryDialog', () => {
  it('names the entry it is about to delete', async () => {
    await openDialog('releases')

    expect(dialogHeading()?.textContent).toContain('Delete releases?')
  })

  it('keeps the delete action disabled while nothing is typed', async () => {
    await openDialog()

    expect(confirmButton().hasAttribute('disabled')).toBe(true)
  })

  it('keeps the delete action disabled while the typed id is only a part of the id', async () => {
    await openDialog()

    await typeText('releas')

    expect(confirmButton().hasAttribute('disabled')).toBe(true)
  })

  it('keeps the delete action disabled when the typed id has a stray space', async () => {
    await openDialog()

    await typeText('releases ')

    expect(confirmButton().hasAttribute('disabled')).toBe(true)
  })

  it('keeps the delete action disabled when the case differs', async () => {
    await openDialog()

    await typeText('Releases')

    expect(confirmButton().hasAttribute('disabled')).toBe(true)
  })

  it('enables the delete action once the id is typed exactly', async () => {
    await openDialog()

    await typeText('releases')

    expect(confirmButton().hasAttribute('disabled')).toBe(false)
  })

  it('emits confirm when the exact id is typed and the action is pressed', async () => {
    await openDialog()
    await typeText('releases')

    confirmButton().click()
    await flushPromises()

    expect(wrapper!.emitted('confirm')).toEqual([[]])
  })

  it('does not confirm when the form is submitted with a wrong id', async () => {
    await openDialog()
    await typeText('releas')

    form().dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(wrapper!.emitted('confirm')).toBeUndefined()
  })

  it('closes without deleting when the dialog is cancelled', async () => {
    await openDialog()

    requireElement('delete-entry-cancel').click()
    await flushPromises()

    expect(wrapper!.emitted('update:modelValue')).toEqual([[false]])
    expect(wrapper!.emitted('confirm')).toBeUndefined()
  })

  it('is announced as an alertdialog on the modal root', async () => {
    await openDialog()

    expect(document.querySelector('.vfm')?.getAttribute('role')).toBe('alertdialog')
  })

  it('uses no fixed id for its heading', async () => {
    await openDialog()

    expect(document.getElementById('delete-entry-title'), 'the heading id is generated per instance').toBeNull()
  })

  it('draws the scrim through the dialog frame, not as an inline style', async () => {
    await openDialog()

    const overlay = document.querySelector('.vfm__overlay')
    expect(overlay?.getAttribute('style') ?? '', 'the overlay has no inline colour').not.toContain('color-mix')
  })

  it('puts the focus on Cancel when it opens, so that Enter does not delete', async () => {
    await openDialog()

    expect(document.activeElement?.id).toBe('delete-entry-cancel')
  })

  it('returns the focus to the control that opened it after the deletion is confirmed', async () => {
    const opener = document.createElement('button')
    opener.textContent = 'Delete releases'
    document.body.appendChild(opener)
    extraElements.push(opener)
    opener.focus()
    await openDialog()
    await typeText('releases')

    confirmButton().click()
    await flushPromises()
    await wrapper!.setProps({ modelValue: false })
    await flushPromises()

    expect(document.activeElement, 'focus is not lost to the page body').toBe(opener)
  })
})
