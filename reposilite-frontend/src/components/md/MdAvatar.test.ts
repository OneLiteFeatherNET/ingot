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

import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MdAvatar from './MdAvatar.vue'

const mountAvatar = (props: Record<string, unknown> = {}, initial = 'M') =>
  mount(MdAvatar, { props, slots: { default: () => initial } })

describe('MdAvatar drawing', () => {
  it('draws the initial in a circle on the primary container role', () => {
    const classes = mountAvatar().classes()

    expect(classes, 'the avatar is a circle').toContain('rounded-corner-full')
    expect(classes, 'the avatar uses the primary container role').toContain('bg-primary-container')
    expect(classes, 'the initial uses the on-primary-container role').toContain('text-on-primary-container')
  })

  it('shows the initial that the default slot gives', () => {
    expect(mountAvatar({}, 'K').text()).toBe('K')
  })

  it('uses the medium size when no size is given, which is 32px', () => {
    const classes = mountAvatar().classes()

    expect(classes, 'the medium avatar is 32px square').toContain('size-8')
    expect(mountAvatar().attributes('data-size')).toBe('md')
  })

  it('draws the small size at 24px', () => {
    const wrapper = mountAvatar({ size: 'sm' })

    expect(wrapper.classes(), 'the small avatar is 24px square').toContain('size-6')
    expect(wrapper.attributes('data-size')).toBe('sm')
  })

  it('keeps the initial centred in its circle', () => {
    const classes = mountAvatar().classes()

    expect(classes).toContain('inline-flex')
    expect(classes).toContain('items-center')
    expect(classes).toContain('justify-center')
  })
})

describe('MdAvatar accessibility', () => {
  it('is hidden from assistive technology when it has no label', () => {
    const wrapper = mountAvatar()

    expect(wrapper.attributes('aria-hidden'), 'an unlabelled avatar is decorative').toBe('true')
    expect(wrapper.attributes('role'), 'an unlabelled avatar has no image role').toBeUndefined()
  })

  it('has an accessible name equal to the label when it has one', () => {
    const wrapper = mountAvatar({ label: 'Jane Doe' })

    expect(wrapper.attributes('aria-hidden'), 'a named avatar is not hidden').toBeUndefined()
    expect(wrapper.attributes('role'), 'a named avatar is an image').toBe('img')
    expect(wrapper.attributes('aria-label'), 'the label is the accessible name').toBe('Jane Doe')
  })
})

describe('MdAvatar anchors', () => {
  it('carries the md-avatar anchor on the root element', () => {
    expect(mountAvatar().attributes('data-ingot-component')).toBe('md-avatar')
  })
})
