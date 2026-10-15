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
import BreadcrumbNavigation from './BreadcrumbNavigation.vue'

// A real router on memory history: the parent control is a router link, and the tests check
// what it actually resolves to.
const mountBreadcrumb = async (path = '/releases/net/example') => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }]
  })
  await router.push(path)
  await router.isReady()

  return mount(BreadcrumbNavigation, {
    props: { parentPath: '/releases/net' },
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
    const linkTexts = wrapper.findAll('a').map(link => link.text())

    expect(linkTexts, 'the ancestors are still reachable').toEqual(expect.arrayContaining(['/', 'releases/', 'net/']))
  })

  it('keeps a long directory name on one line with an ellipsis and its full name in the title', async () => {
    const segment = '80-db1-bba-b9621f618-a-very-long-directory-name'
    wrapper = await mountBreadcrumb(`/filled/${segment}`)
    const current = wrapper.find('[aria-current="page"]')

    expect(current.classes(), 'the long name is cut with an ellipsis, not wrapped or cut in the middle').toContain('truncate')
    expect(current.attributes('title'), 'hovering the crumb shows the whole name').toBe(segment)
  })

  it('gives every ancestor crumb its full name as a title', async () => {
    wrapper = await mountBreadcrumb('/releases/net/example')
    const ancestors = wrapper.findAll('ol a')

    expect(ancestors.map(link => link.attributes('title'))).toEqual(['/', 'releases', 'net'])
  })

  it('resolves the parent control to the parent directory', async () => {
    wrapper = await mountBreadcrumb()

    expect(wrapper.find('[aria-label="Parent directory"]').attributes('href'), 'the parent link goes one level up').toBe('/releases/net')
  })
})
