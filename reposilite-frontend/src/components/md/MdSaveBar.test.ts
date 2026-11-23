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
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import MdSaveBar from './MdSaveBar.vue'

describe('MdSaveBar', () => {
  it('sticks to the bottom of its scroll container', () => {
    const wrapper = mount(MdSaveBar)

    expect(wrapper.classes(), 'the bar must be sticky').toContain('sticky')
    expect(wrapper.classes(), 'the bar must sit at the bottom edge').toContain('bottom-0')
  })

  it('keeps the bottom clearance of the bottom bar and the safe area as a class', () => {
    const wrapper = mount(MdSaveBar)

    expect(
      wrapper.classes(),
      'below the medium window class the bar must clear the bottom bar and the safe area'
    ).toContain('max-medium:bottom-[calc(var(--app-bottom-bar-height,0px)+env(safe-area-inset-bottom,0px))]')
  })

  it('carries no inline style for the clearance', () => {
    const wrapper = mount(MdSaveBar)

    expect(wrapper.attributes('style'), 'the clearance must be a class, not a style offset').toBeUndefined()
  })

  it('renders the default slot as the content of the bar', () => {
    const wrapper = mount(MdSaveBar, {
      slots: { default: () => h('button', { 'data-test': 'save' }, 'Save') }
    })

    expect(wrapper.find('[data-test="save"]').exists(), 'the slot content must sit inside the bar').toBe(true)
  })

  it('carries the md-save-bar anchor on its root', () => {
    const wrapper = mount(MdSaveBar)

    expect(wrapper.attributes('data-ingot-component')).toBe('md-save-bar')
  })

  it('passes a caller class through to the bar', () => {
    const wrapper = mount(MdSaveBar, { attrs: { class: 'mt-6' } })

    expect(wrapper.classes(), 'a caller class must land on the root').toContain('mt-6')
  })
})
