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
    expect(container.find('.md-title-large').exists(), 'the title sits in it').toBe(true)
  })

  it('renders the instance name as no heading, so the page keeps the only level one heading', () => {
    const wrapper = mountHeader()

    expect(wrapper.findAll('h1, h2, h3, h4, h5, h6').length, 'the top bar has no heading element').toBe(0)
  })

  it('links the instance name to the start page', () => {
    const wrapper = mountHeader()

    const link = wrapper.find('.md-title-large a')
    expect(link.exists(), 'the name is a link').toBe(true)
    expect(link.attributes('href'), 'the link goes to the start page').toBe('/')
    expect(link.text(), 'the link carries the instance name').toBe(wrapper.find('.md-title-large').text())
  })

  it('keeps the title anchor on the element that holds the name inside the header anchor', () => {
    const wrapper = mountHeader()

    const header = wrapper.find('header')
    const title = wrapper.find('.md-title-large')
    expect(header.attributes('data-ingot-component'), 'the header keeps its component anchor').toBe('default-header')
    expect(title.attributes('data-ingot-part'), 'the title keeps its part anchor').toBe('title')
    expect(header.element.contains(title.element), 'the title anchor sits inside the header anchor').toBe(true)
    expect(title.text().length, 'the anchor holds the instance name').toBeGreaterThan(0)
  })

  it('spans the full window width on every page, so the title lines up with full-width content', () => {
    const wrapper = mountHeader()

    expect(wrapper.find('header > .container').classes(), 'no maximum width, as the wide pages have none').toContain('max-w-none')
  })

  it('draws no rule under the top bar', () => {
    const wrapper = mountHeader()

    expect(wrapper.find('.olf-brand-bar').exists(), 'the top bar draws no gradient rule').toBe(false)
  })

  it('draws no border, shadow or transition on the bar, so the content sheet is the only edge', () => {
    const wrapper = mountHeader()

    const decoration = wrapper.find('header').classes().filter(name => /(^|:)(border|shadow|transition)/.test(name))
    expect(decoration, 'the bar has no border, shadow or transition class').toEqual([])
  })
})
