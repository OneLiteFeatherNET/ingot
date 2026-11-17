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
import MdSkeleton from './MdSkeleton.vue'

describe('MdSkeleton', () => {
  it('draws a bar by default', () => {
    const wrapper = mount(MdSkeleton)

    expect(wrapper.attributes('data-shape'), 'the default shape must be a bar').toBe('bar')
  })

  it('draws a circle with the full corner role', () => {
    const wrapper = mount(MdSkeleton, { props: { shape: 'circle' } })

    expect(wrapper.attributes('data-shape'), 'the circle shape must be marked').toBe('circle')
    expect(wrapper.classes(), 'a circle must use the full corner').toContain('rounded-corner-full')
  })

  it('takes its width from the md width preset', () => {
    const wrapper = mount(MdSkeleton, { props: { width: 'md' } })

    expect(wrapper.classes(), 'the md preset must set half the width').toContain('w-1/2')
  })

  it('animates only when motion is allowed, and stays static under reduced motion', () => {
    const wrapper = mount(MdSkeleton)

    expect(wrapper.classes(), 'the animation must sit behind motion-safe').toContain('motion-safe:animate-skeleton-fade-in')
    expect(wrapper.classes(), 'no unconditional animation may remain').not.toContain('animate-skeleton-fade-in')
  })

  it('is hidden from assistive technology because it carries no text', () => {
    const wrapper = mount(MdSkeleton)

    expect(wrapper.attributes('aria-hidden'), 'a placeholder must be decorative').toBe('true')
  })
})
