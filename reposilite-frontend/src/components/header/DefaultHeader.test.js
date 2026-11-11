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
import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import DefaultHeader from './DefaultHeader.vue'

// The account menu is not under test here, so the session is a signed out one.
vi.mock('../../store/session', () => ({
  useSession: () => ({
    token: ref({ name: '', secret: '' }),
    isLogged: ref(false),
    logout: vi.fn(),
    login: vi.fn(),
    loginFailureMessage: (error) => String(error)
  })
}))

const mountHeader = (props = {}) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:qualifier(.*)', component: { render: () => null } }]
  })
  return mount(DefaultHeader, { props, global: { plugins: [router] } })
}

describe('DefaultHeader', () => {
  it('sits on the surface container colour at the top of the page, like the navigation rail', () => {
    const wrapper = mountHeader()

    expect(wrapper.find('header').classes()).toContain('bg-surface-container')
    expect(wrapper.find('header').classes(), 'the header must not fall back to the page surface')
      .not.toContain('bg-surface')
  })

  it('keeps the header sticky at the top of the page', () => {
    const wrapper = mountHeader()

    expect(wrapper.find('header').classes()).toEqual(expect.arrayContaining(['sticky', 'top-0']))
  })

  it('ends the bar 16px short of the window from 1600px up, as the content sheet does', () => {
    const wrapper = mountHeader()

    expect(wrapper.find('header').classes(), 'the bar and the sheet share one right edge').toContain('min-[1600px]:pr-4')
  })

  it('places the title in the container the page content uses, so both start at one x', () => {
    const wrapper = mountHeader()

    const container = wrapper.find('header > .container')
    expect(container.exists(), 'the title row is the shared container').toBe(true)
    expect(container.find('h1').exists(), 'the title sits in it').toBe(true)
  })

  it('takes the administration frame width when the page is an administration page', () => {
    const wrapper = mountHeader({ framed: true })

    const container = wrapper.find('header > .container')
    expect(container.classes(), 'the same width rule as the administration frame').toEqual(
      expect.arrayContaining(['max-w-[1280px]', 'min-[1920px]:max-w-[1600px]'])
    )
  })

  it('keeps the usual container width on pages without the administration frame', () => {
    const wrapper = mountHeader()

    expect(wrapper.find('header > .container').classes()).not.toContain('max-w-[1280px]')
  })

  it('draws the brand rule under the top bar across its whole width', () => {
    const wrapper = mountHeader()

    const rule = wrapper.find('header > .olf-brand-bar')
    expect(rule.exists(), 'the rule is a direct child of the bar').toBe(true)
    expect(rule.classes(), 'it spans the bar from edge to edge, at its bottom edge').toEqual(
      expect.arrayContaining(['absolute', 'inset-x-0', 'bottom-0'])
    )
    expect(rule.attributes('aria-hidden'), 'the decoration carries no information').toBe('true')
  })
})
