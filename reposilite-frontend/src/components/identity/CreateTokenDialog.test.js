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
import CreateTokenDialog from './CreateTokenDialog.vue'
import { setClock, resetClock } from '../../helpers/clock'

// 2026-06-15 12:00:00 UTC: the expiry is counted from the injected clock, never the system time.
const FIXED = new Date(Date.UTC(2026, 5, 15, 12))

const dialog = () => document.querySelector('[role="dialog"]')
const field = (id) => document.getElementById(id)
const segment = (label) =>
  [...dialog().querySelectorAll('button[aria-pressed]')].find(node => node.textContent.trim() === label)

const type = (id, value) => {
  field(id).value = value
  field(id).dispatchEvent(new Event('input'))
}

const submit = async () => {
  dialog().querySelector('form').dispatchEvent(new Event('submit'))
  await flushPromises()
}

const pick = async (label) => {
  segment(label).click()
  await flushPromises()
}

const check = async (id) => {
  field(id).click()
  await flushPromises()
}

describe('CreateTokenDialog', () => {
  let wrapper
  let api

  beforeEach(async () => {
    setClock(() => FIXED)
    api = {
      create: vi.fn().mockResolvedValue({ data: { token: { name: 'laptop' }, secret: 's3cr3t' } })
    }
    wrapper = mount(CreateTokenDialog, { props: { modelValue: true, api }, attachTo: document.body })
    await flushPromises()
  })

  afterEach(() => {
    wrapper.unmount()
    document.body.innerHTML = ''
    resetClock()
  })

  it('names the dialog after its heading', () => {
    const heading = dialog().querySelector('h2')

    expect(heading.textContent.trim()).toBe('Create token')
    expect(dialog().getAttribute('aria-labelledby'), 'screen readers announce the dialog by its heading')
      .toBe(heading.id)
  })

  it('asks for a name and sends nothing while the name is empty', async () => {
    await submit()

    expect(api.create, 'an invalid form must not reach the server').not.toHaveBeenCalled()
    expect(dialog().textContent).toContain('Token name is required')
  })

  it('rejects a name with a colon before it is sent', async () => {
    type('create-token-name', 'a:b')
    await submit()

    expect(api.create).not.toHaveBeenCalled()
    expect(dialog().textContent).toContain("Token name must not contain ':'")
  })

  it('selects 90 days as the default expiry', () => {
    expect(segment('90 days').getAttribute('aria-pressed'), '90 days is the default preset').toBe('true')
    expect(segment('Never').getAttribute('aria-pressed')).toBe('false')
  })

  it.each([
    ['30 days', '2026-07-15T12:00:00.000Z'],
    ['90 days', '2026-09-13T12:00:00.000Z'],
    ['365 days', '2027-06-15T12:00:00.000Z'],
    ['Never', null]
  ])('turns the %s preset into the expiry date', async (label, expiresAt) => {
    type('create-token-name', 'laptop')
    await pick(label)
    await submit()

    expect(api.create.mock.calls[0][0].expiresAt).toBe(expiresAt)
  })

  it('sends the exact body with the description and the checked scopes', async () => {
    type('create-token-name', ' laptop ')
    type('create-token-description', 'my laptop')
    await check('create-token-scope-read')
    await check('create-token-scope-write')
    await submit()

    expect(api.create).toHaveBeenCalledWith({
      name: 'laptop',
      description: 'my laptop',
      expiresAt: '2026-09-13T12:00:00.000Z',
      scopes: ['READ', 'WRITE']
    })
  })

  it('sends scopes as null when no box is checked', async () => {
    type('create-token-name', 'laptop')
    await submit()

    expect(api.create).toHaveBeenCalledWith({
      name: 'laptop',
      expiresAt: '2026-09-13T12:00:00.000Z',
      scopes: null
    })
  })

  it('hands the new token and its secret to the parent and closes', async () => {
    type('create-token-name', 'laptop')
    await submit()

    expect(wrapper.emitted('created')[0][0]).toEqual({ token: { name: 'laptop' }, secret: 's3cr3t' })
    expect(wrapper.emitted('update:modelValue')[0], 'the dialog asks to be closed').toEqual([false])
  })

  it('shows the failure inside the dialog and keeps it open', async () => {
    api.create.mockRejectedValue(Object.assign(new Error('failed'), {
      response: { status: 409, data: { message: "Token 'laptop' already exists" } }
    }))
    type('create-token-name', 'laptop')
    await submit()

    expect(dialog().querySelector('[role="alert"]').textContent).toContain("Token 'laptop' already exists")
    expect(wrapper.emitted('created'), 'a failed create hands nothing on').toBeUndefined()
  })
})
