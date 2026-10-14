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
import HeaderHero from './HeaderHero.vue'

describe('HeaderHero', () => {
  it('draws exactly one OneLiteFeather brand bar in the hero', () => {
    const wrapper = mount(HeaderHero)

    expect(wrapper.findAll('.olf-brand-bar'), 'the hero should carry a single brand bar').toHaveLength(1)
  })

  it('places the brand bar on the top edge of the hero, before the content', () => {
    const wrapper = mount(HeaderHero)

    expect(wrapper.element.firstElementChild?.classList.contains('olf-brand-bar'), 'the brand bar should be the first child of the hero').toBe(true)
  })

  it('hides the decorative brand bar from assistive technology', () => {
    const bar = mount(HeaderHero).find('.olf-brand-bar')

    expect(bar.attributes('aria-hidden'), 'the brand bar carries no information, so it is hidden').toBe('true')
  })
})
