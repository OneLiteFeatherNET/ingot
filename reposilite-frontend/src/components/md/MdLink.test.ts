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

import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import MdLink from './MdLink.vue'

// The router is built inside each test, so no route table or history is shared between tests.
const makeRouter = async (path: string): Promise<Router> => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/browse', component: { render: () => null } },
      { path: '/settings', component: { render: () => null } }
    ]
  })
  await router.push(path)
  await router.isReady()
  return router
}

const mountLink = (props: Record<string, unknown>, router?: Router) =>
  mount(MdLink, {
    props,
    slots: { default: () => 'Repository docs' },
    global: router ? { plugins: [router] } : {}
  })

describe('MdLink external links', () => {
  it('opens an external link in a new tab', () => {
    const link = mountLink({ href: 'https://example.org/docs', external: true }).find('a')

    expect(link.attributes('target'), 'a link outside the dashboard opens in a new tab').toBe('_blank')
  })

  it('sets rel to noopener noreferrer on an external link', () => {
    const link = mountLink({ href: 'https://example.org/docs', external: true }).find('a')

    expect(link.attributes('rel')).toBe('noopener noreferrer')
  })

  it('names the new tab for assistive technology on an external link', () => {
    const wrapper = mountLink({ href: 'https://example.org/docs', external: true })

    expect(wrapper.text(), 'the accessible name says that the link opens in a new tab').toContain('(opens in a new tab)')
  })

  it('keeps the link text ahead of the new tab name', () => {
    const wrapper = mountLink({ href: 'https://example.org/docs', external: true })

    expect(wrapper.text().startsWith('Repository docs')).toBe(true)
  })
})

describe('MdLink internal links', () => {
  it('leaves a plain link without a target', () => {
    const link = mountLink({ href: '/browse' }).find('a')

    expect(link.attributes('target')).toBeUndefined()
  })

  it('leaves a plain link without rel', () => {
    const link = mountLink({ href: '/browse' }).find('a')

    expect(link.attributes('rel')).toBeUndefined()
  })

  it('does not announce a new tab for a link that stays in the dashboard', () => {
    expect(mountLink({ href: '/browse' }).text()).not.toContain('opens in a new tab')
  })

  it('renders a router link with the href of its route', async () => {
    const router = await makeRouter('/browse')
    const link = mountLink({ to: '/settings' }, router).find('a')

    expect(link.attributes('href')).toBe('/settings')
  })

  it('navigates a router link through the router without a reload', async () => {
    const router = await makeRouter('/browse')
    const wrapper = mountLink({ to: '/settings' }, router)

    await wrapper.find('a').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/settings')
  })
})

describe('MdLink presentation', () => {
  it('draws the link in the primary colour role', () => {
    expect(mountLink({ href: '/browse' }).find('a').classes()).toContain('text-primary')
  })

  it('shows a focus ring and a state layer', () => {
    const classes = mountLink({ href: '/browse' }).find('a').classes()

    expect(classes).toContain('md-focus-ring')
    expect(classes).toContain('md-state-layer')
  })

  it('carries the md-link anchor on the root element', () => {
    expect(mountLink({ href: '/browse' }).find('a').attributes('data-ingot-component')).toBe('md-link')
  })
})
