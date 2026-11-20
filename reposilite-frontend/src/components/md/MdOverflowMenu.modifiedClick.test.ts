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
import MdOverflowMenu from './MdOverflowMenu.vue'

/*
 * The overflow menu and the navigation item decide what a modified click is through one helper, so a link item of
 * the menu and a destination of the navigation leave the same clicks to the browser. The helper is replaced here,
 * and the menu has to follow its answer rather than a check of its own.
 */
const { isModifiedClick } = vi.hoisted(() => ({ isModifiedClick: vi.fn() }))
vi.mock('../../helpers/modifiedClick', () => ({ isModifiedClick }))

const ITEMS = [{ value: 'edit', label: 'Edit', ariaLabel: 'Edit releases', href: '/settings/repositories/releases' }]

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  isModifiedClick.mockReset()
})

const openMenu = async () => {
  wrapper = mount(MdOverflowMenu, {
    props: { label: 'Actions for releases', items: ITEMS },
    attachTo: document.body
  })
  await flushPromises()
  await wrapper.find('button').trigger('click')
  await flushPromises()
}

const clickLinkItem = async () => {
  const event = new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 })
  document.querySelector<HTMLElement>('[role="menu"] a[role="menuitem"]')!.dispatchEvent(event)
  await flushPromises()
  return event
}

describe('MdOverflowMenu modified clicks', () => {
  it('leaves a link click to the browser when the shared helper calls it modified', async () => {
    isModifiedClick.mockReturnValue(true)
    await openMenu()

    const event = await clickLinkItem()

    expect(isModifiedClick, 'the menu asks the shared helper').toHaveBeenCalledWith(event)
    expect(event.defaultPrevented, 'the browser follows the link itself').toBe(false)
    expect(wrapper!.emitted('select'), 'nothing is reported for the same page').toBeUndefined()
  })

  it('reports a link click and keeps the browser from following it when the shared helper calls it plain', async () => {
    isModifiedClick.mockReturnValue(false)
    await openMenu()

    const event = await clickLinkItem()

    expect(isModifiedClick, 'the menu asks the shared helper').toHaveBeenCalledWith(event)
    expect(event.defaultPrevented, 'the router handles the navigation').toBe(true)
    expect(wrapper!.emitted('select'), 'the chosen value is reported').toEqual([['edit']])
  })
})
