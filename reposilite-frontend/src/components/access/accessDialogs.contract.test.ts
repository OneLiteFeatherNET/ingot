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
import { h } from 'vue'
import AddMemberDialog from './AddMemberDialog.vue'
import ConfirmActionDialog from './ConfirmActionDialog.vue'
import NewMachineDialog from './NewMachineDialog.vue'
import NewTeamDialog from './NewTeamDialog.vue'
import NewUserDialog from './NewUserDialog.vue'
import ResetPasswordDialog from './ResetPasswordDialog.vue'
import SsoProviderDialog from './SsoProviderDialog.vue'

const noop = () => Promise.resolve({ data: {} })
const api = { create: noop, setPassword: noop } as never

const dialogs: Array<[string, object, Record<string, unknown>]> = [
  ['AddMemberDialog', AddMemberDialog, { teamName: 'qa' }],
  ['ConfirmActionDialog', ConfirmActionDialog, { title: 'Remove it?', confirmLabel: 'Remove' }],
  ['NewMachineDialog', NewMachineDialog, { api }],
  ['NewTeamDialog', NewTeamDialog, { api }],
  ['NewUserDialog', NewUserDialog, { api }],
  ['ResetPasswordDialog', ResetPasswordDialog, { api, principal: { name: 'alice' } }],
  ['SsoProviderDialog', SsoProviderDialog, { productionUrl: 'https://repo.example.org' }]
]

let wrapper: VueWrapper | null = null
let opener: HTMLButtonElement | null = null

const openDialog = async (component: object, props: Record<string, unknown>) => {
  wrapper = mount(component, { props: { modelValue: false, ...props }, attachTo: document.body })
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

describe('NewMachineDialog heading id', () => {
  it('differs between two mounted instances', async () => {
    wrapper = mount(
      { render: () => h('div', [h(NewMachineDialog, { modelValue: true, api }), h(NewMachineDialog, { modelValue: true, api })]) },
      { attachTo: document.body }
    )
    await flushPromises()

    const ids = [...document.querySelectorAll('[role="dialog"]')].map(element => element.getAttribute('aria-labelledby'))
    expect(new Set(ids).size, 'each instance owns its heading id').toBe(2)
    expect(ids, 'no fixed heading id').not.toContain('new-machine-title')
  })
})
