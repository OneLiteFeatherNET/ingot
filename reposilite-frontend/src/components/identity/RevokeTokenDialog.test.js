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
import RevokeTokenDialog from './RevokeTokenDialog.vue'

const dialog = () => document.querySelector('[role="dialog"]')
const revokeButton = () =>
  [...dialog().querySelectorAll('button')].find(node => node.textContent.trim() === 'Revoke')

const type = (value) => {
  const input = dialog().querySelector('input')
  input.value = value
  input.dispatchEvent(new Event('input'))
  return flushPromises()
}

describe('RevokeTokenDialog', () => {
  let wrapper
  let api

  beforeEach(async () => {
    api = { revoke: vi.fn().mockResolvedValue({ status: 204 }) }
    wrapper = mount(RevokeTokenDialog, {
      props: { modelValue: true, token: { name: 'laptop' }, api },
      attachTo: document.body
    })
    await flushPromises()
  })

  afterEach(() => {
    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('names the token that will stop working', () => {
    expect(dialog().textContent).toContain('laptop')
  })

  it('keeps the revoke button disabled until the name is typed', () => {
    expect(revokeButton().disabled, 'nothing is typed yet').toBe(true)
  })

  it('keeps the button disabled for a name that differs in case', async () => {
    await type('Laptop')

    expect(revokeButton().disabled, 'the name must match exactly').toBe(true)
  })

  it('keeps the button disabled for a partial name', async () => {
    await type('lap')

    expect(revokeButton().disabled).toBe(true)
  })

  it('enables the button once the name is typed exactly', async () => {
    await type('laptop')

    expect(revokeButton().disabled).toBe(false)
  })

  it('revokes the token by its name and reports it', async () => {
    await type('laptop')
    revokeButton().click()
    await flushPromises()

    expect(api.revoke).toHaveBeenCalledWith('laptop')
    expect(wrapper.emitted('revoked')[0]).toEqual([{ name: 'laptop' }])
    expect(wrapper.emitted('update:modelValue')[0]).toEqual([false])
  })

  it('shows a failed revoke inside the dialog', async () => {
    api.revoke.mockRejectedValue(Object.assign(new Error('failed'), {
      response: { status: 404, data: { message: 'Token not found' } }
    }))
    await type('laptop')
    revokeButton().click()
    await flushPromises()

    expect(dialog().querySelector('[role="alert"]').textContent).toContain('Token not found')
    expect(wrapper.emitted('revoked')).toBeUndefined()
  })
})
