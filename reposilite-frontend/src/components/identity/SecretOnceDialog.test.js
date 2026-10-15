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
import SecretOnceDialog from './SecretOnceDialog.vue'

const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: toast.success,
  createErrorToast: toast.error,
  errorMessage: (error) => error?.message ?? `${error}`
}))

const SECRET = 'ingot-secret-0123456789'

const dialog = () => document.querySelector('[role="dialog"]')
const buttonByLabel = (label) =>
  [...dialog().querySelectorAll('button')].find(node => node.textContent.trim() === label)

describe('SecretOnceDialog', () => {
  let wrapper

  beforeEach(async () => {
    toast.success.mockClear()
    toast.error.mockClear()
    localStorage.clear()
    sessionStorage.clear()
    wrapper = mount(SecretOnceDialog, {
      props: { modelValue: true, secret: SECRET, tokenName: 'laptop' },
      attachTo: document.body
    })
    await flushPromises()
  })

  afterEach(() => {
    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('shows the secret in a monospace field', () => {
    const input = dialog().querySelector('input[readonly]')

    expect(input.value).toBe(SECRET)
    expect(input.className, 'a secret is easier to compare in a monospace face').toContain('font-mono')
  })

  it('warns that the secret will not be shown again', () => {
    expect(dialog().textContent).toContain('You will not see this secret again')
  })

  it('names the token the secret belongs to', () => {
    expect(dialog().textContent).toContain('laptop')
  })

  it('copies the secret and reports it in a snackbar', async () => {
    const writeText = vi.fn().mockResolvedValue()
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

    buttonByLabel('Copy').click()
    await flushPromises()

    expect(writeText).toHaveBeenCalledWith(SECRET)
    expect(toast.success).toHaveBeenCalledTimes(1)
  })

  it('reports a clipboard that refuses the copy', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('denied'))
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

    buttonByLabel('Copy').click()
    await flushPromises()

    expect(toast.error, 'the user must learn that nothing was copied').toHaveBeenCalledTimes(1)
  })

  it('does not write the secret to a storage or to the address', () => {
    const everything = JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage })

    expect(everything, 'the secret must stay in memory').not.toContain(SECRET)
    expect(window.location.href, 'the secret must not reach the URL').not.toContain(SECRET)
  })

  it('offers a single close action', () => {
    const labels = [...dialog().querySelectorAll('button')].map(node => node.textContent.trim())

    expect(labels.filter(label => label === 'Close')).toHaveLength(1)
  })

  it('asks to be closed when the close action is used', async () => {
    buttonByLabel('Close').click()
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')[0]).toEqual([false])
  })
})
