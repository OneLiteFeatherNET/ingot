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
import MdButton from './MdButton.vue'

describe('MdButton', () => {
  it('renders a button element with type button by default', () => {
    const wrapper = mount(MdButton, { slots: { default: 'Save' } })

    expect(wrapper.element.tagName).toBe('BUTTON')
    expect(wrapper.attributes('type')).toBe('button')
  })

  it('renders an anchor when href is given', () => {
    const wrapper = mount(MdButton, { props: { href: '/tokens' }, slots: { default: 'Tokens' } })

    expect(wrapper.element.tagName).toBe('A')
    expect(wrapper.attributes('href')).toBe('/tokens')
  })

  it('disables a button element through the disabled attribute', () => {
    const wrapper = mount(MdButton, { props: { disabled: true }, slots: { default: 'Save' } })

    expect(wrapper.attributes('disabled')).toBeDefined()
  })

  it('marks a disabled anchor with aria-disabled and removes its href', () => {
    const wrapper = mount(MdButton, {
      props: { href: '/tokens', disabled: true },
      slots: { default: 'Tokens' }
    })

    expect(wrapper.attributes('aria-disabled')).toBe('true')
    expect(wrapper.attributes('href')).toBeUndefined()
  })

  it('uses the primary colour roles for the filled variant', () => {
    const wrapper = mount(MdButton, { props: { variant: 'filled' }, slots: { default: 'Save' } })

    expect(wrapper.classes()).toContain('bg-primary')
    expect(wrapper.classes()).toContain('text-on-primary')
  })

  it('uses the secondary container roles for the tonal variant', () => {
    const wrapper = mount(MdButton, { props: { variant: 'tonal' }, slots: { default: 'Save' } })

    expect(wrapper.classes()).toContain('bg-secondary-container')
    expect(wrapper.classes()).toContain('text-on-secondary-container')
  })

  it('uses the error roles for the error variant', () => {
    const wrapper = mount(MdButton, { props: { variant: 'error' }, slots: { default: 'Revoke' } })

    expect(wrapper.classes()).toContain('bg-error')
    expect(wrapper.classes()).toContain('text-on-error')
  })

  it('uses the error text role for a text button with the error prop', () => {
    const wrapper = mount(MdButton, { props: { variant: 'text', error: true }, slots: { default: 'Revoke' } })

    expect(wrapper.classes()).toContain('text-error')
    expect(wrapper.classes()).not.toContain('text-primary')
  })

  it('uses the error outline and text role for an outlined button with the error prop', () => {
    const wrapper = mount(MdButton, { props: { variant: 'outlined', error: true }, slots: { default: 'Delete' } })

    expect(wrapper.classes()).toContain('border-error')
    expect(wrapper.classes()).toContain('text-error')
    expect(wrapper.classes()).not.toContain('border-outline')
  })

  it('uses the error roles for a filled button with the error prop', () => {
    const wrapper = mount(MdButton, { props: { error: true }, slots: { default: 'Delete' } })

    expect(wrapper.classes()).toContain('bg-error')
    expect(wrapper.classes()).toContain('text-on-error')
  })

  it('keeps the error variant as an alias for the filled error button', () => {
    const wrapper = mount(MdButton, { props: { variant: 'error' }, slots: { default: 'Revoke' } })

    expect(wrapper.classes()).toContain('bg-error')
    expect(wrapper.classes()).toContain('text-on-error')
    expect(wrapper.attributes('data-variant')).toBe('error')
  })

  it('does not apply error colours to a text button without the error prop', () => {
    const wrapper = mount(MdButton, { props: { variant: 'text' }, slots: { default: 'Cancel' } })

    expect(wrapper.classes()).toContain('text-primary')
    expect(wrapper.classes()).not.toContain('text-error')
  })

  it('shows the disabled colours instead of the error colours when disabled', () => {
    const wrapper = mount(MdButton, { props: { variant: 'text', error: true, disabled: true }, slots: { default: 'Revoke' } })

    expect(wrapper.classes()).not.toContain('text-error')
    expect(wrapper.classes()).toContain('text-on-surface/38')
  })

  it('reports the variant through data-variant', () => {
    const wrapper = mount(MdButton, { props: { variant: 'outlined' }, slots: { default: 'Save' } })

    expect(wrapper.attributes('data-variant')).toBe('outlined')
  })

  it('keeps a 48px minimum touch target', () => {
    const wrapper = mount(MdButton, { slots: { default: 'Save' } })

    expect(wrapper.classes()).toContain('min-h-12')
  })

  it('renders the icon slot in a wrapper hidden from assistive technology', () => {
    const wrapper = mount(MdButton, {
      slots: { icon: () => h('svg', { 'data-test': 'icon' }), default: 'Upload' }
    })

    const iconWrapper = wrapper.find('[data-test="icon"]').element.parentElement
    expect(iconWrapper.getAttribute('aria-hidden')).toBe('true')
  })

  it('renders no icon wrapper when no icon slot is given', () => {
    const wrapper = mount(MdButton, { slots: { default: 'Upload' } })

    expect(wrapper.find('[aria-hidden="true"]').exists()).toBe(false)
  })

  it('forwards click listeners to the rendered element', async () => {
    const onClick = vi.fn()
    const wrapper = mount(MdButton, { attrs: { onClick }, slots: { default: 'Save' } })

    await wrapper.trigger('click')

    expect(onClick).toHaveBeenCalledTimes(1)
  })
})
