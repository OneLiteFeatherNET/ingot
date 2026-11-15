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
import PasswordCard from './PasswordCard.vue'

const toast = vi.hoisted(() => ({ success: vi.fn() }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: toast.success,
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

describe('PasswordCard', () => {
  let wrapper
  let api

  const mountCard = (props = {}) => {
    wrapper = mount(PasswordCard, { props: { api, available: true, ...props }, attachTo: document.body })
    return wrapper
  }

  const fill = async (current, next, confirm = next) => {
    await wrapper.find('#account-current-password').setValue(current)
    await wrapper.find('#account-new-password').setValue(next)
    await wrapper.find('#account-confirm-password').setValue(confirm)
  }

  const submit = async () => {
    await wrapper.find('form').trigger('submit')
    await flushPromises()
  }

  beforeEach(() => {
    toast.success.mockClear()
    api = { changePassword: vi.fn().mockResolvedValue({ status: 204 }) }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  it('sends the exact payload to change the password', async () => {
    mountCard()

    await fill('old-password-12', 'new-password-123')
    await submit()

    expect(api.changePassword).toHaveBeenCalledWith({
      currentPassword: 'old-password-12',
      newPassword: 'new-password-123'
    })
  })

  it('clears the form and shows a snackbar once the password is changed', async () => {
    mountCard()

    await fill('old-password-12', 'new-password-123')
    await submit()

    expect(wrapper.find('#account-current-password').element.value, 'a password must not linger in the form').toBe('')
    expect(wrapper.find('#account-new-password').element.value).toBe('')
    expect(wrapper.find('#account-confirm-password').element.value).toBe('')
    expect(toast.success).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })

  it('hints that the new password needs at least 12 characters', () => {
    mountCard()

    expect(wrapper.text()).toContain('At least 12 characters')
  })

  it('sends nothing for a new password under 12 characters', async () => {
    mountCard()

    await fill('old-password-12', 'short')
    await submit()

    expect(api.changePassword, 'the length is checked before the request').not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Use at least 12 characters')
  })

  it('sends nothing when the confirmation differs', async () => {
    mountCard()

    await fill('old-password-12', 'new-password-123', 'new-password-124')
    await submit()

    expect(api.changePassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Passwords do not match')
  })

  it('asks for the current password', async () => {
    mountCard()

    await fill('', 'new-password-123')
    await submit()

    expect(api.changePassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Enter your current password')
  })

  it('shows a wrong current password as an inline error and keeps what was typed', async () => {
    api.changePassword.mockRejectedValue(Object.assign(new Error('failed'), {
      response: { status: 400, data: { message: 'The current password is wrong' } }
    }))
    mountCard()

    await fill('wrong-password-1', 'new-password-123')
    await submit()

    expect(wrapper.find('[role="alert"]').text()).toContain('The current password is wrong')
    expect(toast.success).not.toHaveBeenCalled()
    expect(wrapper.find('#account-new-password').element.value, 'the new password stays for another try').toBe('new-password-123')
  })

  it('hides the form in token mode and says why', () => {
    mountCard({ available: false })

    expect(wrapper.find('form').exists(), 'a token session has no password to change').toBe(false)
    expect(wrapper.text()).toContain('signed in with an access token')
  })
})
