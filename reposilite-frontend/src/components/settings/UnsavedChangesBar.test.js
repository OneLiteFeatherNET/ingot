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
import UnsavedChangesBar from './UnsavedChangesBar.vue'

const mountBar = (props = {}) => mount(UnsavedChangesBar, {
  props: { count: 1, invalid: false, saving: false, ...props }
})

const buttonNamed = (wrapper, name) => wrapper.findAll('button').find(button => button.text() === name)

describe('UnsavedChangesBar', () => {
  it('counts one pending change in the singular', () => {
    expect(mountBar({ count: 1 }).text()).toContain('1 unsaved change')
    expect(mountBar({ count: 1 }).text()).not.toContain('1 unsaved changes')
  })

  it('counts several pending changes in the plural', () => {
    expect(mountBar({ count: 3 }).text()).toContain('3 unsaved changes')
  })

  it('announces the count as a status for assistive technology', () => {
    const bar = mountBar({ count: 2 }).find('[role="status"]')

    expect(bar.exists(), 'the count is announced politely').toBe(true)
    expect(bar.text()).toContain('2 unsaved changes')
  })

  it('emits save when the save action is pressed', async () => {
    const wrapper = mountBar()

    await buttonNamed(wrapper, 'Save').trigger('click')

    expect(wrapper.emitted('save')).toEqual([[]])
  })

  it('emits discard when the discard action is pressed', async () => {
    const wrapper = mountBar()

    await buttonNamed(wrapper, 'Discard').trigger('click')

    expect(wrapper.emitted('discard')).toEqual([[]])
  })

  it('does not allow saving while a domain has an invalid entry, and says why', () => {
    const wrapper = mountBar({ invalid: true })

    expect(buttonNamed(wrapper, 'Save').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Fix the errors before saving.')
  })

  it('still allows discarding while the domain is invalid', () => {
    const wrapper = mountBar({ invalid: true })

    expect(buttonNamed(wrapper, 'Discard').attributes('disabled')).toBeUndefined()
  })

  it('does not allow a second save while one is running', () => {
    const wrapper = mountBar({ saving: true })

    expect(buttonNamed(wrapper, 'Save').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Saving')
  })
})
