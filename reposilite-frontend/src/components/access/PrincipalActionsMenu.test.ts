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
import PrincipalActionsMenu from './PrincipalActionsMenu.vue'
import type { PrincipalSummary } from './accessPrincipals'
import { createErrorToast, createSuccessToast } from '../../helpers/toast'

type Principal = Pick<PrincipalSummary, 'name' | 'kind' | 'displayName' | 'disabled'>
type ErrorLike = { message?: string; response?: { data?: { message?: string } } }

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error: ErrorLike | undefined) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

const ALICE: Principal = { name: 'alice', kind: 'USER', displayName: 'Alice Example', disabled: false }

const rejection = (status: number, message: string | undefined) =>
  Object.assign(new Error('failed'), { response: { status, data: { message } } })

// The menu is teleported to the body, so it is read from the document and not from the wrapper.
const menu = (): HTMLElement => document.querySelector<HTMLElement>('[role="menu"]')!
// The items are read by their visible name, the name a person sees and a screen reader announces.
const item = (label: string): HTMLElement =>
  [...document.querySelectorAll<HTMLElement>('[role="menu"] [role="menuitem"]')].find(node => node.textContent?.trim() === label)!
const clickItem = async (label: string) => {
  item(label).click()
  await flushPromises()
}

const texts = () => [...document.querySelectorAll('[role="menuitem"]')].map(node => node.textContent?.trim())

const dialog = (): HTMLElement => document.querySelector<HTMLElement>('[role="dialog"]')!
const dialogButton = (label: string) =>
  [...dialog().querySelectorAll('button')].find(node => node.textContent?.trim() === label)!

