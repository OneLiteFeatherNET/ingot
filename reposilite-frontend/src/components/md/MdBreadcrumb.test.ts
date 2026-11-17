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
import MdBreadcrumb from './MdBreadcrumb.vue'

const trail = [
  { label: 'Home', href: '/' },
  { label: 'Repositories', href: '/repositories' },
  { label: 'releases', href: '/repositories/releases' },
  { label: 'net/onelitefeather/ingot' }
]

describe('MdBreadcrumb', () => {
  it('renders a nav with the given accessible name', () => {
    const wrapper = mount(MdBreadcrumb, { props: { items: trail, label: 'File browser trail' } })

    expect(wrapper.element.tagName).toBe('NAV')
    expect(wrapper.attributes('aria-label')).toBe('File browser trail')
  })

  it('lists the crumbs in an ol with one item per crumb', () => {
    const wrapper = mount(MdBreadcrumb, { props: { items: trail } })

    expect(wrapper.find('nav > ol').exists()).toBe(true)
    expect(wrapper.findAll('ol > li')).toHaveLength(trail.length)
  })

  it('hides every separator from assistive technology', () => {
    const wrapper = mount(MdBreadcrumb, { props: { items: trail } })
    const separators = wrapper.findAll('[data-test="breadcrumb-separator"]')

    expect(separators).toHaveLength(trail.length - 1)
    for (const separator of separators) {
      expect(separator.attributes('aria-hidden')).toBe('true')
    }
  })

  it('marks only the last crumb with aria-current="page"', () => {
    const wrapper = mount(MdBreadcrumb, { props: { items: trail } })
    const current = wrapper.findAll('[aria-current]')

    expect(current).toHaveLength(1)
    expect(current[0].attributes('aria-current')).toBe('page')
    expect(current[0].text()).toBe('net/onelitefeather/ingot')
  })

  it('links every crumb before the last one', () => {
    const wrapper = mount(MdBreadcrumb, { props: { items: trail } })
    const links = wrapper.findAll('a')

    expect(links.map(link => link.attributes('href'))).toEqual(['/', '/repositories', '/repositories/releases'])
  })

  it('gives every link the 48px touch target', () => {
    const wrapper = mount(MdBreadcrumb, { props: { items: trail } })

    for (const link of wrapper.findAll('a')) {
      expect(link.classes()).toContain('md-touch-target')
    }
  })

  it('truncates the middle crumbs below the crumb width token', () => {
    const wrapper = mount(MdBreadcrumb, { props: { items: trail } })
    const crumbs = wrapper.findAll('ol > li')

    for (const crumb of [crumbs[1], crumbs[2]]) {
      expect(crumb.find('a, span').classes()).toEqual(expect.arrayContaining(['truncate', 'max-w-md-crumb']))
    }
  })

  it('does not truncate the first and the last crumb', () => {
    const wrapper = mount(MdBreadcrumb, { props: { items: trail } })
    const crumbs = wrapper.findAll('ol > li')

    expect(crumbs[0].find('a').classes()).not.toContain('max-w-md-crumb')
    expect(crumbs[crumbs.length - 1].find('span').classes()).not.toContain('max-w-md-crumb')
  })

  it('carries the md-breadcrumb anchor on the nav', () => {
    const wrapper = mount(MdBreadcrumb, { props: { items: trail } })

    expect(wrapper.attributes('data-ingot-component')).toBe('md-breadcrumb')
  })
})
