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
import TokenList from './TokenList.vue'
import { setClock, resetClock } from '../../helpers/clock'

// The API sends an Instant as epoch seconds: the fixtures carry the same number.
const wireInstant = (iso) => Date.parse(iso) / 1000

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const LAPTOP = {
  name: 'laptop',
  description: 'my laptop',
  createdAt: [2026, 10, 4],
  expiresAt: wireInstant('2027-01-02T00:00:00Z'),
  scopes: ['READ', 'WRITE'],
  lastUsedAt: wireInstant('2026-10-14T08:30:00Z')
}

const CI = {
  name: 'ci',
  description: '',
  createdAt: [2026, 10, 5],
  expiresAt: null,
  scopes: null,
  lastUsedAt: null
}

const dialogs = () => [...document.querySelectorAll('[role="dialog"]')]
const dialog = () => dialogs()[0]
const dialogButton = (label) =>
  dialogs().flatMap(node => [...node.querySelectorAll('button')]).find(node => node.textContent.trim() === label)
const rows = (wrapper) => wrapper.findAll('[data-test="token-row"]')
const rowButton = (row, label) => row.findAll('button').find(button => button.text() === label)
const headerButton = (wrapper, label) => wrapper.findAll('button').find(button => button.text() === label)

const type = (id, value) => {
  const input = document.getElementById(id)
  input.value = value
  input.dispatchEvent(new Event('input'))
}

