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

import { describe, it, expect, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { computeAccessibleName } from 'dom-accessibility-api'
import MdOverflowMenu from './MdOverflowMenu.vue'

// The menu is teleported to the body, so it is read from the document and not from the wrapper!.
const menu = () => document.querySelector<HTMLElement>('[role="menu"]')!
const menuItems = () => [...document.querySelectorAll<HTMLElement>('[role="menu"] [role="menuitem"]')]

const ITEMS = [
  { value: 'edit', label: 'Edit', ariaLabel: 'Edit releases' },
  { value: 'copy', label: 'Copy address', ariaLabel: 'Copy address of releases' },
  { value: 'delete', label: 'Delete', ariaLabel: 'Delete releases', error: true }
]

let wrapper: VueWrapper | undefined

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

const trigger = () => wrapper!.find('button')

describe('MdOverflowMenu', () => {
  it('names the trigger with its label and marks it as a closed menu button', async () => {
    await mountMenu()

    expect(trigger().attributes('aria-label'), 'the trigger carries the label').toBe('Actions for releases')
    expect(trigger().attributes('aria-haspopup'), 'the trigger opens a menu').toBe('menu')
    expect(trigger().attributes('aria-expanded'), 'the menu is closed at first').toBe('false')
  })

  it('keeps the trigger named by its content when a trigger slot is given', async () => {
    wrapper = mount(MdOverflowMenu, {
      props: { label: 'Account menu for Alice', items: ITEMS },
      slots: { trigger: '<span>Alice</span>' },
      attachTo: document.body
    })
    await flushPromises()

    expect(trigger().attributes('aria-label'), 'no label overrides the content').toBeUndefined()
  })

  it('names a trigger with a slot by the trigger label, so the name says what the button does', async () => {
    wrapper = mount(MdOverflowMenu, {
      props: { label: 'Account menu', triggerLabel: 'Account menu, Alice Example', items: ITEMS },
      slots: { trigger: '<span aria-hidden="true">A</span> <span>Alice Example</span>' },
      attachTo: document.body
    })
    await flushPromises()

    expect(trigger().attributes('aria-label'), 'the trigger label names the button').toBe('Account menu, Alice Example')
    expect(computeAccessibleName(trigger().element), 'a screen reader hears the menu and the name').toBe('Account menu, Alice Example')
  })

  it('gives the header and the items the same horizontal inset', async () => {
    wrapper = mount(MdOverflowMenu, {
      props: { label: 'Account menu for Alice', items: ITEMS },
      slots: { header: '<p>Alice Example</p>' },
      attachTo: document.body
    })
    await flushPromises()
    await trigger().trigger('click')
    await flushPromises()

    const header = document.querySelector<HTMLElement>('[role="menu"] > [role="none"]')!
    expect(header.classList, 'the header carries the shared inset').toContain('px-3')
    expect(menuItems()[0].classList, 'the items carry the same inset').toContain('px-3')
  })

  it('moves to the first item with Home and to the last with End', async () => {
    await mountMenu()
    await trigger().trigger('click')
    await flushPromises()

    menuItems()[2].dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }))
    expect(document.activeElement, 'Home moves to the first item').toBe(menuItems()[0])

    menuItems()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }))
    expect(document.activeElement, 'End moves to the last item').toBe(menuItems()[2])
  })

  it('tells its parent when the menu opens and when it closes', async () => {
    await mountMenu()

    await trigger().trigger('click')
    await flushPromises()
    expect(wrapper!.emitted('toggle')?.at(-1), 'the parent learns that the menu opened').toEqual([true])

    menuItems()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(wrapper!.emitted('toggle')?.at(-1), 'the parent learns that the menu closed').toEqual([false])
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

    expect(wrapper!.emitted('select'), 'one selection is reported').toEqual([['copy']])
    expect(menu(), 'the menu closes after a choice').toBeNull()
  })

  it('draws an error item in the error colour', async () => {
    await mountMenu()
    await trigger().trigger('click')
    await flushPromises()

    expect(menuItems()[2].classList.contains('text-error'), 'Delete is drawn as an error item').toBe(true)
    expect(menuItems()[2].classList.contains('!text-error'), 'the error tone is not an important override').toBe(false)
    expect(menuItems()[0].classList.contains('text-error'), 'Edit is not drawn as an error item').toBe(false)
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
      expect(wrapper!.emitted('select'), 'the chosen value is reported').toEqual([['edit']])
    })

    it('leaves a click with a modifier key to the browser, which opens a new tab', async () => {
      await mountMenu({ items: LINK_ITEMS })
      await trigger().trigger('click')
      await flushPromises()

      const event = clickEvent({ ctrlKey: true })
      menuItems()[0].dispatchEvent(event)
      await flushPromises()

      expect(event.defaultPrevented, 'the browser follows the link in a new tab').toBe(false)
      expect(wrapper!.emitted('select'), 'nothing is reported for the same page').toBeUndefined()
    })
  })

  describe('trigger and header slots', () => {
    it('draws the trigger slot in place of the Actions text', async () => {
      wrapper = mount(MdOverflowMenu, {
        props: { label: 'Account menu for Alice', items: ITEMS },
        slots: { trigger: '<span data-test="custom-trigger">Alice</span>' },
        attachTo: document.body
      })
      await flushPromises()

      expect(wrapper.find('[data-test="custom-trigger"]').exists(), 'the slot content is the trigger').toBe(true)
      expect(trigger().text(), 'the default text is not drawn').not.toBe('Actions')
    })

    it('leaves the name of a trigger with a slot to its content, so the content can carry the visible name', async () => {
      wrapper = mount(MdOverflowMenu, {
        props: { label: 'Account menu for Alice', items: ITEMS },
        slots: { trigger: '<span class="sr-only">Account menu for</span> <span aria-hidden="true">A</span> <span>Alice</span>' },
        attachTo: document.body
      })
      await flushPromises()

      expect(trigger().attributes('aria-label'), 'no label overrides the content (WCAG 2.5.3, Label in Name)').toBeUndefined()
      expect(computeAccessibleName(trigger().element), 'the content gives the name, and the visible name is part of it').toBe('Account menu for Alice')
    })

    it('keeps aria-haspopup and aria-expanded correct on a trigger with a slot', async () => {
      wrapper = mount(MdOverflowMenu, {
        props: { label: 'Account menu for Alice', items: ITEMS },
        slots: { trigger: '<span>Alice</span>' },
        attachTo: document.body
      })
      await flushPromises()

      expect(trigger().attributes('aria-haspopup'), 'the trigger opens a menu').toBe('menu')
      expect(trigger().attributes('aria-expanded'), 'the menu is closed at first').toBe('false')
      await trigger().trigger('click')
      await flushPromises()
      expect(trigger().attributes('aria-expanded'), 'the trigger reports the open menu').toBe('true')
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

      const heading = document.querySelector<HTMLElement>('[data-test="menu-heading"]')!
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

      const separator = document.querySelector<HTMLElement>('[role="menu"] [role="separator"]')!
      expect(separator, 'a separator is drawn between the groups').not.toBeNull()
      expect(separator.hasAttribute('tabindex'), 'the separator takes no focus').toBe(false)
    })

    it('moves through radio items with the arrow keys and reports the chosen radio by its value', async () => {
      await mountMenu({ items: CHOICE_ITEMS })
      await trigger().trigger('click')
      await flushPromises()

      const light = document.querySelector<HTMLElement>('[role="menuitemradio"]')!
      expect(document.activeElement, 'the first item has the focus').toBe(light)

      light.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
      await flushPromises()
      expect((document.activeElement as HTMLElement).textContent.trim(), 'the arrow key moves to the next radio').toBe('Dark')

      ;(document.activeElement as HTMLElement).click()
      await flushPromises()
      expect(wrapper!.emitted('select'), 'the value of the chosen radio is reported').toEqual([['theme:dark']])
    })
  })

  describe('scrolling', () => {
    // The button's rect is a fixed box, so a test can move it the way a scroll does.
    const moveButton = (top: number) => {
      const rect = { top, bottom: top + 40, left: 0, right: 100, width: 100, height: 40 }
      trigger().element.getBoundingClientRect = () => rect as DOMRect
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

describe('MdOverflowMenu catalogue features', () => {
  const clickEvent = () => new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 })

  it('marks a disabled item with aria-disabled and reports no choice when it is clicked', async () => {
    await mountMenu({
      items: [
        { value: 'edit', label: 'Edit', ariaLabel: 'Edit releases' },
        { value: 'archive', label: 'Archive', ariaLabel: 'Archive releases', disabled: true }
      ]
    })
    await trigger().trigger('click')
    await flushPromises()

    expect(menuItems()[1].getAttribute('aria-disabled'), 'the disabled item says so').toBe('true')
    menuItems()[1].dispatchEvent(clickEvent())
    await flushPromises()

    expect(wrapper!.emitted('select'), 'a disabled item reports no choice').toBeUndefined()
    expect(menu(), 'the menu stays open after a click on a disabled item').not.toBeNull()
  })

  it('skips a disabled item when the arrow keys move between the items', async () => {
    await mountMenu({
      items: [
        { value: 'edit', label: 'Edit', ariaLabel: 'Edit releases' },
        { value: 'archive', label: 'Archive', ariaLabel: 'Archive releases', disabled: true },
        { value: 'copy', label: 'Copy address', ariaLabel: 'Copy address of releases' }
      ]
    })
    await trigger().trigger('click')
    await flushPromises()

    menuItems()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
    await flushPromises()

    expect(document.activeElement, 'ArrowDown passes over the disabled item').toBe(menuItems()[2])
  })

  it('draws the supporting line of a two-line item under its label', async () => {
    await mountMenu({
      items: [{ value: 'copy', label: 'Copy address', supporting: 'Latest release only' }]
    })
    await trigger().trigger('click')
    await flushPromises()

    const item = menuItems()[0]
    expect(item.textContent, 'the label is drawn').toContain('Copy address')
    expect(item.textContent, 'the supporting line is drawn').toContain('Latest release only')
  })

  it('joins the supporting line to the accessible name of a two-line item with an aria label', async () => {
    await mountMenu({
      items: [{ value: 'copy', label: 'Copy address', ariaLabel: 'Copy releases', supporting: 'Latest release only' }]
    })
    await trigger().trigger('click')
    await flushPromises()

    expect(computeAccessibleName(menuItems()[0]), 'the supporting line is part of the name').toBe('Copy releases, Latest release only')
  })

  it('draws the checked radio item with the secondary container tone and no important override', async () => {
    await mountMenu({
      items: [{ value: 'theme:dark', label: 'Dark', type: 'radio', checked: true }]
    })
    await trigger().trigger('click')
    await flushPromises()

    const checked = document.querySelector<HTMLElement>('[role="menuitemradio"]')!
    expect(checked.classList.contains('bg-secondary-container'), 'the checked choice has the container tone').toBe(true)
    expect(checked.classList.contains('text-on-secondary-container'), 'the checked choice has the on-container text').toBe(true)
    expect(checked.className, 'no class is an important override').not.toContain('!')
  })

  it('keeps the focus ring on every item', async () => {
    await mountMenu()
    await trigger().trigger('click')
    await flushPromises()

    for (const item of menuItems()) {
      expect(item.classList.contains('md-focus-ring'), `${item.textContent?.trim()} keeps the focus ring`).toBe(true)
    }
  })

  it('gives every item a touch target of at least 48 pixels', async () => {
    await mountMenu()
    await trigger().trigger('click')
    await flushPromises()

    for (const item of menuItems()) {
      expect(item.classList.contains('md-touch-target'), `${item.textContent?.trim()} has the touch target`).toBe(true)
    }
  })

  it('stacks the menu on the menu layer token and caps its width with the menu token', async () => {
    await mountMenu()
    await trigger().trigger('click')
    await flushPromises()

    expect(menu().classList.contains('z-md-menu'), 'the menu uses the z token').toBe(true)
    expect(menu().classList.contains('z-50'), 'the literal z-50 is gone').toBe(false)
    expect(menu().classList.contains('max-w-md-menu'), 'the width cap is a token').toBe(true)
    expect(menu().className, 'no arbitrary max width is left').not.toContain('max-w-[')
  })

  it('draws the trigger as a text button unless another variant is asked for', async () => {
    await mountMenu()

    expect(trigger().attributes('data-variant'), 'the default trigger is a text button').toBe('text')
  })

  it('draws the trigger in the variant it is given, so a menu can open from an outlined button', async () => {
    await mountMenu({ triggerVariant: 'outlined' })

    expect(trigger().attributes('data-variant'), 'the trigger takes the given variant').toBe('outlined')
  })

  it('disables the trigger when the menu is disabled, so it cannot be opened', async () => {
    await mountMenu({ disabled: true })

    expect(trigger().attributes('disabled'), 'the trigger is a disabled native button').toBeDefined()
    await trigger().trigger('click')
    await flushPromises()
    expect(document.querySelector('[role="menu"]'), 'no menu opens from a disabled trigger').toBeNull()
  })

  it('still applies the deprecated triggerClass to the trigger button', async () => {
    await mountMenu({ triggerClass: 'w-10' })

    expect(trigger().classes(), 'the class string keeps working for one release').toContain('w-10')
  })
})

describe('MdOverflowMenu motion', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  // The button sits at a fixed box, and the menu has a fixed layout height, so a test decides where the menu opens.
  // The height is read from offsetHeight, which the scale of the enter transition does not change.
  const placeButton = (top: number, menuHeight: number) => {
    const rect = { top, bottom: top + 40, left: 0, right: 100, width: 100, height: 40 }
    trigger().element.getBoundingClientRect = () => rect as DOMRect
    vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockReturnValue(menuHeight)
  }

  it('opens and closes the menu through the menu pattern of the motion primitives', async () => {
    await mountMenu()
    await trigger().trigger('click')
    await flushPromises()

    const transition = wrapper!.findComponent({ name: 'MdTransition' }) as VueWrapper<{ $props: Record<string, unknown> }>
    expect(transition.exists(), 'the menu is wrapped by MdTransition').toBe(true)
    expect(transition.props('pattern'), 'the menu uses the menu pattern, not a transition name of its own').toBe('menu')
  })

  it('scales from the top edge when the menu opens below its button', async () => {
    await mountMenu()
    placeButton(100, 200)
    await trigger().trigger('click')
    await flushPromises()

    expect(menu().style.top, 'the menu opens below the button').toBe('144px')
    expect(menu().classList.contains('origin-top-right'), 'the menu grows from the corner next to the button').toBe(true)
    expect(menu().classList.contains('origin-bottom-right'), 'no origin of the other placement').toBe(false)
  })

  it('scales from the bottom edge when the menu opens above its button', async () => {
    await mountMenu()
    placeButton(window.innerHeight - 60, 200)
    await trigger().trigger('click')
    await flushPromises()

    expect(menu().style.bottom, 'the menu opens above the button when there is no room below').not.toBe('')
    expect(menu().classList.contains('origin-bottom-right'), 'the menu grows from the corner next to the button').toBe(true)
    expect(menu().classList.contains('origin-top-right'), 'no origin of the other placement').toBe(false)
  })
})
