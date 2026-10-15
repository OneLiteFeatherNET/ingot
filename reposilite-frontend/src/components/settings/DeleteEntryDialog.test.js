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
import DeleteEntryDialog from './DeleteEntryDialog.vue'

let wrapper

const openDialog = async (id = 'releases') => {
  wrapper = mount(DeleteEntryDialog, {
    props: { modelValue: false, id },
    attachTo: document.body
  })
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
}

const confirmButton = () => document.getElementById('delete-entry-confirm')
const typedInput = () => document.getElementById('delete-entry-confirm-input')
const form = () => document.querySelector('#delete-entry-form')

const typeText = async (value) => {
  typedInput().value = value
  typedInput().dispatchEvent(new Event('input'))
  await flushPromises()
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('DeleteEntryDialog', () => {
  it('names the entry it is about to delete', async () => {
    await openDialog('releases')

    expect(document.getElementById('delete-entry-title')?.textContent).toContain('Delete releases?')
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

    expect(wrapper.emitted('confirm')).toEqual([[]])
  })

  it('does not confirm when the form is submitted with a wrong id', async () => {
    await openDialog()
    await typeText('releas')

    form().dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('closes without deleting when the dialog is cancelled', async () => {
    await openDialog()

    document.getElementById('delete-entry-cancel').click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })
})