describe('TokenList', () => {
  let wrapper
  let api

  const mountList = async (props = {}) => {
    wrapper = mount(TokenList, { props: { api, ...props }, attachTo: document.body })
    await flushPromises()
  }

  beforeEach(() => {
    setClock(() => new Date(Date.UTC(2026, 9, 15, 12)))
    api = {
      list: vi.fn().mockResolvedValue({ data: [LAPTOP, CI] }),
      create: vi.fn().mockResolvedValue({ data: { token: { name: 'new' }, secret: 'created-secret' } }),
      rotate: vi.fn().mockResolvedValue({ data: { token: LAPTOP, secret: 'rotated-secret' } }),
      revoke: vi.fn().mockResolvedValue({ status: 204 })
    }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
    resetClock()
  })

  describe('rows', () => {
    it('lists one row per token with its name and description', async () => {
      await mountList()

      expect(rows(wrapper)).toHaveLength(2)
      expect(rows(wrapper)[0].text()).toContain('laptop')
      expect(rows(wrapper)[0].text()).toContain('my laptop')
    })

    it('shows the creation date and the expiry date', async () => {
      await mountList()

      const text = rows(wrapper)[0].text()

      expect(text).toContain('2026-10-04')
      expect(text).toContain('2027-01-02')
    })

    it('shows the last use as a relative time with the exact time in a tooltip', async () => {
      await mountList()

      const lastUsed = rows(wrapper)[0].find('[data-test="last-used"]')

      expect(lastUsed.text()).toContain('1 day ago')
      expect(lastUsed.attributes('title'), 'the tooltip carries the absolute timestamp').toBe('2026-10-14 08:30 UTC')
    })

    it('shows the address of the last use only when the server sent one', async () => {
      api.list.mockResolvedValue({ data: [{ ...LAPTOP, lastUsedIp: '203.0.113.7' }, LAPTOP] })
      await mountList()

      expect(rows(wrapper)[0].find('[data-test="last-used-ip"]').text()).toBe('from 203.0.113.7')
      expect(rows(wrapper)[1].find('[data-test="last-used-ip"]').exists(), 'no address, no label').toBe(false)
    })

    it('says Never for a token that does not expire and Never used for one that was not used', async () => {
      await mountList()

      const text = rows(wrapper)[1].text()

      expect(text, 'no expiry date').toContain('Never')
      expect(text, 'no last use').toContain('Never used')
    })

    it('shows the action names of a narrowed token as chips', async () => {
      await mountList()

      const chips = rows(wrapper)[0].findAll('[data-test="scope"]').map(chip => chip.text())

      expect(chips).toEqual(['READ', 'WRITE'])
    })

    it('says that a token without scopes has all rights of the teams', async () => {
      await mountList()

      expect(rows(wrapper)[1].text()).toContain('All rights of your teams')
      expect(rows(wrapper)[1].findAll('[data-test="scope"]')).toHaveLength(0)
    })

    it('does not offer rotation when the api cannot rotate', async () => {
      delete api.rotate
      await mountList()

      expect(rowButton(rows(wrapper)[0], 'Rotate'), 'an api without rotate gets no rotate action').toBeUndefined()
      expect(rowButton(rows(wrapper)[0], 'Revoke')).toBeDefined()
    })
  })

  describe('empty state', () => {
    beforeEach(() => {
      api.list.mockResolvedValue({ data: [] })
    })

    it('explains that there are no tokens and offers to create the first one', async () => {
      await mountList()

      expect(rows(wrapper)).toHaveLength(0)
      expect(wrapper.text()).toContain('You have no tokens yet')
      expect(headerButton(wrapper, 'Create your first token'), 'the empty state has a clear call to action').toBeDefined()
    })

    it('opens the create dialog from the call to action', async () => {
      await mountList()

      await headerButton(wrapper, 'Create your first token').trigger('click')
      await flushPromises()

      expect(dialog().textContent).toContain('Create token')
    })
  })

  describe('failure to load', () => {
    it('shows the failure and loads again on retry', async () => {
      api.list.mockRejectedValueOnce(new Error('offline'))
      await mountList()

      expect(wrapper.find('[role="alert"]').text()).toContain('offline')

      await headerButton(wrapper, 'Retry').trigger('click')
      await flushPromises()

      expect(rows(wrapper)).toHaveLength(2)
    })
  })

  describe('create', () => {
    it('shows the secret of a new token once and lists the tokens again', async () => {
      await mountList()

      await headerButton(wrapper, 'Create token').trigger('click')
      await flushPromises()
      type('create-token-name', 'new')
      dialog().querySelector('form').dispatchEvent(new Event('submit'))
      await flushPromises()

      expect(api.create).toHaveBeenCalledTimes(1)
      expect(document.body.textContent, 'the secret-once dialog shows the new secret').toContain('You will not see this secret again')
      expect(document.querySelector('input[readonly]').value).toBe('created-secret')
      expect(api.list, 'the list is read again to show the new token').toHaveBeenCalledTimes(2)
    })

    it('forgets the secret when the dialog is closed', async () => {
      await mountList()
      await headerButton(wrapper, 'Create token').trigger('click')
      await flushPromises()
      type('create-token-name', 'new')
      dialog().querySelector('form').dispatchEvent(new Event('submit'))
      await flushPromises()

      dialogButton('Close').click()
      await flushPromises()

      expect(document.querySelector('input[readonly]')?.value ?? '', 'the secret must not linger').not.toBe('created-secret')
    })
  })

  describe('rotate', () => {
    it('asks for confirmation, rotates by name and shows the new secret', async () => {
      await mountList()

      await rowButton(rows(wrapper)[0], 'Rotate').trigger('click')
      await flushPromises()
      expect(dialog().textContent).toContain('The old secret stops working')
      expect(api.rotate, 'nothing happens before the confirmation').not.toHaveBeenCalled()

      dialogButton('Rotate secret').click()
      await flushPromises()

      expect(api.rotate).toHaveBeenCalledWith('laptop')
      expect(document.querySelector('input[readonly]').value).toBe('rotated-secret')
    })

    it('shows the Bearer and Basic examples for the rotated secret', async () => {
      await mountList()
      await rowButton(rows(wrapper)[0], 'Rotate').trigger('click')
      await flushPromises()

      dialogButton('Rotate secret').click()
      await flushPromises()

      const bearer = document.querySelector('[data-snippet="bearer"]').textContent
      const basic = document.querySelector('[data-snippet="basic"]').textContent
      expect(bearer).toContain('Authorization: Bearer rotated-secret')
      expect(basic).toContain('laptop:rotated-secret')
    })
  })

  describe('revoke', () => {
    it('revokes the token by name after its name was typed and lists again', async () => {
      await mountList()

      await rowButton(rows(wrapper)[1], 'Revoke').trigger('click')
      await flushPromises()
      type('revoke-token-confirm', 'ci')
      await flushPromises()
      dialogButton('Revoke').click()
      await flushPromises()

      expect(api.revoke).toHaveBeenCalledWith('ci')
      expect(api.list).toHaveBeenCalledTimes(2)
    })

    it('does not revoke while the name is not typed', async () => {
      await mountList()

      await rowButton(rows(wrapper)[1], 'Revoke').trigger('click')
      await flushPromises()
      dialogButton('Revoke').click()
      await flushPromises()

      expect(api.revoke).not.toHaveBeenCalled()
    })
  })
})
