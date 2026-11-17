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

import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { ROUTES } from '../../router/routes'
import BreadcrumbNavigation from './BreadcrumbNavigation.vue'

// The crumbs are router links into the real route table, so the tests check the URLs that
// the links actually resolve to.
const mountBreadcrumb = async (path = 'releases/net/example') => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ROUTES.map(route => ({ ...route, component: { render: () => null } }))
  })
  // The page being shown is the browse URL of the path, as it is in the application.
  await router.push(`/browse/${path}`)
  await router.isReady()

  return mount(BreadcrumbNavigation, {
    props: { path },
    global: { plugins: [router] }
  })
}

describe('BreadcrumbNavigation', () => {
  let wrapper

  beforeEach(() => {
    wrapper?.unmount()
  })

  it('names the parent link for assistive technology', async () => {
    wrapper = await mountBreadcrumb()

    expect(wrapper.find('a[aria-label="Parent directory"]').exists(), 'the icon-only parent link has a name').toBe(true)
  })

  it('presents the parent control as an icon button', async () => {
    wrapper = await mountBreadcrumb()
    const parent = wrapper.find('[aria-label="Parent directory"]')

    expect(parent.attributes('data-variant'), 'the parent control is an MD3 icon button').toBe('standard')
  })

  it('gives the Index of link a 48px touch target', async () => {
    wrapper = await mountBreadcrumb()
    const index = wrapper.findAll('a').find(link => link.text() === 'Index of')

    expect(index.classes(), 'the link is at least 48px tall').toContain('min-h-12')
    expect(index.classes(), 'the text stays centred in the taller target').toContain('items-center')
  })

  it('gives every ancestor crumb a 48px touch target', async () => {
    wrapper = await mountBreadcrumb()
    const crumbs = wrapper.findAll('ol a')

    expect(crumbs.length, 'the path has ancestor links').toBeGreaterThan(0)
    for (const crumb of crumbs) {
      expect(crumb.classes(), `the crumb ${crumb.text()} is at least 48px tall`).toContain('min-h-12')
      expect(crumb.classes(), 'the text stays centred in the taller target').toContain('items-center')
    }
  })

  it('shows the current directory as text rather than as a link', async () => {
    wrapper = await mountBreadcrumb()
    const current = wrapper.find('[aria-current="page"]')

    expect(current.exists(), 'the current directory is marked as the current page').toBe(true)
    expect(current.text(), 'the marked crumb is the directory being viewed').toBe('example')
    expect(current.element.tagName, 'the current crumb is not a link').not.toBe('A')
  })

  it('links every ancestor directory', async () => {
    wrapper = await mountBreadcrumb()
    const linkTexts = wrapper.findAll('ol a').map(link => link.text())

    expect(linkTexts, 'the ancestors are still reachable').toEqual(['Index of', 'releases', 'net'])
  })

  it('shows the segments with the path separator between them, and no slash inside a name', async () => {
    wrapper = await mountBreadcrumb()
    // The separators are part of their crumb's list item, so the names are read from the crumbs themselves.
    const names = wrapper.findAll('ol a, ol [aria-current="page"]').map(crumb => crumb.text())

    expect(names, 'the trail reads as plain segment names').toEqual(['Index of', 'releases', 'net', 'example'])
  })

  it('draws one muted separator between each pair of crumbs and hides it from assistive technology', async () => {
    wrapper = await mountBreadcrumb()
    const separators = wrapper.findAll('ol span[aria-hidden="true"]')

    expect(separators.length, 'there is one separator per boundary').toBe(3)
    for (const separator of separators) {
      expect(separator.text(), 'the separator is a slash').toBe('/')
      expect(separator.classes(), 'the separator is a muted MD3 token').toContain('text-outline')
    }
  })

  it('keeps the separators out of the links, so a link name is only the directory', async () => {
    wrapper = await mountBreadcrumb()
    const linkNames = wrapper.findAll('ol a').map(link => link.text())

    for (const name of linkNames) {
      expect(name, `the link ${name} carries no separator`).not.toContain('/')
    }
  })

  it('marks the segment being viewed as the current page and the only one', async () => {
    wrapper = await mountBreadcrumb()
    const current = wrapper.findAll('[aria-current="page"]')

    expect(current.map(item => item.text()), 'exactly the last segment is the current page').toEqual(['example'])
  })

  it('names the trail as breadcrumb navigation', async () => {
    wrapper = await mountBreadcrumb()

    expect(wrapper.element.tagName, 'the trail is a nav element').toBe('NAV')
    expect(wrapper.attributes('aria-label')).toBe('Breadcrumb')
    expect(wrapper.find('nav ol').exists(), 'the segments form an ordered list').toBe(true)
  })

  it('lets a long path wrap between segments, not inside them', async () => {
    wrapper = await mountBreadcrumb('releases/net/onelitefeather/mycelium-bom/1.8.9')
    const list = wrapper.find('ol')

    expect(list.classes(), 'the segments flow onto the next line when there is no room').toContain('flex-wrap')
    for (const item of wrapper.findAll('ol li')) {
      expect(item.classes(), `the crumb ${item.text()} may shrink but is not split`).toContain('max-w-full')
    }
    expect(wrapper.html(), 'no crumb forces a break inside its name').not.toMatch(/break-all|break-words|wrap-anywhere/)
  })

  it('shows Index of as the current page at the top of the tree', async () => {
    wrapper = await mountBreadcrumb('')
    const current = wrapper.find('[aria-current="page"]')

    expect(current.text(), 'at the top the page is the index itself').toBe('Index of')
    expect(current.element.tagName, 'the index is not a link to itself').not.toBe('A')
  })

  it('keeps a long directory name on one line with an ellipsis and its full name in the title', async () => {
    const segment = '80-db1-bba-b9621f618-a-very-long-directory-name'
    wrapper = await mountBreadcrumb(`filled/${segment}`)
    const current = wrapper.find('[aria-current="page"]')

    expect(current.classes(), 'the long name is cut with an ellipsis, not wrapped or cut in the middle').toContain('truncate')
    expect(current.attributes('title'), 'hovering the crumb shows the whole name').toBe(segment)
  })

  it('gives every ancestor crumb its full name as a title', async () => {
    wrapper = await mountBreadcrumb('releases/net/example')
    const ancestors = wrapper.findAll('ol a').filter(link => link.text() !== 'Index of')

    expect(ancestors.map(link => link.attributes('title'))).toEqual(['releases', 'net'])
  })

  it('points each ancestor crumb at its browse URL', async () => {
    wrapper = await mountBreadcrumb()
    const net = wrapper.findAll('ol a').find(link => link.text() === 'net')

    expect(net.attributes('href'), 'the crumb links to the browse URL of its directory').toBe('/browse/releases/net')
  })

  it('points the Index of crumb at overview', async () => {
    wrapper = await mountBreadcrumb()
    const root = wrapper.findAll('a').find(link => link.text() === 'Index of')

    expect(root.attributes('href')).toBe('/')
  })

  it('resolves the parent control to the parent directory', async () => {
    wrapper = await mountBreadcrumb()

    expect(wrapper.find('[aria-label="Parent directory"]').attributes('href'), 'the parent link goes one level up')
      .toBe('/browse/releases/net')
  })

  it('resolves the parent of a top level directory to overview', async () => {
    wrapper = await mountBreadcrumb('releases')

    expect(wrapper.find('[aria-label="Parent directory"]').attributes('href')).toBe('/')
  })
})
