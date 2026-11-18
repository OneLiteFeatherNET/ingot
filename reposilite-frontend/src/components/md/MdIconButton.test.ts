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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import MdIconButton from './MdIconButton.vue'

describe('MdIconButton', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('MdIconButton without aria-label warns', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})

    mount(MdIconButton, { slots: { default: () => h('svg') } })

    expect(warn).toHaveBeenCalledWith(expect.stringContaining('aria-label'))
  })

  it('MdIconButton with aria-label does not warn', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})

    mount(MdIconButton, {
      attrs: { 'aria-label': 'Delete' },
      slots: { default: () => h('svg') }
    })

    expect(warn).not.toHaveBeenCalled()
  })

  it('passes aria-label through to the rendered button', () => {
    const wrapper = mount(MdIconButton, {
      attrs: { 'aria-label': 'Delete' },
      slots: { default: () => h('svg') }
    })

    expect((wrapper.element as HTMLElement).tagName).toBe('BUTTON')
    expect(wrapper.attributes('aria-label')).toBe('Delete')
  })

  it('renders an anchor when href is given', () => {
    const wrapper = mount(MdIconButton, {
      props: { href: '/settings' },
      attrs: { 'aria-label': 'Settings' },
      slots: { default: () => h('svg') }
    })

    expect((wrapper.element as HTMLElement).tagName).toBe('A')
    expect(wrapper.attributes('href')).toBe('/settings')
  })

  it('reports the selected state through aria-pressed when toggle is set', () => {
    const wrapper = mount(MdIconButton, {
      props: { toggle: true, selected: true },
      attrs: { 'aria-label': 'Favourite' },
      slots: { default: () => h('svg') }
    })

    expect(wrapper.attributes('aria-pressed')).toBe('true')
  })

  it('renders no aria-pressed when toggle is not set', () => {
    const wrapper = mount(MdIconButton, {
      props: { selected: true },
      attrs: { 'aria-label': 'Favourite' },
      slots: { default: () => h('svg') }
    })

    expect(wrapper.attributes('aria-pressed')).toBeUndefined()
  })

  it('uses the primary colour roles for the filled variant', () => {
    const wrapper = mount(MdIconButton, {
      props: { variant: 'filled' },
      attrs: { 'aria-label': 'Add' },
      slots: { default: () => h('svg') }
    })

    expect(wrapper.classes()).toContain('bg-primary')
    expect(wrapper.classes()).toContain('text-on-primary')
  })

  it('uses the error role as text colour for the error variant', () => {
    const wrapper = mount(MdIconButton, {
      props: { variant: 'error' },
      attrs: { 'aria-label': 'Revoke token' },
      slots: { default: () => h('svg') }
    })

    expect(wrapper.classes()).toContain('text-error')
    expect(wrapper.classes()).not.toContain('bg-error')
  })

  it('is a 40px square container and state layer', () => {
    const wrapper = mount(MdIconButton, {
      attrs: { 'aria-label': 'Close' },
      slots: { default: () => h('svg') }
    })

    expect(wrapper.classes()).toContain('h-10')
    expect(wrapper.classes()).toContain('w-10')
    expect(wrapper.classes()).not.toContain('h-12')
  })

  it('sizes the icon to 24px inside the 40px container', () => {
    const wrapper = mount(MdIconButton, {
      attrs: { 'aria-label': 'Close' },
      slots: { default: () => h('svg') }
    })

    expect(wrapper.classes()).toContain('[&>svg]:size-6')
  })

  it('marks the root with the touch target class that widens the hit area to 48px', () => {
    const wrapper = mount(MdIconButton, {
      attrs: { 'aria-label': 'Close' },
      slots: { default: () => h('svg') }
    })

    expect(wrapper.classes()).toContain('md-touch-target')
  })
})

const makeRouter = async (path: string) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { render: () => null } },
      { path: '/settings', component: { render: () => null } }
    ]
  })
  await router.push(path)
  await router.isReady()
  return router
}

describe('MdIconButton compact size, to and inverse', () => {
  it('draws the compact size as a 24px container inside the 48px hit area', () => {
    const wrapper = mount(MdIconButton, {
      props: { size: 'compact' },
      attrs: { 'aria-label': 'Remove' },
      slots: { default: () => h('svg') }
    })

    expect(wrapper.classes(), 'the compact button is 24px wide').toContain('w-6')
    expect(wrapper.classes(), 'the compact button is 24px tall').toContain('h-6')
    expect(wrapper.classes(), 'the compact button is not the 40px square').not.toContain('h-10')
    expect(wrapper.classes(), 'the hit area stays 48px').toContain('md-touch-target')
  })

  it('renders a router link when to is given', async () => {
    const router = await makeRouter('/')
    const wrapper = mount(MdIconButton, {
      props: { to: '/settings' },
      attrs: { 'aria-label': 'Settings' },
      slots: { default: () => h('svg') },
      global: { plugins: [router] }
    })

    expect((wrapper.element as HTMLElement).tagName).toBe('A')
    expect(wrapper.attributes('href')).toBe('/settings')
  })

  it('moves the router to the target of a plain click on a to button', async () => {
    const router = await makeRouter('/')
    const wrapper = mount(MdIconButton, {
      props: { to: '/settings' },
      attrs: { 'aria-label': 'Settings' },
      slots: { default: () => h('svg') },
      global: { plugins: [router] }
    })

    await wrapper.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path, 'the plain click navigates in the router').toBe('/settings')
  })

  it('uses the inverse text role for an inverse button', () => {
    const wrapper = mount(MdIconButton, {
      props: { inverse: true },
      attrs: { 'aria-label': 'Dismiss notification' },
      slots: { default: () => h('svg') }
    })

    expect(wrapper.classes()).toContain('text-inverse-on-surface')
    expect(wrapper.classes(), 'the standard secondary role is replaced').not.toContain('text-on-surface-variant')
  })

  it('shows the disabled colours instead of the inverse colours when disabled', () => {
    const wrapper = mount(MdIconButton, {
      props: { inverse: true, disabled: true },
      attrs: { 'aria-label': 'Dismiss notification' },
      slots: { default: () => h('svg') }
    })

    expect(wrapper.classes()).not.toContain('text-inverse-on-surface')
    expect(wrapper.classes()).toContain('text-on-surface/38')
  })
})
