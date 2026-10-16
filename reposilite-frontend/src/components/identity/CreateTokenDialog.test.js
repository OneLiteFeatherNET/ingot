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
  [...dialog().querySelectorAll('[role="radio"]')].find(node => node.textContent.trim() === label)
const createButton = () =>
  [...dialog().querySelectorAll('button')].find(node => node.textContent.trim() === 'Create')
const NEVER_WARNING = 'Tokens without expiry stay valid until revoked. Prefer an expiry and rotate the token.'

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

const choose = async (id) => {
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
    wrapper = mount(CreateTokenDialog, { props: { modelValue: true, api, locale: 'en-GB' }, attachTo: document.body })
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

  it('selects 90 days as the default expiry, so a token never starts without one', () => {
    expect(segment('90 days').getAttribute('aria-checked'), '90 days is the default preset').toBe('true')
    expect(segment('No expiry').getAttribute('aria-checked')).toBe('false')
  })

  it.each([
    ['30 days', '2026-07-15T12:00:00.000Z'],
    ['90 days', '2026-09-13T12:00:00.000Z'],
    ['1 year', '2027-06-15T12:00:00.000Z'],
    ['No expiry', null]
  ])('turns the %s preset into the expiry date', async (label, expiresAt) => {
    type('create-token-name', 'laptop')
    await pick(label)
    await submit()

    expect(api.create.mock.calls[0][0].expiresAt).toBe(expiresAt)
  })

  it('sends the exact body with the description and the expiry', async () => {
    type('create-token-name', ' laptop ')
    type('create-token-description', 'my laptop')
    await submit()

    expect(api.create).toHaveBeenCalledWith({
      name: 'laptop',
      description: 'my laptop',
      expiresAt: '2026-09-13T12:00:00.000Z',
      scopes: null
    })
  })

  it('defaults access to the same rights as the owner teams, which narrows nothing', async () => {
    type('create-token-name', 'laptop')
    await submit()

    expect(field('create-token-access-all').checked, 'same as my teams is the default').toBe(true)
    expect(api.create.mock.calls[0][0].scopes, 'full access sends no narrowing').toBeNull()
  })

  it('narrows a read only token to READ', async () => {
    type('create-token-name', 'ci')
    await choose('create-token-access-read')
    await submit()

    expect(field('create-token-access-read').checked, 'read only is the chosen access').toBe(true)
    expect(api.create.mock.calls[0][0].scopes).toEqual(['READ'])
  })

  it('shows what each access choice allows, beside its radio', () => {
    expect(dialog().querySelector('#create-token-access-all-help').textContent.trim()).toBe('Everything your teams allow')
    expect(dialog().querySelector('#create-token-access-read-help').textContent.trim()).toBe('Download artifacts, no deploy')
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

  it('keeps Create disabled until the name is valid', async () => {
    expect(createButton().disabled, 'an empty name cannot be sent').toBe(true)

    type('create-token-name', 'laptop')
    await flushPromises()

    expect(createButton().disabled).toBe(false)
  })

  it('shows the resulting date under the control, in the dialog locale', async () => {
    await pick('30 days')

    expect(dialog().querySelector('#create-token-expiry-note').textContent.trim()).toBe('Expires on 15 Jul 2026')
  })

  it('warns in error tone when the token never expires', async () => {
    await pick('No expiry')

    const note = dialog().querySelector('#create-token-expiry-note')

    expect(note.textContent.trim()).toBe(NEVER_WARNING)
    expect(note.classList.contains('text-error'), 'the warning is in the error tone').toBe(true)
  })

  it('does not warn about an expiry the token has', () => {
    const note = dialog().querySelector('#create-token-expiry-note')

    expect(note.classList.contains('text-error')).toBe(false)
  })

  it('opens a date field for a custom expiry, bounded from tomorrow to five years ahead', async () => {
    await pick('Custom')

    expect(field('create-token-expiry-date'), 'a custom expiry asks for a date').not.toBeNull()
    expect(field('create-token-expiry-date').getAttribute('min')).toBe('2026-06-16')
    expect(field('create-token-expiry-date').getAttribute('max')).toBe('2031-06-15')
  })

  it('asks for a custom date and sends nothing without one', async () => {
    type('create-token-name', 'laptop')
    await pick('Custom')
    await submit()

    expect(api.create, 'a custom expiry needs a date before it is sent').not.toHaveBeenCalled()
    expect(dialog().textContent).toContain('Choose an expiry date')
  })

  it.each([
    ['2026-06-15', 'Choose a date from tomorrow on', 'today is too early'],
    ['2026-06-14', 'Choose a date from tomorrow on', 'a past date is too early'],
    ['2031-06-16', 'Choose a date up to 5 years from today', 'a date beyond five years is too late']
  ])('rejects the custom date %s because %s', async (value, message) => {
    type('create-token-name', 'laptop')
    await pick('Custom')
    type('create-token-expiry-date', value)
    await flushPromises()

    expect(dialog().textContent).toContain(message)
    expect(createButton().disabled, 'Create stays disabled for an invalid date').toBe(true)
  })

  it('sends a valid custom date as the end of that day in UTC', async () => {
    type('create-token-name', 'laptop')
    await pick('Custom')
    type('create-token-expiry-date', '2031-06-15')
    await submit()

    expect(api.create.mock.calls[0][0].expiresAt).toBe('2031-06-15T23:59:59.000Z')
  })
})
