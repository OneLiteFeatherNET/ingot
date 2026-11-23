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
import DeletePolicyDialog from './DeletePolicyDialog.vue'
import RunNowDialog from './RunNowDialog.vue'

const dialogs: Array<[string, object, Record<string, unknown>]> = [
  ['DeletePolicyDialog', DeletePolicyDialog, { policyId: 'snapshots-weekly' }],
  ['RunNowDialog', RunNowDialog, { policyId: 'snapshots-weekly', running: false, result: null, error: null }]
]

let wrapper: VueWrapper | null = null
let opener: HTMLButtonElement | null = null

const openDialog = async (component: object, props: Record<string, unknown>) => {
  wrapper = mount(component, { props: { modelValue: false, ...props }, attachTo: document.body }) as VueWrapper
  await wrapper.setProps({ modelValue: true })
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

describe.each(dialogs)('%s dialog contract', (_name, component, props) => {
  it('is named by its heading', async () => {
    await openDialog(component, props)

    const name = document.getElementById(dialog().getAttribute('aria-labelledby') ?? '')
    expect(name?.textContent?.trim(), 'the dialog is named by a non-empty heading').toBeTruthy()
  })

  it('moves focus into the dialog when it opens', async () => {
    await openDialog(component, props)

    expect(dialog().contains(document.activeElement), 'focus is inside the dialog').toBe(true)
  })

  it('returns focus to the opener when it closes', async () => {
    opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    await openDialog(component, props)

    await wrapper!.setProps({ modelValue: false })
    await flushPromises()

    expect(document.activeElement, 'focus returns to the opener').toBe(opener)
  })

  it('asks to close on Escape', async () => {
    await openDialog(component, props)

    ;(document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(wrapper!.emitted('update:modelValue'), 'Escape asks the parent to close').toEqual([[false]])
  })

  it('has at most one filled button', async () => {
    await openDialog(component, props)

    const filled = dialog().querySelectorAll('[data-variant="filled"]')
    expect(filled.length, 'only the confirm action is filled').toBeLessThanOrEqual(1)
  })
})

describe('DeletePolicyDialog destructive contract', () => {
  it('is an alertdialog', async () => {
    await openDialog(DeletePolicyDialog, { policyId: 'snapshots-weekly' })

    expect(dialog().getAttribute('role'), 'a destructive confirmation is announced as an alertdialog').toBe('alertdialog')
  })

  it('focuses Cancel first', async () => {
    await openDialog(DeletePolicyDialog, { policyId: 'snapshots-weekly' })

    expect(document.activeElement?.id, 'the safe action holds the initial focus').toBe('delete-policy-cancel')
  })

  it('names the filled error confirm Delete policy', async () => {
    await openDialog(DeletePolicyDialog, { policyId: 'snapshots-weekly' })

    const confirm = dialog().querySelector<HTMLElement>('[data-variant="filled"]')
    expect(confirm?.textContent?.trim(), 'the only filled button is the confirm').toBe('Delete policy')
  })
})
