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
import PrincipalActionsMenu from './PrincipalActionsMenu.vue'
import { createErrorToast, createSuccessToast } from '../../helpers/toast'

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

const ALICE = { name: 'alice', kind: 'USER', displayName: 'Alice Example', disabled: false }

const rejection = (status, message) =>
  Object.assign(new Error('failed'), { response: { status, data: { message } } })

const dialog = () => document.querySelector('[role="dialog"]')
const dialogButton = (label) =>
  [...dialog().querySelectorAll('button')].find(node => node.textContent.trim() === label)

const typeInDialog = async (value) => {
  const input = dialog().querySelector('input')
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('PrincipalActionsMenu', () => {
  let wrapper
  let api

  const mountMenu = async (props = {}) => {
    wrapper = mount(PrincipalActionsMenu, { props: { principal: ALICE, api, ...props }, attachTo: document.body })
    await flushPromises()
  }

  const chooseItem = async (test) => {
    await wrapper.find('[data-test="actions"]').trigger('click')
    await wrapper.find(`[data-test="${test}"]`).trigger('click')
    await flushPromises()
  }

  beforeEach(() => {
    vi.mocked(createErrorToast).mockClear()
    vi.mocked(createSuccessToast).mockClear()
    api = {
      get: vi.fn().mockResolvedValue({ data: { ...ALICE }, headers: { etag: '"7"' } }),
      update: vi.fn().mockResolvedValue({ data: { ...ALICE, disabled: true } }),
      setPassword: vi.fn().mockResolvedValue({ status: 204 }),
      grantInstanceAdmin: vi.fn().mockResolvedValue({ data: { ...ALICE, instanceRole: true } }),
      revokeInstanceAdmin: vi.fn().mockResolvedValue({ data: { ...ALICE, instanceRole: false } }),
      remove: vi.fn().mockResolvedValue({ status: 204 })
    }
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  describe('menu items', () => {
    it('offers every action for a user', async () => {
      await mountMenu()
      await wrapper.find('[data-test="actions"]').trigger('click')

      expect(wrapper.findAll('[role="menuitem"]').map(item => item.text()))
        .toEqual(['Reset password', 'Disable', 'Make instance admin', 'Delete'])
    })

    it('offers Enable for a disabled principal', async () => {
      await mountMenu({ principal: { ...ALICE, disabled: true } })
      await wrapper.find('[data-test="actions"]').trigger('click')

      expect(wrapper.find('[data-test="toggle-disabled"]').text()).toBe('Enable')
    })

    it('offers Remove instance admin for an instance admin', async () => {
      await mountMenu({ instanceAdmin: true })
      await wrapper.find('[data-test="actions"]').trigger('click')

      expect(wrapper.find('[data-test="toggle-admin"]').text()).toBe('Remove instance admin')
    })

    it('offers a machine account neither a password nor the admin role', async () => {
      await mountMenu({ principal: { name: 'ci-bot', kind: 'MACHINE', disabled: false } })
      await wrapper.find('[data-test="actions"]').trigger('click')

      expect(wrapper.findAll('[role="menuitem"]').map(item => item.text())).toEqual(['Disable', 'Delete'])
    })
  })

  describe('disable and enable', () => {
    it('reads the principal again and sends the fresh entity tag as If-Match when disabling', async () => {
      await mountMenu()

      await chooseItem('toggle-disabled')

      expect(api.get).toHaveBeenCalledWith('alice')
      expect(api.update).toHaveBeenCalledWith('alice', { disabled: true }, '"7"')
      expect(api.get.mock.invocationCallOrder[0], 'the read comes before the write')
        .toBeLessThan(api.update.mock.invocationCallOrder[0])
    })

    it('sends the fresh entity tag as If-Match when enabling', async () => {
      api.get.mockResolvedValue({ data: { ...ALICE, disabled: true }, headers: { etag: '"9"' } })
      await mountMenu({ principal: { ...ALICE, disabled: true } })

      await chooseItem('toggle-disabled')

      expect(api.update).toHaveBeenCalledWith('alice', { disabled: false }, '"9"')
    })

    it('reports the changed principal', async () => {
      await mountMenu()

      await chooseItem('toggle-disabled')

      expect(wrapper.emitted('changed')[0][0]).toMatchObject({ name: 'alice', disabled: true })
    })

    it('shows the server message of a refused change in a snackbar', async () => {
      api.update.mockRejectedValue(rejection(409, 'The last instance admin cannot be disabled.'))
      await mountMenu()

      await chooseItem('toggle-disabled')

      expect(createErrorToast).toHaveBeenCalledWith('The last instance admin cannot be disabled.')
      expect(wrapper.emitted('changed')).toBeUndefined()
    })

    it('asks for a reload instead of overwriting after a 412', async () => {
      api.update.mockRejectedValue(rejection(412, 'Precondition failed'))
      await mountMenu()

      await chooseItem('toggle-disabled')

      expect(createErrorToast).toHaveBeenCalledWith(expect.stringContaining('changed in the meantime'))
      expect(wrapper.emitted('changed')[0], 'the caller reads the principal again').toEqual([])
    })
  })

  describe('instance admin', () => {
    it('asks first and sends nothing until it is confirmed', async () => {
      await mountMenu()

      await chooseItem('toggle-admin')

      expect(dialog().textContent).toContain('Make instance admin')
      expect(api.grantInstanceAdmin).not.toHaveBeenCalled()
    })

    it('grants the role with exactly one PUT call after the confirmation', async () => {
      await mountMenu()
      await chooseItem('toggle-admin')

      dialogButton('Make instance admin').click()
      await flushPromises()

      expect(api.grantInstanceAdmin).toHaveBeenCalledTimes(1)
      expect(api.grantInstanceAdmin).toHaveBeenCalledWith('alice')
      expect(api.revokeInstanceAdmin).not.toHaveBeenCalled()
    })

    it('revokes the role with exactly one DELETE call after the confirmation', async () => {
      await mountMenu({ instanceAdmin: true })
      await chooseItem('toggle-admin')

      dialogButton('Remove instance admin').click()
      await flushPromises()

      expect(api.revokeInstanceAdmin).toHaveBeenCalledTimes(1)
      expect(api.revokeInstanceAdmin).toHaveBeenCalledWith('alice')
      expect(api.grantInstanceAdmin).not.toHaveBeenCalled()
    })

    it('sends nothing when the confirmation is cancelled', async () => {
      await mountMenu()
      await chooseItem('toggle-admin')

      dialogButton('Cancel').click()
      await flushPromises()

      expect(api.grantInstanceAdmin).not.toHaveBeenCalled()
    })

    it('shows the server message for the last instance admin inside the dialog', async () => {
      api.revokeInstanceAdmin.mockRejectedValue(rejection(409, 'The last instance admin cannot be demoted.'))
      await mountMenu({ instanceAdmin: true })
      await chooseItem('toggle-admin')

      dialogButton('Remove instance admin').click()
      await flushPromises()

      expect(dialog().querySelector('[role="alert"]').textContent).toBe('The last instance admin cannot be demoted.')
    })
  })

  describe('delete', () => {
    it('keeps the delete button disabled until the username is typed', async () => {
      await mountMenu()
      await chooseItem('delete')

      expect(dialogButton('Delete').disabled).toBe(true)
      await typeInDialog('alic')
      expect(dialogButton('Delete').disabled, 'a partial username does not count').toBe(true)
      await typeInDialog('alice')
      expect(dialogButton('Delete').disabled).toBe(false)
    })

    it('deletes by name once the username is typed and reports it', async () => {
      await mountMenu()
      await chooseItem('delete')
      await typeInDialog('alice')

      dialogButton('Delete').click()
      await flushPromises()

      expect(api.remove).toHaveBeenCalledWith('alice')
      expect(wrapper.emitted('deleted')).toHaveLength(1)
    })

    it('shows the server message for the last instance admin inside the dialog', async () => {
      api.remove.mockRejectedValue(rejection(409, 'The last instance admin cannot be deleted.'))
      await mountMenu()
      await chooseItem('delete')
      await typeInDialog('alice')

      dialogButton('Delete').click()
      await flushPromises()

      expect(dialog().querySelector('[role="alert"]').textContent).toBe('The last instance admin cannot be deleted.')
      expect(wrapper.emitted('deleted')).toBeUndefined()
    })
  })

  describe('reset password', () => {
    it('opens the dialog for the user', async () => {
      await mountMenu()

      await chooseItem('reset-password')

      expect(dialog().textContent).toContain('Reset password')
      expect(dialog().textContent).toContain('alice')
    })
  })
})
