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
import NewMachineDialog from './NewMachineDialog.vue'

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

const dialog = () => document.querySelector('[role="dialog"]')

const type = (id, value) => {
  const input = document.getElementById(id)
  input.value = value
  input.dispatchEvent(new Event('input'))
}

const submit = async () => {
  dialog().querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

describe('NewMachineDialog', () => {
  let wrapper
  let api

  beforeEach(async () => {
    api = { create: vi.fn().mockResolvedValue({ data: { name: 'ci-bot' } }) }
    wrapper = mount(NewMachineDialog, { props: { modelValue: true, api }, attachTo: document.body })
    await flushPromises()
  })

  afterEach(() => {
    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('creates a principal of kind MACHINE with the exact body', async () => {
    type('new-machine-name', 'ci-bot')
    type('new-machine-display-name', 'CI bot')
    await submit()

    expect(api.create).toHaveBeenCalledWith({ kind: 'MACHINE', name: 'ci-bot', displayName: 'CI bot' })
  })

  it('creates the account disabled when asked', async () => {
    type('new-machine-name', 'ci-bot')
    document.getElementById('new-machine-disabled').click()
    await submit()

    expect(api.create).toHaveBeenCalledWith({ kind: 'MACHINE', name: 'ci-bot', disabled: true })
  })

  it('sends no disabled flag by default', async () => {
    type('new-machine-name', 'ci-bot')
    await submit()

    expect(api.create.mock.calls[0][0]).not.toHaveProperty('disabled')
  })

  it('sends nothing without a name', async () => {
    await submit()

    expect(api.create).not.toHaveBeenCalled()
    expect(dialog().textContent).toContain('A name is required')
  })

  it('reports the new account and closes', async () => {
    type('new-machine-name', 'ci-bot')
    await submit()

    expect(wrapper.emitted('created')[0]).toEqual(['ci-bot'])
    expect(wrapper.emitted('update:modelValue')[0]).toEqual([false])
  })

  it('shows a name that is taken inside the dialog', async () => {
    api.create.mockRejectedValue(Object.assign(new Error('failed'), {
      response: { status: 409, data: { message: 'The name ci-bot is taken' } }
    }))
    type('new-machine-name', 'ci-bot')
    await submit()

    expect(dialog().querySelector('[role="alert"]').textContent).toContain('The name ci-bot is taken')
    expect(wrapper.emitted('created')).toBeUndefined()
  })
})
