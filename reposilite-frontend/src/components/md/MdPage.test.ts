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
import MdPage from './MdPage.vue'

describe('MdPage', () => {
  it('renders its default slot content', () => {
    const wrapper = mount(MdPage, { slots: { default: () => h('p', 'Repository list') } })

    expect(wrapper.text()).toBe('Repository list')
  })

  it('takes its side gutter from the shared container utility', () => {
    const wrapper = mount(MdPage)

    expect(wrapper.classes()).toContain('container')
  })

  it('adds no px-* utility to the root, so the window margins still apply', () => {
    const wrapper = mount(MdPage, { props: { measure: true, centred: true } })

    expect(wrapper.classes().filter(name => /(^|:)px-/.test(name))).toEqual([])
  })

  it('caps the width at the reading measure when measure is set', () => {
    const wrapper = mount(MdPage, { props: { measure: true } })

    expect(wrapper.classes()).toContain('max-w-md-reading')
  })

  it('does not cap the width without measure', () => {
    const wrapper = mount(MdPage)

    expect(wrapper.classes()).not.toContain('max-w-md-reading')
  })

  it('lifts the width steps of the container, so the page takes the full width without a max-w class', () => {
    const wrapper = mount(MdPage)

    expect((wrapper.element as HTMLElement).style.maxWidth, 'the 1536 pixel container step is lifted').toBe('none')
    expect(wrapper.classes().filter(name => name.startsWith('max-w-')), 'the page container carries no max-w class').toEqual([])
  })

  it('leaves the reading measure in charge of the width when measure is set', () => {
    const wrapper = mount(MdPage, { props: { measure: true } })

    expect((wrapper.element as HTMLElement).style.maxWidth, 'no inline width overrides the reading measure').toBe('')
  })

  it('centres the page when centred is set', () => {
    const wrapper = mount(MdPage, { props: { centred: true } })

    expect(wrapper.classes()).toContain('mx-auto')
  })

  it('draws the page spacing, 28 pixels above and 48 below, when spaced is set', () => {
    const wrapper = mount(MdPage, { props: { spaced: true } })

    expect(wrapper.classes(), 'the top spacing of a page').toContain('pt-7')
    expect(wrapper.classes(), 'the bottom spacing of a page').toContain('pb-12')
  })

  it('adds no vertical spacing without spaced, so a header or a section keeps its own', () => {
    const wrapper = mount(MdPage)

    expect(wrapper.classes().filter(name => /^(?:p[tby]|m[tby])-/.test(name)), 'no vertical spacing by default').toEqual([])
  })

  it('carries the md-page anchor on the root', () => {
    const wrapper = mount(MdPage)

    expect(wrapper.attributes('data-ingot-component')).toBe('md-page')
  })
})
