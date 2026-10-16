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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import DeleteEntryModal from './DeleteEntryModal.vue'

// Records every delete request the modal sends, so each test can inspect its own.
const { deleteRequests } = vi.hoisted(() => ({ deleteRequests: [] }))

vi.mock('../../store/session', async () => {
  const { ref } = await import('vue')
  const client = ref({
    maven: {
      delete: (gav) => {
        deleteRequests.push(gav)
        return Promise.resolve({})
      }
    }
  })
  return {
    useSession: () => ({ client })
  }
})

vi.mock('../../store/qualifier', () => ({
  default: () => ({ refreshQualifier: () => {} })
}))

const file = { path: 'releases/net/example', file: 'example-1.0.jar', directory: false }
const folder = { path: 'releases/net', file: 'example', directory: true }

let wrapper
const close = vi.fn()

// The dialog is teleported to the body, so the tests look for it there.
const mountDialog = (value) => {
  wrapper = mount(DeleteEntryModal, {
    props: { value, close },
    attachTo: document.body
  })
}

const dialog = () => document.querySelector('[role="alertdialog"]')
const dialogText = () => dialog()?.textContent.replace(/\s+/g, ' ').trim() ?? ''
const buttonInDialog = (text) =>
  [...(dialog()?.querySelectorAll('button') ?? [])].find(button => button.textContent.trim() === text)

describe('DeleteEntryModal', () => {
  beforeEach(() => {
    deleteRequests.length = 0
    close.mockReset()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('shows the full path of the file it is about to delete', async () => {
    mountDialog(file)
    await flushPromises()

    expect(dialogText(), 'the path names the entry').toContain('/releases/net/example/example-1.0.jar')
  })

  it('dims the page behind the dialog with the 32% scrim', async () => {
    mountDialog(file)
    await flushPromises()
    const overlay = document.querySelector('.vfm__overlay')

    expect(overlay, 'the modal paints an overlay').not.toBeNull()
    expect(overlay.classList.contains('bg-scrim/32!'), 'the overlay uses the md3 scrim at 32%').toBe(true)
    expect(overlay.classList.contains('bg-scrim/40!'), 'the old 40% scrim is gone').toBe(false)
  })

  it('states that deleting a file cannot be undone', async () => {
    mountDialog(file)
    await flushPromises()

    expect(dialogText(), 'the permanence is stated').toContain('cannot be undone')
  })

  it('states that deleting a folder removes every file inside it', async () => {
    mountDialog(folder)
    await flushPromises()

    expect(dialogText(), 'the scope of a folder delete is stated').toContain('every file inside it')
  })

  it('labels the confirming action Delete', async () => {
    mountDialog(file)
    await flushPromises()

    expect(buttonInDialog('Delete'), 'the destructive action names what it does').toBeDefined()
  })

  it('deletes the entry when Delete is pressed', async () => {
    mountDialog(file)
    await flushPromises()

    buttonInDialog('Delete').click()
    await flushPromises()

    expect(deleteRequests, 'the delete request names the file').toEqual(['releases/net/example/example-1.0.jar'])
    expect(close, 'the dialog closes once the delete is requested').toHaveBeenCalled()
  })

  it('leaves the entry alone when Cancel is pressed', async () => {
    mountDialog(file)
    await flushPromises()

    buttonInDialog('Cancel').click()
    await flushPromises()

    expect(deleteRequests, 'cancelling deletes nothing').toEqual([])
    expect(close, 'cancelling closes the dialog').toHaveBeenCalled()
  })
})
