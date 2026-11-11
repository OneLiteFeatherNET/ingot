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

import { describe, it, expect, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import MdOverflowMenu from './MdOverflowMenu.vue'

// The menu is teleported to the body, so it is read from the document and not from the wrapper.
const menu = () => document.querySelector('[role="menu"]')
const menuItems = () => [...document.querySelectorAll('[role="menu"] [role="menuitem"]')]

const ITEMS = [
  { value: 'edit', label: 'Edit', ariaLabel: 'Edit releases' },
  { value: 'copy', label: 'Copy address', ariaLabel: 'Copy address of releases' },
  { value: 'delete', label: 'Delete', ariaLabel: 'Delete releases', error: true }
]

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

// A fresh menu per test, attached to the document so that focus can move into it.
const mountMenu = async (props = {}) => {
  wrapper = mount(MdOverflowMenu, {
    props: { label: 'Actions for releases', items: ITEMS, ...props },
    attachTo: document.body
  })
  await flushPromises()
  return wrapper
}

const trigger = () => wrapper.find('button')

describe('MdOverflowMenu', () => {
  it('names the trigger with its label and marks it as a closed menu button', async () => {
    await mountMenu()

    expect(trigger().attributes('aria-label'), 'the trigger carries the label').toBe('Actions for releases')
    expect(trigger().attributes('aria-haspopup'), 'the trigger opens a menu').toBe('menu')
    expect(trigger().attributes('aria-expanded'), 'the menu is closed at first').toBe('false')
  })

  it('draws no menu until the trigger is activated', async () => {
    await mountMenu()

    expect(menu(), 'no menu is drawn before the trigger is used').toBeNull()
  })

  it('is a native button, so Enter and Space open it as a click', async () => {
    await mountMenu()

    expect(trigger().element.tagName, 'the trigger is a button element').toBe('BUTTON')
    expect(trigger().attributes('type'), 'the trigger does not submit a form').toBe('button')
  })

  it('opens a menu with one item per entry, named by its accessible name', async () => {
    await mountMenu()

    await trigger().trigger('click')
    await flushPromises()

    expect(menuItems().map(item => item.getAttribute('aria-label')), 'the items keep their accessible names')
      .toEqual(['Edit releases', 'Copy address of releases', 'Delete releases'])
    expect(trigger().attributes('aria-expanded'), 'the trigger reports the open menu').toBe('true')
  })

  it('moves the focus to the first item when the menu opens', async () => {
    await mountMenu()

    await trigger().trigger('click')
    await flushPromises()

    expect(document.activeElement, 'the first item has the focus').toBe(menuItems()[0])
  })

  it('moves between the items with the arrow keys and wraps at the ends', async () => {
    await mountMenu()
    await trigger().trigger('click')
    await flushPromises()

    menuItems()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
    await flushPromises()
    expect(document.activeElement, 'ArrowDown moves to the second item').toBe(menuItems()[1])

    menuItems()[0].focus()
    menuItems()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }))
    await flushPromises()
    expect(document.activeElement, 'ArrowUp from the first item wraps to the last').toBe(menuItems()[2])
  })

  it('closes on Escape and returns the focus to the trigger', async () => {
    await mountMenu()
    await trigger().trigger('click')
    await flushPromises()

    menuItems()[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(menu(), 'Escape closes the menu').toBeNull()
    expect(document.activeElement, 'the focus goes back to the trigger').toBe(trigger().element)
  })

  it('reports the chosen item by its value and closes the menu', async () => {
    await mountMenu()
    await trigger().trigger('click')
    await flushPromises()

    menuItems()[1].click()
    await flushPromises()

    expect(wrapper.emitted('select'), 'one selection is reported').toEqual([['copy']])
    expect(menu(), 'the menu closes after a choice').toBeNull()
  })

  it('draws an error item in the error colour', async () => {
    await mountMenu()
    await trigger().trigger('click')
    await flushPromises()

    expect(menuItems()[2].className, 'Delete is drawn as an error item').toContain('!text-error')
    expect(menuItems()[0].className, 'Edit is not drawn as an error item').not.toContain('!text-error')
  })

  describe('link items', () => {
    const LINK_ITEMS = [
      { value: 'edit', label: 'Edit', ariaLabel: 'Edit releases', href: '/settings/repositories/releases' },
      { value: 'delete', label: 'Delete', ariaLabel: 'Delete releases', error: true }
    ]

    const clickEvent = (init = {}) => new MouseEvent('click', { bubbles: true, cancelable: true, button: 0, ...init })

    it('renders an item with an href as a link that keeps the menuitem role', async () => {
      await mountMenu({ items: LINK_ITEMS })
      await trigger().trigger('click')
      await flushPromises()

      const edit = menuItems()[0]
      expect(edit.tagName, 'the item is an anchor, so middle click and open in new tab work').toBe('A')
      expect(edit.getAttribute('role'), 'the link is still a menu item').toBe('menuitem')
      expect(edit.getAttribute('href'), 'the link goes to the repository detail').toBe('/settings/repositories/releases')
      expect(menuItems()[1].tagName, 'an item without an href stays a button').toBe('BUTTON')
    })

    it('reports a plain click on a link item and keeps the browser from following it', async () => {
      await mountMenu({ items: LINK_ITEMS })
      await trigger().trigger('click')
      await flushPromises()

      const event = clickEvent()
      menuItems()[0].dispatchEvent(event)
      await flushPromises()

      expect(event.defaultPrevented, 'the router handles the navigation').toBe(true)
      expect(wrapper.emitted('select'), 'the chosen value is reported').toEqual([['edit']])
    })

    it('leaves a click with a modifier key to the browser, which opens a new tab', async () => {
      await mountMenu({ items: LINK_ITEMS })
      await trigger().trigger('click')
      await flushPromises()

      const event = clickEvent({ ctrlKey: true })
      menuItems()[0].dispatchEvent(event)
      await flushPromises()

      expect(event.defaultPrevented, 'the browser follows the link in a new tab').toBe(false)
      expect(wrapper.emitted('select'), 'nothing is reported for the same page').toBeUndefined()
    })
  })

  describe('trigger and header slots', () => {
    it('draws the trigger slot in place of the Actions text, and leaves the name to the visible content', async () => {
      wrapper = mount(MdOverflowMenu, {
        props: { label: 'Account menu', items: ITEMS },
        slots: { trigger: '<span data-test="custom-trigger">Alice</span>' },
        attachTo: document.body
      })
      await flushPromises()

      expect(wrapper.find('[data-test="custom-trigger"]').exists(), 'the slot content is the trigger').toBe(true)
      expect(trigger().text(), 'the default text is not drawn').not.toBe('Actions')
      expect(trigger().attributes('aria-label'), 'a label that differs from the visible name would mismatch it').toBeUndefined()
    })

    it('names the menu by the label when the trigger carries its own content', async () => {
      wrapper = mount(MdOverflowMenu, {
        props: { label: 'Account menu', items: ITEMS },
        slots: { trigger: '<span>Alice</span>' },
        attachTo: document.body
      })
      await flushPromises()
      await trigger().trigger('click')
      await flushPromises()

      expect(menu().getAttribute('aria-label')).toBe('Account menu')
    })

    it('draws the header slot above the items without making it a menu item', async () => {
      wrapper = mount(MdOverflowMenu, {
        props: { label: 'Account menu', items: ITEMS },
        slots: { header: '<p data-test="menu-heading">alice</p>' },
        attachTo: document.body
      })
      await flushPromises()
      await trigger().trigger('click')
      await flushPromises()

      const heading = document.querySelector('[data-test="menu-heading"]')
      expect(heading, 'the header is drawn while the menu is open').not.toBeNull()
      expect(heading.closest('[role="menuitem"]'), 'the heading is not a menu item').toBeNull()
      expect(menuItems()).toHaveLength(ITEMS.length)
    })
  })

  describe('radio and separator items', () => {
    const CHOICE_ITEMS = [
      { value: 'theme:light', label: 'Light', type: 'radio', checked: false },
      { value: 'theme:dark', label: 'Dark', type: 'radio', checked: true },
      { type: 'separator' },
      { value: 'logout', label: 'Logout' }
    ]

    it('draws radio items with the menuitemradio role and their checked state', async () => {
      await mountMenu({ items: CHOICE_ITEMS })
      await trigger().trigger('click')
      await flushPromises()

      const radios = document.querySelectorAll('[role="menu"] [role="menuitemradio"]')
      expect(radios).toHaveLength(2)
      expect(radios[0].getAttribute('aria-checked'), 'the unchecked choice').toBe('false')
      expect(radios[1].getAttribute('aria-checked'), 'the checked choice').toBe('true')
    })

    it('draws the separator as a separator that is not focusable', async () => {
      await mountMenu({ items: CHOICE_ITEMS })
      await trigger().trigger('click')
      await flushPromises()

      const separator = document.querySelector('[role="menu"] [role="separator"]')
      expect(separator, 'a separator is drawn between the groups').not.toBeNull()
      expect(separator.hasAttribute('tabindex'), 'the separator takes no focus').toBe(false)
    })

    it('moves through radio items with the arrow keys and reports the chosen radio by its value', async () => {
      await mountMenu({ items: CHOICE_ITEMS })
      await trigger().trigger('click')
      await flushPromises()

      const light = document.querySelector('[role="menuitemradio"]')
      expect(document.activeElement, 'the first item has the focus').toBe(light)

      light.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
      await flushPromises()
      expect(document.activeElement.textContent.trim(), 'the arrow key moves to the next radio').toBe('Dark')

      document.activeElement.click()
      await flushPromises()
      expect(wrapper.emitted('select'), 'the value of the chosen radio is reported').toEqual([['theme:dark']])
    })
  })

  describe('scrolling', () => {
    // The button's rect is a fixed box, so a test can move it the way a scroll does.
    const moveButton = (top) => {
      const rect = { top, bottom: top + 40, left: 0, right: 100, width: 100, height: 40 }
      trigger().element.getBoundingClientRect = () => rect
    }

    it('stays open when a scroll event arrives just after it opened', async () => {
      await mountMenu()
      moveButton(200)
      await trigger().trigger('click')
      await flushPromises()

      window.dispatchEvent(new Event('scroll'))
      await flushPromises()

      expect(menu(), 'a scroll that began before the menu opened does not close it').not.toBeNull()
    })

    it('follows its button when the page scrolls', async () => {
      await mountMenu()
      moveButton(200)
      await trigger().trigger('click')
      await flushPromises()
      expect(menu().style.top, 'the menu opens below the button').toBe('244px')

      moveButton(300)
      window.dispatchEvent(new Event('scroll'))
      await flushPromises()

      expect(menu().style.top, 'the menu moved with the button').toBe('344px')
    })

    it('closes when its button has scrolled out of the screen', async () => {
      await mountMenu()
      moveButton(200)
      await trigger().trigger('click')
      await flushPromises()

      moveButton(-80)
      window.dispatchEvent(new Event('scroll'))
      await flushPromises()

      expect(menu(), 'a menu without its button on screen closes').toBeNull()
    })
  })
})
