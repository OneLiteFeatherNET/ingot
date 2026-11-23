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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'
import type { Component } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { createAppRouter } from '../router/create'
import type { AppRouterOptions } from '../router/create'
import IndexPage from './IndexPage.vue'

const session = vi.hoisted(() => ({ can: (): boolean => true }))

vi.mock('../store/session', () => ({
  useSession: () => ({ isManager: ref(true), isLogged: ref(true), mustChangePassword: ref(false), can: session.can, credentials: { value: {} } })
}))

vi.mock('../store/login', () => ({ openLogin: vi.fn() }))

// No built-in page is left over for More today, so the last page of the bar is moved under it.
vi.mock('../navigation/registry', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../navigation/registry')>()
  return {
    ...actual,
    barLayout: (can: Parameters<typeof actual.barLayout>[0]) => {
      const layout = actual.barLayout(can)
      return { bar: layout.bar.slice(0, -1), more: [{ heading: 'More', pages: layout.bar.slice(-1) }] }
    }
  }
})

vi.mock('../store/pluginUi', async () => {
  const { ref: makeRef } = await import('vue')
  return { pluginUiReady: makeRef(true), loadPluginUi: vi.fn(), clearPluginUi: vi.fn(), readerPagesReady: makeRef(true), loadReaderPages: vi.fn() }
})

vi.mock('../components/header/DefaultHeader.vue', async () => {
  const { h } = await import('vue')
  return { __esModule: true, default: { name: 'HeaderStub', render: () => h('header') } }
})

vi.mock('../components/header/HeaderHero.vue', async () => {
  const { h } = await import('vue')
  return { __esModule: true, default: { name: 'HeroStub', render: () => h('p', 'hero') } }
})

vi.mock('../components/browser/FileBrowserView.vue', async () => {
  const { h } = await import('vue')
  return { __esModule: true, default: { name: 'FileBrowserStub', render: () => h('p', 'browser') } }
})

vi.mock('../components/overview/RepositoryOverview.vue', async () => {
  const { h } = await import('vue')
  return { __esModule: true, default: { name: 'RepositoryOverviewStub', render: () => h('p', 'overview') } }
})

let mounted: VueWrapper[] = []

// The wrapper is attached to the document, so the focus and the document keydown are the real ones.
const mountWithMore = async (): Promise<VueWrapper> => {
  const router = createAppRouter({
    history: createMemoryHistory(),
    component: IndexPage as Component,
    whenSessionKnown: () => Promise.resolve(),
    isManager: () => true,
    can: () => session.can(),
    isLogged: () => true,
    openLogin: vi.fn(),
    notify: () => {},
  } as AppRouterOptions)
  await router.push('/')
  await router.isReady()
  const wrapper = mount(IndexPage, { props: { qualifier: { path: '', watchable: 0 } }, global: { plugins: [router] }, attachTo: document.body }) as VueWrapper
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

const moreButton = (wrapper: VueWrapper) =>
  wrapper.findAll('nav[aria-label="Primary"]')[0].findAll('button').find(button => button.text() === 'More')!

const PANEL = 'nav[aria-label="More sections"]'

const escape = () => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))

beforeEach(() => {
  document.body.innerHTML = ''
})

afterEach(() => {
  mounted.forEach(wrapper => wrapper.unmount())
  mounted = []
})

describe('IndexPage More panel and Escape', () => {
  it('closes the More panel with Escape while the focus is on the More item', async () => {
    const wrapper = await mountWithMore()
    await moreButton(wrapper).trigger('click')
    ;(moreButton(wrapper).element as HTMLElement).focus()

    escape()
    await flushPromises()

    expect(wrapper.find(PANEL).exists(), 'Escape closes the panel from its trigger').toBe(false)
  })

  it('keeps the focus on the More item after Escape closed the panel', async () => {
    const wrapper = await mountWithMore()
    await moreButton(wrapper).trigger('click')
    ;(moreButton(wrapper).element as HTMLElement).focus()

    escape()
    await flushPromises()

    expect(document.activeElement, 'the focus stays on the trigger').toBe(moreButton(wrapper).element)
  })

  it('returns the focus to the More item when Escape is pressed inside the panel', async () => {
    const wrapper = await mountWithMore()
    await moreButton(wrapper).trigger('click')
    ;(wrapper.find(`${PANEL} a`).element as HTMLElement).focus()

    await wrapper.find(PANEL).trigger('keydown', { key: 'Escape' })
    await flushPromises()

    expect(wrapper.find(PANEL).exists(), 'Escape closes the panel').toBe(false)
    expect(document.activeElement, 'the focus goes back to the trigger').toBe(moreButton(wrapper).element)
  })

  it('does not take the focus on Escape while the panel is closed', async () => {
    const wrapper = await mountWithMore()
    const other = document.createElement('button')
    document.body.append(other)
    other.focus()

    escape()
    await flushPromises()

    expect(document.activeElement, 'the focus is left where it was').toBe(other)
    expect(wrapper.find(PANEL).exists()).toBe(false)
  })
})
