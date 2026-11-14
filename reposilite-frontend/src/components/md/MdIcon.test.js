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
import MdIcon from './MdIcon.vue'

// The close glyph of the side sheet, as components/icons/CloseIcon.vue draws it.
const SHEET_CLOSE_PATH = 'M6 18L18 6M6 6l12 12'

describe('MdIcon', () => {
  it('is decorative by default, so a screen reader skips it', () => {
    const wrapper = mount(MdIcon, { props: { path: SHEET_CLOSE_PATH } })

    expect(wrapper.attributes('aria-hidden'), 'the icon is hidden from assistive technology').toBe('true')
    expect(wrapper.attributes('role'), 'a decorative icon has no role').toBeUndefined()
  })

  it('takes a label and then names itself as an image', () => {
    const wrapper = mount(MdIcon, { props: { path: SHEET_CLOSE_PATH, label: 'Close' } })

    expect(wrapper.attributes('aria-hidden'), 'a labelled icon is not hidden').toBeUndefined()
    expect(wrapper.attributes('role'), 'a labelled icon is an image').toBe('img')
    expect(wrapper.attributes('aria-label'), 'the label is the accessible name').toBe('Close')
  })

  it('is 24px by default, the size of the existing icon slots', () => {
    const wrapper = mount(MdIcon, { props: { path: SHEET_CLOSE_PATH } })

    expect(wrapper.classes(), 'the default size is the 24px class').toContain('size-6')
  })

  it('draws a named glyph from the registry', () => {
    const wrapper = mount(MdIcon, { props: { name: 'sheet-close' } })

    expect(wrapper.find('path').attributes('d'), 'the registry holds the close path').toBe(SHEET_CLOSE_PATH)
  })
})
