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
import CleaningIcon from './CleaningIcon.vue'

// The glyphs are the Material Symbols "cleaning_services" from @material-symbols/svg-400, outlined
// and filled. Both share the silhouette; the outlined one also carries the handle as a cut-out
// sub-path, which the filled one does not. The assertions pin that difference.
const HANDLE_SUBPATH = 'Zm350-420v-340H430v340h100Z'

const pathOf = (wrapper) => wrapper.find('path').attributes('d')

describe('CleaningIcon', () => {
  it('draws the outlined Material Symbols glyph when inactive', () => {
    const wrapper = mount(CleaningIcon)

    expect(pathOf(wrapper), 'the outlined glyph carries the handle as its own sub-path').toContain(HANDLE_SUBPATH)
  })

  it('draws the filled Material Symbols glyph when active', () => {
    const wrapper = mount(CleaningIcon, { props: { filled: true } })

    expect(pathOf(wrapper), 'the filled glyph is the solid silhouette, without the handle cut-out').not.toContain(HANDLE_SUBPATH)
    expect(pathOf(wrapper)).toMatch(/^M120-40v-290q0-78\.85/)
  })

  it('uses a different path for the active state than for the inactive one', () => {
    const inactive = pathOf(mount(CleaningIcon))
    const active = pathOf(mount(CleaningIcon, { props: { filled: true } }))

    expect(active, 'the filled and outlined glyphs are separate drawings').not.toBe(inactive)
  })

  it('keeps the Material Symbols 960 unit grid and stays hidden from assistive technology', () => {
    const svg = mount(CleaningIcon).find('svg')

    expect(svg.attributes('viewBox')).toBe('0 -960 960 960')
    expect(svg.attributes('aria-hidden')).toBe('true')
  })
})
