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
import NewUserDialog from './NewUserDialog.vue'
import { useSession } from '../../store/session'
import type { PrincipalsApi } from './accessPrincipals'
import { createErrorToast } from '../../helpers/toast'

type ErrorLike = { response?: { data?: { message?: string } }, message?: string } | undefined

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error: ErrorLike) => error?.response?.data?.message ?? error?.message ?? String(error)
}))

const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]')!

const type = (id: string, value: string) => {
  const input = document.getElementById(id) as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input'))
}

const submit = async () => {
  dialog().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

const fill = ({ name = 'carol', displayName = 'Carol Example', password = 'a-long-password' }: { name?: string, displayName?: string, password?: string } = {}) => {
  type('new-user-name', name)
  type('new-user-display-name', displayName)
  type('new-user-password', password)
}

describe('NewUserDialog', () => {
  let wrapper: VueWrapper | undefined
  let api: { create: Mock, setPassword: Mock }
  let calls: string[]

  beforeEach(async () => {
    calls = []
    api = {
      create: vi.fn(() => { calls.push('create'); return Promise.resolve({ data: { name: 'carol' } }) }),
      setPassword: vi.fn(() => { calls.push('setPassword'); return Promise.resolve({ status: 204 }) })
    }
    vi.mocked(createErrorToast).mockClear()
    // The fakes answer with the shape the dialog reads, not with the full API responses.
    wrapper = mount(NewUserDialog, { props: { modelValue: true, api: api as unknown as Pick<PrincipalsApi, 'create' | 'setPassword'> }, attachTo: document.body })
    await flushPromises()
  })

  afterEach(() => {
    wrapper!.unmount()
    document.body.innerHTML = ''
    useSession().passwordMinLength.value = 12
  })

  it('creates the principal first and sets the initial password second', async () => {
    fill()
    await submit()

    expect(calls).toEqual(['create', 'setPassword'])
  })

  it('creates a principal of kind USER with the exact body', async () => {
    fill()
    await submit()

    expect(api.create).toHaveBeenCalledWith({ kind: 'USER', name: 'carol', displayName: 'Carol Example' })
  })

  it('sets the password of the new user with the exact value', async () => {
    fill()
    await submit()

    expect(api.setPassword).toHaveBeenCalledWith('carol', 'a-long-password')
  })

  it('leaves the display name out when it is blank', async () => {
    fill({ displayName: '' })
    await submit()

    expect(api.create).toHaveBeenCalledWith({ kind: 'USER', name: 'carol' })
  })

  it('reports the new user and closes', async () => {
    fill()
    await submit()

    expect(wrapper!.emitted('created')![0]).toEqual(['carol'])
    expect(wrapper!.emitted('update:modelValue')![0]).toEqual([false])
  })

  it('sends nothing for an initial password under 12 characters', async () => {
    fill({ password: 'short-11-ch' })
    await submit()

    expect(api.create).not.toHaveBeenCalled()
    expect(dialog().textContent).toContain('Use at least 12 characters')
  })

  it('names the configured minimum and sends nothing below it', async () => {
    useSession().passwordMinLength.value = 16
    fill({ password: 'a'.repeat(12) })
    await submit()

    expect(api.create).not.toHaveBeenCalled()
    expect(dialog().textContent).toContain('Use at least 16 characters')
  })

  it('accepts 8 characters when the configured minimum is 8', async () => {
    useSession().passwordMinLength.value = 8
    fill({ password: 'eight-08' })
    await submit()

    expect(api.create).toHaveBeenCalled()
  })

  it('sends nothing without a username', async () => {
    fill({ name: ' ' })
    await submit()

    expect(api.create).not.toHaveBeenCalled()
    expect(dialog().textContent).toContain('A name is required')
  })

  it('shows a name that is taken inside the dialog and sets no password', async () => {
    api.create.mockRejectedValue(Object.assign(new Error('failed'), {
      response: { status: 409, data: { message: 'The name carol is taken' } }
    }))
    fill()
    await submit()

    const alert = dialog().querySelector<HTMLElement>('[role="alert"][data-tone="error"]')
    expect(alert, 'the refusal must be drawn with the shared alert').not.toBeNull()
    expect(alert!.getAttribute('role'), 'a refusal is announced at once').toBe('alert')
    expect(alert!.textContent).toContain('The name carol is taken')
    expect(api.setPassword, 'no password for a user that was not created').not.toHaveBeenCalled()
  })

  it('tells the admin when the user exists but the password was refused', async () => {
    api.setPassword.mockRejectedValue(Object.assign(new Error('failed'), {
      response: { status: 400, data: { message: 'Password is too common' } }
    }))
    fill()
    await submit()

    expect(createErrorToast).toHaveBeenCalledWith(expect.stringContaining('Password is too common'))
    expect(wrapper!.emitted('created'), 'the list must show the user that now exists').toHaveLength(1)
  })
})
