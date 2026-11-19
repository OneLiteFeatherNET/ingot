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
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import StepUpDialog from './StepUpDialog.vue'

// The dialog is a view of the session's step-up state. The session is replaced by a double whose
// refs the test sets, so the dialog is tested without a server.
const session = await vi.hoisted(async () => {
  const { ref } = await import('vue')
  const { vi: mock } = await import('vitest')
  return {
    stepUp: ref<undefined | { methods: string[]; message: string; busy: boolean }>(undefined),
    confirmStepUp: mock.fn<(code: string) => Promise<void>>(() => Promise.resolve()),
    cancelStepUp: mock.fn()
  }
})

vi.mock('../../store/session', () => ({
  useSession: () => ({
    stepUp: session.stepUp,
    confirmStepUp: session.confirmStepUp,
    cancelStepUp: session.cancelStepUp
  })
}))

const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]')
const confirmButton = () =>
  [...(dialog()?.querySelectorAll('button') ?? [])].find(node => node.textContent?.trim() === 'Confirm') as HTMLButtonElement
const codeInput = () => dialog()?.querySelector('input') as HTMLInputElement

const type = (value: string) => {
  const input = codeInput()
  input.value = value
  input.dispatchEvent(new Event('input'))
  return flushPromises()
}

describe('StepUpDialog', () => {
  let wrapper: VueWrapper

  const mountDialog = async () => {
    wrapper = mount(StepUpDialog, { attachTo: document.body }) as VueWrapper
    await flushPromises()
  }

  beforeEach(() => {
    session.stepUp.value = undefined
    session.confirmStepUp.mockClear()
    session.cancelStepUp.mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
    document.body.innerHTML = ''
  })

  it('stays closed while no sensitive action waits for a second factor', async () => {
    await mountDialog()

    expect(dialog(), 'no step-up is pending, so no dialog is shown').toBeNull()
  })

  it('opens when a step-up is pending and names the recovery code as a way to confirm', async () => {
    session.stepUp.value = { methods: ['AUTHENTICATOR', 'RECOVERY_CODE'], message: '', busy: false }
    await mountDialog()

    expect(dialog(), 'the dialog opens on the pending step-up').not.toBeNull()
    expect(dialog()?.textContent, 'the field accepts a recovery code too').toContain('recovery code')
  })

  it('keeps the confirm button disabled until a code is typed', async () => {
    session.stepUp.value = { methods: ['AUTHENTICATOR'], message: '', busy: false }
    await mountDialog()

    expect(confirmButton().disabled, 'an empty code cannot be sent').toBe(true)
  })

  it('shows the server message in an alert when the code is refused', async () => {
    session.stepUp.value = { methods: ['AUTHENTICATOR'], message: 'Invalid code', busy: false }
    await mountDialog()

    expect(dialog()?.querySelector('[role="alert"]')?.textContent, 'the reason of the refusal is shown').toContain('Invalid code')
  })

  it('sends the typed code to the session when confirmed', async () => {
    session.stepUp.value = { methods: ['AUTHENTICATOR'], message: '', busy: false }
    await mountDialog()

    await type('123456')
    confirmButton().click()
    await flushPromises()

    expect(session.confirmStepUp, 'the code goes to the step-up of the session').toHaveBeenCalledWith('123456')
  })

  it('cancels the step-up when the dialog is dismissed', async () => {
    session.stepUp.value = { methods: ['AUTHENTICATOR'], message: '', busy: false }
    await mountDialog()

    const cancel = [...(dialog()?.querySelectorAll('button') ?? [])].find(node => node.textContent?.trim() === 'Cancel') as HTMLButtonElement
    cancel.click()
    await flushPromises()

    expect(session.cancelStepUp, 'cancelling tells the session that the action is refused').toHaveBeenCalledTimes(1)
  })
})
