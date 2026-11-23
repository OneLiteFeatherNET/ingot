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
import type { Component } from 'vue'
import ConflictDialogSource from './ConflictDialog.vue'
import DeleteEntryDialogSource from './DeleteEntryDialog.vue'

const ConflictDialog = ConflictDialogSource as Component
const DeleteEntryDialog = DeleteEntryDialogSource as Component

const conflict = { kind: 'settings', name: 'maven', title: 'Maven', reviewed: false, server: null }

// Each dialog opens through its own prop and asks to close through its own event.
type Entry = {
  name: string
  component: Component
  closed: Record<string, unknown>
  open: Record<string, unknown>
  event: string
  args: unknown[]
}

const dialogs: Entry[] = [
  { name: 'ConflictDialog', component: ConflictDialog, closed: { conflict: null }, open: { conflict }, event: 'dismiss', args: [] },
  {
    name: 'DeleteEntryDialog',
    component: DeleteEntryDialog,
    closed: { modelValue: false, id: 'releases' },
    open: { modelValue: true },
    event: 'update:modelValue',
    args: [false]
  }
]

let wrapper: VueWrapper | null = null
let opener: HTMLButtonElement | null = null

const openDialog = async (entry: Entry) => {
  wrapper = mount(entry.component, { props: entry.closed, attachTo: document.body })
  await wrapper.setProps(entry.open)
  await flushPromises()
}

const dialog = () => document.querySelector<HTMLElement>('[role="dialog"], [role="alertdialog"]')!

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  opener?.remove()
  opener = null
  document.body.innerHTML = ''
})

describe.each(dialogs)('$name dialog contract', (entry) => {
  it('is named by its heading', async () => {
    await openDialog(entry)

    const name = document.getElementById(dialog().getAttribute('aria-labelledby') ?? '')
    expect(name?.textContent?.trim(), 'the dialog is named by a non-empty heading').toBeTruthy()
  })

  it('moves focus into the dialog when it opens', async () => {
    await openDialog(entry)

    expect(dialog().contains(document.activeElement), 'focus is inside the dialog').toBe(true)
  })

  it('asks to close on Escape', async () => {
    await openDialog(entry)

    ;(document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(wrapper!.emitted(entry.event), 'Escape asks the parent to close').toEqual([entry.args])
  })

  it('has at most one filled button', async () => {
    await openDialog(entry)

    const filled = dialog().querySelectorAll('[data-variant="filled"]')
    expect(filled.length, 'only the confirm action is filled').toBeLessThanOrEqual(1)
  })

  it('returns focus to the opener when it closes', async () => {
    opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    await openDialog(entry)

    await wrapper!.setProps(entry.closed)
    await flushPromises()

    expect(document.activeElement, 'focus returns to the opener').toBe(opener)
  })
})
