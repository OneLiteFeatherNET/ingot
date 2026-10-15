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
import NewEntryDialog from './NewEntryDialog.vue'

let wrapper

const EXISTING = ['releases', 'snapshots']

const openDialog = async () => {
  wrapper = mount(NewEntryDialog, {
    props: { modelValue: false, noun: 'repository', existingIds: EXISTING },
    attachTo: document.body
  })
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
}

// The dialog is teleported to the body, so its controls are reached through the document.
const idInput = () => document.getElementById('new-entry-id')
const form = () => document.querySelector('#new-entry-form')
// The helper text is the error only while the field is marked invalid; otherwise it is the hint.
const errorText = () => {
  const invalid = idInput().getAttribute('aria-invalid') === 'true'
  return invalid ? document.getElementById('new-entry-id-helper')?.textContent?.trim() ?? '' : ''
}

const typeId = async (value) => {
  idInput().value = value
  idInput().dispatchEvent(new Event('input'))
  await flushPromises()
}

const submit = async () => {
  form().dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
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
    expect(wrapper.emitted('create')).toBeUndefined()
  })

  it('refuses an id that is already used, ignoring case', async () => {
    await openDialog()
    await typeId('Releases')

    await submit()

    expect(errorText()).toBe('A repository with this id already exists.')
    expect(wrapper.emitted('create')).toBeUndefined()
  })

  it('refuses an id with characters that are not URL-safe', async () => {
    await openDialog()
    await typeId('my repo/1')

    await submit()

    expect(errorText()).toBe('Use only letters, digits, dots, underscores and hyphens.')
    expect(wrapper.emitted('create')).toBeUndefined()
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

    expect(wrapper.emitted('create')).toEqual([['mirror-2']])
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })

  it('starts empty the next time it opens, so an earlier attempt does not leak into it', async () => {
    await openDialog()
    await typeId('releases')
    await submit()
    await wrapper.setProps({ modelValue: false })
    await flushPromises()

    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    expect(idInput().value).toBe('')
    expect(errorText()).toBe('')
  })
})
