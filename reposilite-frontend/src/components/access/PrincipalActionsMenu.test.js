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

// The menu is teleported to the body, so it is read from the document and not from the wrapper.
const menu = () => document.querySelector('[role="menu"]')
const item = (test) => document.querySelector(`[role="menu"] [data-test="${test}"]`)
const clickItem = async (test) => {
  item(test).click()
  await flushPromises()
}

const texts = () => [...document.querySelectorAll('[role="menuitem"]')].map(node => node.textContent.trim())

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
    await flushPromises()
    await clickItem(test)
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
    vi.restoreAllMocks()
    wrapper?.unmount()
    wrapper = undefined
    document.body.innerHTML = ''
  })

  describe('open state', () => {
    it('tells its row when the menu opens', async () => {
      await mountMenu()

      await wrapper.find('[data-test="actions"]').trigger('click')

      expect(wrapper.emitted('toggle')?.at(-1), 'the row needs to know to rise above the next one').toEqual([true])
    })

    it('tells its row when the menu closes after an item was chosen', async () => {
      await mountMenu()

      await chooseItem('reset-password')

      expect(wrapper.emitted('toggle')?.at(-1)).toEqual([false])
    })

    it('tells its row when Escape closes the menu', async () => {
      await mountMenu()
      await wrapper.find('[data-test="actions"]').trigger('click')

      menu().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await flushPromises()

      expect(wrapper.emitted('toggle')?.at(-1)).toEqual([false])
    })
  })

  describe('placement', () => {
    const openMenu = async () => {
      await wrapper.find('[data-test="actions"]').trigger('click')
      await flushPromises()
    }

    it('mounts the open menu directly under the body so no card can clip it', async () => {
      await mountMenu()

      await openMenu()

      expect(menu(), 'the menu is open').not.toBeNull()
      expect(menu().parentElement, 'a teleported menu is a child of the body').toBe(document.body)
      expect(wrapper.find('[role="menu"]').exists(), 'nothing of the menu stays inside the row').toBe(false)
    })

    it('mounts no menu while it is closed', async () => {
      await mountMenu()

      expect(menu()).toBeNull()
    })

    it('positions the menu from the trigger with fixed positioning', async () => {
      await mountMenu()
      const trigger = wrapper.find('[data-test="actions"]').element
      vi.spyOn(trigger, 'getBoundingClientRect').mockReturnValue({ top: 100, bottom: 140, left: 260, right: 300, width: 40, height: 40 })

      await openMenu()

      expect(menu().style.position).toBe('fixed')
      expect(menu().style.top, 'four pixels below the trigger').toBe('144px')
      expect(menu().style.right, 'right edges line up').toBe(`${window.innerWidth - 300}px`)
    })

    it('opens upwards when there is no room below the trigger', async () => {
      await mountMenu()
      const trigger = wrapper.find('[data-test="actions"]').element
      vi.spyOn(trigger, 'getBoundingClientRect').mockReturnValue({ top: 700, bottom: 740, left: 260, right: 300, width: 40, height: 40 })
      vi.spyOn(window, 'innerHeight', 'get').mockReturnValue(768)
      const original = Element.prototype.getBoundingClientRect
      vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function () {
        return this.getAttribute?.('role') === 'menu'
          ? { top: 0, bottom: 200, left: 0, right: 224, width: 224, height: 200 }
          : original.call(this)
      })

      await openMenu()

      expect(menu().style.bottom, 'the menu sits above the trigger').toBe(`${768 - 700 + 4}px`)
      expect(menu().style.top).toBe('')
    })
  })

  describe('keyboard and pointer', () => {
    const openMenu = async () => {
      await wrapper.find('[data-test="actions"]').trigger('click')
      await flushPromises()
    }

    it('moves the focus to the first item when it opens', async () => {
      await mountMenu()

      await openMenu()

      expect(document.activeElement).toBe(menu().querySelector('[role="menuitem"]'))
    })

    it('closes on Escape and returns the focus to the trigger', async () => {
      await mountMenu()
      await openMenu()

      item('delete').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await flushPromises()

      expect(menu(), 'Escape closes the menu').toBeNull()
      expect(document.activeElement, 'the focus goes back to where the user came from').toBe(wrapper.find('[data-test="actions"]').element)
    })

    it('closes on a click outside of the menu', async () => {
      await mountMenu()
      await openMenu()

      document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
      await flushPromises()

      expect(menu()).toBeNull()
    })

    it('stays open on a pointer press inside the menu', async () => {
      await mountMenu()
      await openMenu()

      menu().dispatchEvent(new Event('pointerdown', { bubbles: true }))
      await flushPromises()

      expect(menu()).not.toBeNull()
    })

    it('closes when the page scrolls, because the menu no longer lines up with its trigger', async () => {
      await mountMenu()
      await openMenu()

      window.dispatchEvent(new Event('scroll'))
      await flushPromises()

      expect(menu()).toBeNull()
    })
  })

  describe('menu items', () => {
    it('offers every action for a user', async () => {
      await mountMenu()
      await wrapper.find('[data-test="actions"]').trigger('click')

      await flushPromises()

      expect(texts()).toEqual(['Reset password', 'Disable', 'Make instance admin', 'Delete'])
    })

    it('offers Enable for a disabled principal', async () => {
      await mountMenu({ principal: { ...ALICE, disabled: true } })
      await wrapper.find('[data-test="actions"]').trigger('click')

      await flushPromises()

      expect(item('toggle-disabled').textContent.trim()).toBe('Enable')
    })

    it('offers Remove instance admin for an instance admin', async () => {
      await mountMenu({ instanceAdmin: true })
      await wrapper.find('[data-test="actions"]').trigger('click')

      await flushPromises()

      expect(item('toggle-admin').textContent.trim()).toBe('Remove instance admin')
    })

    it('offers a machine account neither a password nor the admin role', async () => {
      await mountMenu({ principal: { name: 'ci-bot', kind: 'MACHINE', disabled: false } })
      await wrapper.find('[data-test="actions"]').trigger('click')

      await flushPromises()

      expect(texts()).toEqual(['Disable', 'Delete'])
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
