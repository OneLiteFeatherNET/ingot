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

import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SettingsDownloadMenu from './SettingsDownloadMenu.vue'

const mountMenu = (props = {}) => mount(SettingsDownloadMenu, {
  props: { disabled: false, hasUnsaved: false, canIncludeUnsaved: true, secretsHidden: false, ...props }
})

const trigger = (wrapper) => wrapper.get('button[aria-haspopup="menu"]')
const itemNamed = (wrapper, name) => wrapper.findAll('[role="menuitem"]').find(item => item.text().includes(name))

describe('SettingsDownloadMenu', () => {
  it('keeps the menu closed until the trigger is pressed', async () => {
    const wrapper = mountMenu()

    expect(wrapper.find('[role="menu"]').exists()).toBe(false)

    await trigger(wrapper).trigger('click')

    expect(wrapper.find('[role="menu"]').exists()).toBe(true)
    expect(trigger(wrapper).attributes('aria-expanded')).toBe('true')
  })

  it('offers the saved settings and emits saved when chosen', async () => {
    const wrapper = mountMenu()
    await trigger(wrapper).trigger('click')

    await itemNamed(wrapper, 'Download saved settings').trigger('click')

    expect(wrapper.emitted('saved')).toEqual([[]])
    expect(wrapper.find('[role="menu"]').exists(), 'the menu closes after a choice').toBe(false)
  })

  it('offers no unsaved option while nothing is unsaved', async () => {
    const wrapper = mountMenu({ hasUnsaved: false })
    await trigger(wrapper).trigger('click')

    expect(itemNamed(wrapper, 'Download including unsaved changes')).toBeUndefined()
  })

  it('offers the unsaved option, clearly labelled, while there are unsaved changes', async () => {
    const wrapper = mountMenu({ hasUnsaved: true })
    await trigger(wrapper).trigger('click')

    const item = itemNamed(wrapper, 'Download including unsaved changes')
    expect(item, 'the labelled unsaved option is offered').toBeDefined()
    expect(item.attributes('disabled')).toBeUndefined()

    await item.trigger('click')
    expect(wrapper.emitted('draft')).toEqual([[]])
  })

  it('disables the unsaved option while the draft has invalid entries', async () => {
    const wrapper = mountMenu({ hasUnsaved: true, canIncludeUnsaved: false })
    await trigger(wrapper).trigger('click')

    expect(itemNamed(wrapper, 'Download including unsaved changes').attributes('disabled')).toBeDefined()
  })

  it('says secrets are not included when the server hides them', async () => {
    const wrapper = mountMenu({ secretsHidden: true })
    await trigger(wrapper).trigger('click')

    expect(wrapper.text()).toContain('Secrets are not included')
  })

  it('stays silent about secrets when the server shows them', async () => {
    const wrapper = mountMenu({ secretsHidden: false })
    await trigger(wrapper).trigger('click')

    expect(wrapper.text()).not.toContain('Secrets are not included')
  })

  it('closes on Escape', async () => {
    const wrapper = mountMenu()
    await trigger(wrapper).trigger('click')

    await wrapper.find('[role="menu"]').trigger('keydown', { key: 'Escape' })

    expect(wrapper.find('[role="menu"]').exists()).toBe(false)
  })

  it('cannot be opened while no settings are loaded', () => {
    expect(trigger(mountMenu({ disabled: true })).attributes('disabled')).toBeDefined()
  })
})
