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
import { mount, RouterLinkStub } from '@vue/test-utils'
import BreadcrumbNavigation from './BreadcrumbNavigation.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/releases/net/example' })
}))

const mountBreadcrumb = () =>
  mount(BreadcrumbNavigation, {
    props: { parentPath: '/releases/net' },
    global: { stubs: { 'router-link': RouterLinkStub } }
  })

describe('BreadcrumbNavigation', () => {
  it('names the parent link for assistive technology', () => {
    const wrapper = mountBreadcrumb()

    expect(wrapper.find('a[aria-label="Parent directory"]').exists(), 'the icon-only parent link has a name').toBe(true)
  })

  it('shows the current directory as text rather than as a link', () => {
    const wrapper = mountBreadcrumb()
    const current = wrapper.find('[aria-current="page"]')

    expect(current.exists(), 'the current directory is marked as the current page').toBe(true)
    expect(current.text(), 'the marked crumb is the directory being viewed').toBe('example')
    expect(current.element.tagName, 'the current crumb is not a link').not.toBe('A')
  })

  it('links every ancestor directory', () => {
    const wrapper = mountBreadcrumb()
    const linkTexts = wrapper.findAll('a').map(link => link.text())

    expect(linkTexts, 'the ancestors are still reachable').toEqual(expect.arrayContaining(['/', 'releases/', 'net/']))
  })
})
