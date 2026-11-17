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
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import MdPageHeader from './MdPageHeader.vue'

describe('MdPageHeader', () => {
  it('renders the title as the only h1 of the header', () => {
    const wrapper = mount(MdPageHeader, { props: { title: 'Repositories' } })

    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.find('h1').text()).toBe('Repositories')
  })

  it('renders the description under the title', () => {
    const wrapper = mount(MdPageHeader, { props: { title: 'Repositories', description: 'Every hosted artifact' } })

    expect(wrapper.text()).toContain('Every hosted artifact')
  })

  it('adds no px-* utility to the root', () => {
    const wrapper = mount(MdPageHeader, { props: { title: 'Repositories' } })

    expect(wrapper.classes().filter(name => /(^|:)px-/.test(name))).toEqual([])
  })

  it('gives the title column min-w-0, so a long title wraps inside it', () => {
    const wrapper = mount(MdPageHeader, { props: { title: 'Repositories' } })

    expect(wrapper.find('[data-test="page-header-title"]').classes()).toContain('min-w-0')
  })

  it('places the back slot outside the title column', () => {
    const wrapper = mount(MdPageHeader, {
      props: { title: 'Repository' },
      slots: { back: () => h('a', { 'data-test': 'back-link' }, 'Back') }
    })
    const column = wrapper.find('[data-test="page-header-title"]')

    expect(wrapper.find('[data-test="back-link"]').exists()).toBe(true)
    expect(column.find('[data-test="back-link"]').exists()).toBe(false)
  })

  it('renders the trail slot above the title', () => {
    const wrapper = mount(MdPageHeader, {
      props: { title: 'Repository' },
      slots: { trail: () => h('span', { 'data-test': 'trail' }, 'Repositories') }
    })
    const trail = wrapper.find('[data-test="trail"]').element
    const title = wrapper.find('h1').element

    expect(trail.compareDocumentPosition(title) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('renders the actions slot outside the title column', () => {
    const wrapper = mount(MdPageHeader, {
      props: { title: 'Repository' },
      slots: { actions: () => h('button', { 'data-test': 'create' }, 'Create') }
    })

    expect(wrapper.find('[data-test="page-header-title"]').find('[data-test="create"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="create"]').exists()).toBe(true)
  })

  it('carries the md-page-header anchor on the root', () => {
    const wrapper = mount(MdPageHeader, { props: { title: 'Repositories' } })

    expect(wrapper.attributes('data-ingot-component')).toBe('md-page-header')
  })
})
