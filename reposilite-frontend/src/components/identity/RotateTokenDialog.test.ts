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

import { describe, it, expect, vi, beforeEach, afterEach, type Mock } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import RotateTokenDialog from './RotateTokenDialog.vue'
import type { IdentityToken, IdentityTokenApi } from './identityTokens'

const dialog = () => document.querySelector('[role="dialog"]') as HTMLElement
const rotateButton = () =>
  [...dialog().querySelectorAll('button')].find(node => node.textContent?.trim() === 'Rotate secret') as HTMLButtonElement

describe('RotateTokenDialog', () => {
  let wrapper: VueWrapper
  let api: { rotate: Mock }

  beforeEach(async () => {
    api = { rotate: vi.fn().mockResolvedValue({ data: { token: { name: 'laptop' }, secret: 'fresh' } }) }
    wrapper = mount(RotateTokenDialog, {
      props: { modelValue: true, token: { name: 'laptop' } as IdentityToken, api: api as unknown as IdentityTokenApi },
      attachTo: document.body
    }) as VueWrapper
    await flushPromises()
  })

  afterEach(() => {
    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('says that the old secret stops working', () => {
    expect(dialog().textContent).toContain('The old secret stops working')
  })

  it('rotates the token by its name and hands on the new secret', async () => {
    rotateButton().click()
    await flushPromises()

    expect(api.rotate).toHaveBeenCalledWith('laptop')
    expect(wrapper.emitted('rotated')?.[0]?.[0]).toEqual({ token: { name: 'laptop' }, secret: 'fresh' })
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
  })

  it('shows a failed rotation inside the dialog', async () => {
    api.rotate.mockRejectedValue(Object.assign(new Error('failed'), {
      response: { status: 404, data: { message: 'Token not found' } }
    }))
    rotateButton().click()
    await flushPromises()

    expect((dialog().querySelector('[role="alert"]') as HTMLElement).textContent).toContain('Token not found')
    expect(wrapper.emitted('rotated')).toBeUndefined()
  })
})
