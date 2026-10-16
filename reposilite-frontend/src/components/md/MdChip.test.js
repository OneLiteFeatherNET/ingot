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

import { describe, it, expect, vi } from 'vitest'
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import MdChip from './MdChip.vue'

describe('MdChip', () => {
  it('assist chip renders a button by default', () => {
    const wrapper = mount(MdChip, { slots: { default: 'Copy coordinates' } })

    expect(wrapper.element.tagName).toBe('BUTTON')
    expect(wrapper.attributes('type')).toBe('button')
  })

  it('assist chip renders an anchor when href is given', () => {
    const wrapper = mount(MdChip, {
      props: { href: '/artifacts' },
      slots: { default: 'Artifacts' }
    })

    expect(wrapper.element.tagName).toBe('A')
    expect(wrapper.attributes('href')).toBe('/artifacts')
  })

  it('filter chip has the checkbox role', () => {
    const wrapper = mount(MdChip, { props: { variant: 'filter' }, slots: { default: 'Snapshots' } })

    expect(wrapper.attributes('role')).toBe('checkbox')
  })

  it('filter chip reports the selected state through aria-checked', () => {
    const wrapper = mount(MdChip, {
      props: { variant: 'filter', selected: true },
      slots: { default: 'Snapshots' }
    })

    expect(wrapper.attributes('aria-checked')).toBe('true')
  })

  it('filter chip shows a check mark only while selected', () => {
    const unselected = mount(MdChip, { props: { variant: 'filter' }, slots: { default: 'Snapshots' } })
    const selected = mount(MdChip, {
      props: { variant: 'filter', selected: true },
      slots: { default: 'Snapshots' }
    })

    expect(unselected.find('svg').exists()).toBe(false)
    expect(selected.find('svg').exists()).toBe(true)
  })

  it('selected filter chip uses the secondary container roles', () => {
    const wrapper = mount(MdChip, {
      props: { variant: 'filter', selected: true },
      slots: { default: 'Snapshots' }
    })

    expect(wrapper.classes()).toContain('bg-secondary-container')
  })

  it('is a 32px tall container, not a 48px one', () => {
    const wrapper = mount(MdChip, { slots: { default: 'Copy' } })

    expect(wrapper.classes()).toContain('h-8')
    expect(wrapper.classes()).not.toContain('min-h-12')
  })

  it('marks the root with the touch target class that widens the hit area to 48px', () => {
    const wrapper = mount(MdChip, { slots: { default: 'Copy' } })

    expect(wrapper.classes()).toContain('md-touch-target')
  })

  it('disabled button chip is disabled', () => {
    const wrapper = mount(MdChip, { props: { disabled: true }, slots: { default: 'Copy' } })

    expect(wrapper.attributes('disabled')).toBeDefined()
  })

  it('forwards click listeners to the rendered element', async () => {
    const onClick = vi.fn()
    const wrapper = mount(MdChip, {
      props: { variant: 'filter' },
      attrs: { onClick },
      slots: { default: 'Snapshots' }
    })

    await wrapper.trigger('click')

    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('renders the icon slot inside a wrapper hidden from assistive technology', () => {
    const wrapper = mount(MdChip, {
      slots: { icon: () => h('svg', { 'data-test': 'icon' }), default: 'Copy' }
    })

    const iconWrapper = wrapper.find('[data-test="icon"]').element.parentElement
    expect(iconWrapper.getAttribute('aria-hidden')).toBe('true')
  })
})
