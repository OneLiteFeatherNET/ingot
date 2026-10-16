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
import StatusLabel from './StatusLabel.vue'

const COLOUR = 'var(--md-ext-color-success)'

const mountLabel = () => mount(StatusLabel, {
  props: { color: COLOUR },
  slots: { default: 'Healthy' }
})

describe('StatusLabel', () => {
  it('lays the dot and its text on one centre line with the shared gap', () => {
    const root = mountLabel().element

    expect(root.tagName).toBe('SPAN')
    expect(root.className.split(' ')).toEqual(expect.arrayContaining(['inline-flex', 'items-center', 'gap-1.5']))
  })

  it('draws the dot as an 8 by 8 pixel circle that does not shrink', () => {
    const dot = mountLabel().element.firstElementChild
    const classes = dot.className.split(' ')

    expect(classes, 'the dot is size-2, which is 8px').toEqual(expect.arrayContaining(['size-2', 'shrink-0', 'rounded-full']))
  })

  it('places the dot before the text and hides it from assistive technology', () => {
    const wrapper = mountLabel()
    const [dot, text] = wrapper.element.children

    expect(dot.getAttribute('aria-hidden')).toBe('true')
    expect(text.textContent.trim(), 'the text is what a reader hears').toBe('Healthy')
  })

  it('paints the dot with the colour it is given and no other', () => {
    const dot = mountLabel().element.firstElementChild

    expect(dot.style.backgroundColor).toBe('var(--md-ext-color-success)')
  })
})
