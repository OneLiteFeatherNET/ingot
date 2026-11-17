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

import { describe, it, expect, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory, RouterLink } from 'vue-router'
import MdBreadcrumb from './MdBreadcrumb.vue'

// The dashboard is served below a sub-path, so the router is built with the same base. Each test gets its own router and
// its own history, and the jsdom location is reset afterwards, so no test depends on another.
const BASE = '/repo/'
const routes = [{ path: '/:pathMatch(.*)*', component: { render: () => null } }]

type BreadcrumbProps = { items: { label: string, to?: string, href?: string, title?: string }[], label?: string }

const mountWithRouter = (props: BreadcrumbProps) => {
  const router = createRouter({ history: createWebHistory(BASE), routes })
  return mount(MdBreadcrumb, { props, global: { plugins: [router] } })
}

const trail = [
  { label: 'Home', to: '/' },
  { label: 'Repositories', to: '/repositories' },
  { label: 'releases', to: '/repositories/releases' },
  { label: 'net/onelitefeather/ingot' }
]

afterEach(() => {
  window.history.replaceState({}, '', '/')
})

describe('MdBreadcrumb', () => {
  it('renders a nav with the given accessible name', () => {
    const wrapper = mountWithRouter({ items: trail, label: 'File browser trail' })

    expect((wrapper.element as HTMLElement).tagName).toBe('NAV')
    expect(wrapper.attributes('aria-label')).toBe('File browser trail')
  })

  it('lists the crumbs in an ol with one item per crumb', () => {
    const wrapper = mountWithRouter({ items: trail })

    expect(wrapper.find('nav > ol').exists()).toBe(true)
    expect(wrapper.findAll('ol > li')).toHaveLength(trail.length)
  })

  it('hides every separator from assistive technology', () => {
    const wrapper = mountWithRouter({ items: trail })
    const separators = wrapper.findAll('[data-test="breadcrumb-separator"]')

    expect(separators).toHaveLength(trail.length - 1)
    for (const separator of separators) {
      expect(separator.attributes('aria-hidden')).toBe('true')
    }
  })

  it('marks only the last crumb with aria-current="page"', () => {
    const wrapper = mountWithRouter({ items: trail })
    const current = wrapper.findAll('[aria-current]')

    expect(current).toHaveLength(1)
    expect(current[0].attributes('aria-current')).toBe('page')
    expect(current[0].text()).toBe('net/onelitefeather/ingot')
  })

  it('gives every link the 48px touch target', () => {
    const wrapper = mountWithRouter({ items: trail })

    for (const link of wrapper.findAll('a')) {
      expect(link.classes()).toContain('md-touch-target')
    }
  })

  it('truncates the middle crumbs below the crumb width token', () => {
    const wrapper = mountWithRouter({ items: trail })
    const crumbs = wrapper.findAll('ol > li')

    for (const crumb of [crumbs[1], crumbs[2]]) {
      expect(crumb.find('a').classes(), 'the crumb is capped at the width token').toContain('max-w-md-crumb')
      expect(crumb.find('a > span').classes(), 'the name is cut with an ellipsis').toEqual(expect.arrayContaining(['truncate', 'min-w-0']))
    }
  })

  it('does not cap the first and the last crumb', () => {
    const wrapper = mountWithRouter({ items: trail })
    const crumbs = wrapper.findAll('ol > li')

    expect(crumbs[0].find('a').classes()).not.toContain('max-w-md-crumb')
    expect(crumbs[crumbs.length - 1].find('span').classes()).not.toContain('max-w-md-crumb')
  })

  it('gives the current crumb and every link the same minimum height class', () => {
    const wrapper = mountWithRouter({ items: trail })
    const crumbs = [...wrapper.findAll('a'), ...wrapper.findAll('[aria-current="page"]')]

    expect(crumbs.length, 'the trail has links and a current crumb').toBeGreaterThan(1)
    for (const crumb of crumbs) {
      expect(crumb.classes(), `the crumb ${crumb.text()} has the 48px minimum height`).toContain('min-h-12')
    }
  })

  it('shows the full name of a crumb as its title when the item gives one', () => {
    const wrapper = mountWithRouter({
      items: [{ label: 'Home', to: '/', title: 'Index of the repository' }, { label: 'long-name', title: 'a-very-long-name' }]
    })

    expect(wrapper.find('a').attributes('title'), 'the link carries its title').toBe('Index of the repository')
    expect(wrapper.find('[aria-current="page"]').attributes('title'), 'the current crumb carries its title').toBe('a-very-long-name')
  })

  it('truncates the current crumb so a long name keeps the row', () => {
    const wrapper = mountWithRouter({ items: trail })

    expect(wrapper.find('[aria-current="page"] > span').classes(), 'the current name is cut with an ellipsis').toContain('truncate')
  })

  it('carries the md-breadcrumb anchor on the nav', () => {
    const wrapper = mountWithRouter({ items: trail })

    expect(wrapper.attributes('data-ingot-component')).toBe('md-breadcrumb')
  })

  it('links a crumb with a route through the router, so the sub-path base is kept', () => {
    const wrapper = mountWithRouter({ items: trail })
    const links = wrapper.findAll('a')

    expect(links.map(link => link.attributes('href'))).toEqual(['/repo/', '/repo/repositories', '/repo/repositories/releases'])
  })

  it('renders a crumb with a route as a RouterLink', () => {
    const wrapper = mountWithRouter({ items: trail })

    expect(wrapper.findAllComponents(RouterLink)).toHaveLength(3)
  })

  it('renders a crumb with an href as a plain link, not a RouterLink', () => {
    const wrapper = mountWithRouter({
      items: [{ label: 'Docs', href: 'https://docs.example.org/ingot' }, { label: 'Current' }]
    })
    const link = wrapper.find('a')

    expect(link.attributes('href')).toBe('https://docs.example.org/ingot')
    expect(wrapper.findAllComponents(RouterLink)).toHaveLength(0)
  })

  it('never renders the last crumb as a link, even when it has a route', () => {
    const wrapper = mountWithRouter({
      items: [{ label: 'Home', to: '/' }, { label: 'Settings', to: '/settings' }]
    })
    const last = wrapper.findAll('ol > li')[1]

    expect(last.find('a').exists()).toBe(false)
    expect(last.find('[aria-current="page"]').exists()).toBe(true)
    expect(wrapper.findAllComponents(RouterLink)).toHaveLength(1)
  })
})
