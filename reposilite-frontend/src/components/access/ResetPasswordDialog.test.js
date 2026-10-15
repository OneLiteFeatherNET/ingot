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
import ResetPasswordDialog from './ResetPasswordDialog.vue'

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

const dialog = () => document.querySelector('[role="dialog"]')
const field = (id) => document.getElementById(id)

const type = (id, value) => {
  const input = field(id)
  input.value = value
  input.dispatchEvent(new Event('input'))
}

const submit = async () => {
  dialog().querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

describe('ResetPasswordDialog', () => {
  let wrapper
  let api

  beforeEach(async () => {
    api = { setPassword: vi.fn().mockResolvedValue({ status: 204 }) }
    wrapper = mount(ResetPasswordDialog, {
      props: { modelValue: true, principal: { name: 'alice' }, api },
      attachTo: document.body
    })
    await flushPromises()
  })

  afterEach(() => {
    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('tells the admin that the user must change the password at the next sign-in', () => {
    expect(dialog().textContent).toContain('alice')
    expect(dialog().textContent).toContain('next time they sign in')
  })

  it('does not send a password under 12 characters', async () => {
    type('reset-password-new', 'short-11-ch')
    type('reset-password-confirm', 'short-11-ch')
    await submit()

    expect(api.setPassword).not.toHaveBeenCalled()
    expect(dialog().textContent).toContain('Use at least 12 characters')
  })

  it('does not send a confirmation that differs', async () => {
    type('reset-password-new', 'a-long-password')
    type('reset-password-confirm', 'another-password')
    await submit()

    expect(api.setPassword).not.toHaveBeenCalled()
    expect(dialog().textContent).toContain('Passwords do not match')
  })

  it('sends the user name and the exact password', async () => {
    type('reset-password-new', 'a-long-password')
    type('reset-password-confirm', 'a-long-password')
    await submit()

    expect(api.setPassword).toHaveBeenCalledTimes(1)
    expect(api.setPassword).toHaveBeenCalledWith('alice', 'a-long-password')
  })

  it('closes and reports done after the server accepts it', async () => {
    type('reset-password-new', 'a-long-password')
    type('reset-password-confirm', 'a-long-password')
    await submit()

    expect(wrapper.emitted('done')[0]).toEqual([{ name: 'alice' }])
    expect(wrapper.emitted('update:modelValue')[0]).toEqual([false])
  })

  it('shows a failed request inside the dialog and stays open', async () => {
    api.setPassword.mockRejectedValue(Object.assign(new Error('failed'), {
      response: { status: 400, data: { message: 'Password is too common' } }
    }))
    type('reset-password-new', 'a-long-password')
    type('reset-password-confirm', 'a-long-password')
    await submit()

    expect(dialog().querySelector('[role="alert"]').textContent).toContain('Password is too common')
    expect(wrapper.emitted('done')).toBeUndefined()
  })
})