const typeInDialog = async (value: string) => {
  const input = dialog().querySelector<HTMLInputElement>('input')!
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('PrincipalActionsMenu', () => {
  let wrapper: VueWrapper
  // The trigger is the first button of the component, the button of the actions menu.
  const trigger = (): HTMLElement => wrapper.find<HTMLButtonElement>('button').element
  let api: Record<'get' | 'update' | 'setPassword' | 'grantInstanceAdmin' | 'revokeInstanceAdmin' | 'remove', Mock>

  const mountMenu = async (props: { principal?: Principal; instanceAdmin?: boolean } = {}) => {
    wrapper = mount(PrincipalActionsMenu, { props: { principal: ALICE, api, ...props }, attachTo: document.body })
    await flushPromises()
  }

  const chooseItem = async (label: string) => {
    await wrapper.find('button').trigger('click')
    await flushPromises()
    await clickItem(label)
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
    document.body.innerHTML = ''
  })

  describe('open state', () => {
    it('tells its row when the menu opens', async () => {
      await mountMenu()

      await wrapper.find('button').trigger('click')

      expect(wrapper.emitted('toggle')?.at(-1), 'the row needs to know to rise above the next one').toEqual([true])
    })

    it('tells its row when the menu closes after an item was chosen', async () => {
      await mountMenu()

      await chooseItem('Reset password')

      expect(wrapper.emitted('toggle')?.at(-1)).toEqual([false])
    })

    it('tells its row when Escape closes the menu', async () => {
      await mountMenu()
      await wrapper.find('button').trigger('click')

      menu().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await flushPromises()

      expect(wrapper.emitted('toggle')?.at(-1)).toEqual([false])
    })
  })

  describe('placement', () => {
    const openMenu = async () => {
      await wrapper.find('button').trigger('click')
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
      const button = trigger()
      vi.spyOn(button, 'getBoundingClientRect').mockReturnValue({ top: 100, bottom: 140, left: 260, right: 300, width: 40, height: 40 } as DOMRect)

      await openMenu()

      expect(menu().style.position).toBe('fixed')
      expect(menu().style.top, 'four pixels below the trigger').toBe('144px')
      expect(menu().style.right, 'right edges line up').toBe(`${window.innerWidth - 300}px`)
    })

    it('opens upwards when there is no room below the trigger', async () => {
      await mountMenu()
      const button = trigger()
      vi.spyOn(button, 'getBoundingClientRect').mockReturnValue({ top: 700, bottom: 740, left: 260, right: 300, width: 40, height: 40 } as DOMRect)
      vi.spyOn(window, 'innerHeight', 'get').mockReturnValue(768)
      const original = Element.prototype.getBoundingClientRect
      vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (this: Element) {
        return this.getAttribute?.('role') === 'menu'
          ? { top: 0, bottom: 200, left: 0, right: 224, width: 224, height: 200 } as DOMRect
          : original.call(this)
      })

      await openMenu()

      expect(menu().style.bottom, 'the menu sits above the trigger').toBe(`${768 - 700 + 4}px`)
      expect(menu().style.top).toBe('')
    })
  })

  describe('keyboard and pointer', () => {
    const openMenu = async () => {
      await wrapper.find('button').trigger('click')
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

      item('Delete').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await flushPromises()

      expect(menu(), 'Escape closes the menu').toBeNull()
      expect(document.activeElement, 'the focus goes back to where the user came from').toBe(trigger())
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

  })

  describe('scrolling and resizing', () => {
    const openMenu = async () => {
      await wrapper.find('button').trigger('click')
      await flushPromises()
    }
    // The trigger's rect is a fixed box, so a test can move it the way a scroll does.
    const moveTrigger = (top: number) => {
      const rect = { top, bottom: top + 40, left: 260, right: 300, width: 40, height: 40 }
      trigger().getBoundingClientRect = () => rect as DOMRect
    }
    const nextFrame = () => new Promise<void>(resolve => requestAnimationFrame(() => resolve()))

    it('opens without scrolling the page: every focus call passes preventScroll', async () => {
      await mountMenu()
      moveTrigger(200)
      const focus = vi.spyOn(HTMLElement.prototype, 'focus')
      // jsdom has no scrollIntoView, so the test installs one to see whether anything calls it.
      const scrollIntoView = vi.fn()
      Object.defineProperty(Element.prototype, 'scrollIntoView', { value: scrollIntoView, configurable: true, writable: true })

      try {
        await openMenu()

        expect(focus, 'the first item takes the focus').toHaveBeenCalled()
        expect(focus.mock.calls.every(([options]) => options?.preventScroll === true), 'no focus call scrolls the page').toBe(true)
        expect(scrollIntoView, 'nothing scrolls an element into view').not.toHaveBeenCalled()
      } finally {
        Reflect.deleteProperty(Element.prototype, 'scrollIntoView')
      }
    })

    it('stays open and follows its trigger when the page scrolls and the trigger stays on screen', async () => {
      await mountMenu()
      moveTrigger(200)
      await openMenu()
      expect(menu().style.top, 'the menu opens below the trigger').toBe('244px')

      moveTrigger(300)
      window.dispatchEvent(new Event('scroll'))
      await nextFrame()

      expect(menu(), 'the menu is still open').not.toBeNull()
      expect(menu().style.top, 'the menu moved with the trigger').toBe('344px')
    })

    it('closes when its trigger has scrolled out of the screen', async () => {
      await mountMenu()
      moveTrigger(200)
      await openMenu()

      moveTrigger(-80)
      window.dispatchEvent(new Event('scroll'))
      await nextFrame()

      expect(menu(), 'a menu without its trigger on screen closes').toBeNull()
    })

    it('closes when the window is resized, as the shared menu does', async () => {
      await mountMenu()
      moveTrigger(200)
      await openMenu()

      window.dispatchEvent(new Event('resize'))
      await flushPromises()

      expect(menu(), 'a resize closes the menu').toBeNull()
    })
  })

  describe('menu items', () => {
    it('offers every action for a user', async () => {
      await mountMenu()
      await wrapper.find('button').trigger('click')

      await flushPromises()

      expect(texts()).toEqual(['Reset password', 'Disable', 'Make instance admin', 'Delete'])
    })

    it('offers Enable for a disabled principal', async () => {
      await mountMenu({ principal: { ...ALICE, disabled: true } })
      await wrapper.find('button').trigger('click')

      await flushPromises()

      expect(texts()).toContain('Enable')
    })

    it('offers Remove instance admin for an instance admin', async () => {
      await mountMenu({ instanceAdmin: true })
      await wrapper.find('button').trigger('click')

      await flushPromises()

      expect(texts()).toContain('Remove instance admin')
    })

    it('offers a machine account neither a password nor the admin role', async () => {
      await mountMenu({ principal: { name: 'ci-bot', kind: 'MACHINE', disabled: false } })
      await wrapper.find('button').trigger('click')

      await flushPromises()

      expect(texts()).toEqual(['Disable', 'Delete'])
    })
  })

  describe('shared overflow menu', () => {
    it('draws the actions with the shared overflow menu', async () => {
      await mountMenu()

      expect(wrapper.findComponent({ name: 'MdOverflowMenu' }).exists(), 'the actions are the shared menu of the md primitives').toBe(true)
    })

    it('disables the change item while the change is still running', async () => {
      api.update.mockReturnValue(new Promise(() => {}))
      await mountMenu()
      await chooseItem('Disable')

      await wrapper.find('button').trigger('click')
      await flushPromises()

      expect(item('Disable').getAttribute('aria-disabled'), 'the item is announced as disabled until the answer comes').toBe('true')
    })

    it('draws the destructive item in the error role and keeps its focus ring', async () => {
      await mountMenu()
      await wrapper.find('button').trigger('click')
      await flushPromises()

      const classes = item('Delete').className
      expect(classes, 'the item uses the error role').toContain('text-error')
      expect(classes, 'the item keeps the focus ring of the menu').toContain('md-focus-ring')
      expect(classes, 'no important modifier overrides the focus ring').not.toContain('!text-error')
    })
  })

  describe('disable and enable', () => {
    it('reads the principal again and sends the fresh entity tag as If-Match when disabling', async () => {
      await mountMenu()

      await chooseItem('Disable')

      expect(api.get).toHaveBeenCalledWith('alice')
      expect(api.update).toHaveBeenCalledWith('alice', { disabled: true }, '"7"')
      expect(api.get.mock.invocationCallOrder[0], 'the read comes before the write')
        .toBeLessThan(api.update.mock.invocationCallOrder[0])
    })

    it('sends the fresh entity tag as If-Match when enabling', async () => {
      api.get.mockResolvedValue({ data: { ...ALICE, disabled: true }, headers: { etag: '"9"' } })
      await mountMenu({ principal: { ...ALICE, disabled: true } })

      await chooseItem('Enable')

      expect(api.update).toHaveBeenCalledWith('alice', { disabled: false }, '"9"')
    })

    it('reports the changed principal', async () => {
      await mountMenu()

      await chooseItem('Disable')

      expect(wrapper.emitted('changed')![0][0]).toMatchObject({ name: 'alice', disabled: true })
    })

    it('shows the server message of a refused change in a snackbar', async () => {
      api.update.mockRejectedValue(rejection(409, 'The last instance admin cannot be disabled.'))
      await mountMenu()

      await chooseItem('Disable')

      expect(createErrorToast).toHaveBeenCalledWith('The last instance admin cannot be disabled.')
      expect(wrapper.emitted('changed')).toBeUndefined()
    })

    it('asks for a reload instead of overwriting after a 412', async () => {
      api.update.mockRejectedValue(rejection(412, 'Precondition failed'))
      await mountMenu()

      await chooseItem('Disable')

      expect(createErrorToast).toHaveBeenCalledWith(expect.stringContaining('changed in the meantime'))
      expect(wrapper.emitted('changed')![0], 'the caller reads the principal again').toEqual([])
    })
  })

  describe('instance admin', () => {
    it('asks first and sends nothing until it is confirmed', async () => {
      await mountMenu()

      await chooseItem('Make instance admin')

      expect(dialog().textContent).toContain('Make instance admin')
      expect(api.grantInstanceAdmin).not.toHaveBeenCalled()
    })

    it('grants the role with exactly one PUT call after the confirmation', async () => {
      await mountMenu()
      await chooseItem('Make instance admin')

      dialogButton('Make instance admin').click()
      await flushPromises()

      expect(api.grantInstanceAdmin).toHaveBeenCalledTimes(1)
      expect(api.grantInstanceAdmin).toHaveBeenCalledWith('alice')
      expect(api.revokeInstanceAdmin).not.toHaveBeenCalled()
    })

    it('revokes the role with exactly one DELETE call after the confirmation', async () => {
      await mountMenu({ instanceAdmin: true })
      await chooseItem('Remove instance admin')

      dialogButton('Remove instance admin').click()
      await flushPromises()

      expect(api.revokeInstanceAdmin).toHaveBeenCalledTimes(1)
      expect(api.revokeInstanceAdmin).toHaveBeenCalledWith('alice')
      expect(api.grantInstanceAdmin).not.toHaveBeenCalled()
    })

    it('sends nothing when the confirmation is cancelled', async () => {
      await mountMenu()
      await chooseItem('Make instance admin')

      dialogButton('Cancel').click()
      await flushPromises()

      expect(api.grantInstanceAdmin).not.toHaveBeenCalled()
    })

    it('shows the server message for the last instance admin inside the dialog', async () => {
      api.revokeInstanceAdmin.mockRejectedValue(rejection(409, 'The last instance admin cannot be demoted.'))
      await mountMenu({ instanceAdmin: true })
      await chooseItem('Remove instance admin')

      dialogButton('Remove instance admin').click()
      await flushPromises()

      expect(dialog().querySelector<HTMLElement>('[role="alert"]')!.textContent).toBe('The last instance admin cannot be demoted.')
    })
  })

  describe('delete', () => {
    // The username and the display name are swapped on purpose: the dialog must name what has to be typed.
    const SWAPPED: Principal = { name: 'sdsdfsdf', kind: 'USER', displayName: 'sdfsdfsdf', disabled: true }

    it('names the username in the dialog text, not the display name', async () => {
      await mountMenu({ principal: SWAPPED })
      await chooseItem('Delete')

      expect(dialog().textContent, 'the text names the username that has to be typed').toContain('sdsdfsdf and their tokens are deleted')
      expect(dialog().textContent, 'the display name is not the name of the user here').not.toContain('sdfsdfsdf and their tokens')
    })

    it('enables the delete button for the username when the display name differs', async () => {
      await mountMenu({ principal: SWAPPED })
      await chooseItem('Delete')

      await typeInDialog('sdfsdfsdf')
      expect(dialogButton('Delete').disabled, 'the display name does not unlock the deletion').toBe(true)
      await typeInDialog('sdsdfsdf')
      expect(dialogButton('Delete').disabled, 'the username does').toBe(false)
    })

    it('keeps the delete button disabled until the username is typed', async () => {
      await mountMenu()
      await chooseItem('Delete')

      expect(dialogButton('Delete').disabled).toBe(true)
      await typeInDialog('alic')
      expect(dialogButton('Delete').disabled, 'a partial username does not count').toBe(true)
      await typeInDialog('alice')
      expect(dialogButton('Delete').disabled).toBe(false)
    })

    it('deletes by name once the username is typed and reports it', async () => {
      await mountMenu()
      await chooseItem('Delete')
      await typeInDialog('alice')

      dialogButton('Delete').click()
      await flushPromises()

      expect(api.remove).toHaveBeenCalledWith('alice')
      expect(wrapper.emitted('deleted')).toHaveLength(1)
    })

    it('shows the server message for the last instance admin inside the dialog', async () => {
      api.remove.mockRejectedValue(rejection(409, 'The last instance admin cannot be deleted.'))
      await mountMenu()
      await chooseItem('Delete')
      await typeInDialog('alice')

      dialogButton('Delete').click()
      await flushPromises()

      expect(dialog().querySelector<HTMLElement>('[role="alert"]')!.textContent).toBe('The last instance admin cannot be deleted.')
      expect(wrapper.emitted('deleted')).toBeUndefined()
    })
  })

  describe('reset password', () => {
    it('opens the dialog for the user', async () => {
      await mountMenu()

      await chooseItem('Reset password')

      expect(dialog().textContent).toContain('Reset password')
      expect(dialog().textContent).toContain('alice')
    })
  })
})
