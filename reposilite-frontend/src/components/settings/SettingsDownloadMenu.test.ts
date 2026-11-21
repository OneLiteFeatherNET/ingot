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
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import SettingsDownloadMenu from './SettingsDownloadMenu.vue'

type MenuProps = Partial<{ disabled: boolean, hasUnsaved: boolean, canIncludeUnsaved: boolean, secretsHidden: boolean }>

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

// A fresh menu per test, attached to the document, because the shared menu is teleported to the body.
const mountMenu = async (props: MenuProps = {}) => {
  wrapper = mount(SettingsDownloadMenu, {
    props: { disabled: false, hasUnsaved: false, canIncludeUnsaved: true, secretsHidden: false, ...props },
    attachTo: document.body
  })
  await flushPromises()
  return wrapper
}

const trigger = () => wrapper!.get('button[aria-haspopup="menu"]')
const openMenu = async () => {
  await trigger().trigger('click')
  await flushPromises()
}
// The menu is teleported to the body, so it is read from the document and not from the wrapper.
const menu = () => document.querySelector<HTMLElement>('[role="menu"]')
const itemNamed = (name: string) =>
  [...document.querySelectorAll<HTMLElement>('[role="menu"] [role="menuitem"]')].find(item => item.textContent?.includes(name))

describe('SettingsDownloadMenu', () => {
  it('opens from an outlined button named Download as JSON', async () => {
    await mountMenu()

    expect(trigger().text(), 'the trigger keeps its visible name').toContain('Download as JSON')
    expect(trigger().attributes('data-variant'), 'the trigger stays an outlined button').toBe('outlined')
  })

  it('keeps the menu closed until the trigger is pressed', async () => {
    await mountMenu()

    expect(menu(), 'no menu before the trigger is pressed').toBeNull()

    await openMenu()

    expect(menu(), 'the menu opens on the trigger').not.toBeNull()
    expect((wrapper!.element as HTMLElement).contains(menu()), 'the shared menu is teleported to the body, so no container clips it').toBe(false)
    expect(menu()?.getAttribute('aria-label'), 'the menu is named').toBe('Download options')
    expect(trigger().attributes('aria-expanded')).toBe('true')
  })

  it('stacks the menu on the menu layer and not on a literal z-index', async () => {
    await mountMenu()
    await openMenu()

    expect(menu()?.classList.contains('z-md-menu'), 'the menu uses the menu layer token').toBe(true)
    expect(menu()?.classList.contains('z-20'), 'the literal z-20 is gone').toBe(false)
  })

  it('offers the saved settings as a two-line item and emits saved when chosen', async () => {
    await mountMenu()
    await openMenu()

    const item = itemNamed('Download saved settings')
    expect(item?.textContent, 'the second line says what the saved state is').toContain('The last state the server confirmed.')

    item?.click()
    await flushPromises()

    expect(wrapper!.emitted('saved')).toEqual([[]])
    expect(menu(), 'the menu closes after a choice').toBeNull()
  })

  it('offers no unsaved option while nothing is unsaved', async () => {
    await mountMenu({ hasUnsaved: false })
    await openMenu()

    expect(itemNamed('Download including unsaved changes')).toBeUndefined()
  })

  it('offers the unsaved option, clearly labelled, while there are unsaved changes', async () => {
    await mountMenu({ hasUnsaved: true })
    await openMenu()

    const item = itemNamed('Download including unsaved changes')
    expect(item, 'the labelled unsaved option is offered').toBeDefined()
    expect(item?.getAttribute('aria-disabled'), 'the option can be chosen').toBeNull()
    expect(item?.textContent, 'the second line warns that it is not the server state').toContain('not a copy of the server state')

    item?.click()
    await flushPromises()

    expect(wrapper!.emitted('draft')).toEqual([[]])
  })

  it('disables the unsaved option while the draft has invalid entries', async () => {
    await mountMenu({ hasUnsaved: true, canIncludeUnsaved: false })
    await openMenu()

    const item = itemNamed('Download including unsaved changes')
    expect(item?.getAttribute('aria-disabled'), 'the option is marked disabled').toBe('true')

    item?.click()
    await flushPromises()

    expect(wrapper!.emitted('draft'), 'a disabled option reports no choice').toBeUndefined()
  })

  it('says secrets are not included when the server hides them', async () => {
    await mountMenu({ secretsHidden: true })
    await openMenu()

    expect(menu()?.textContent).toContain('Secrets are not included')
  })

  it('stays silent about secrets when the server shows them', async () => {
    await mountMenu({ secretsHidden: false })
    await openMenu()

    expect(menu()?.textContent).not.toContain('Secrets are not included')
  })

  it('closes on Escape and returns the focus to the trigger', async () => {
    await mountMenu()
    await openMenu()

    menu()?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(menu(), 'Escape closes the menu').toBeNull()
    expect(document.activeElement, 'the focus returns to the trigger').toBe(trigger().element)
  })

  it('cannot be opened while no settings are loaded', async () => {
    await mountMenu({ disabled: true })

    expect(trigger().attributes('disabled')).toBeDefined()
  })
})
