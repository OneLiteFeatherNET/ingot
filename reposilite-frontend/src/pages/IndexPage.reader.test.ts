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
import type { Component, Ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { createAppRouter } from '../router/create'
import type { AppRouterOptions } from '../router/create'
import { canUse } from '../store/nodeAccess'
import { setContributions, setReaderPages } from '../navigation/registry'
import type { PluginManifest, ReaderManifest } from '../navigation/registry'
import IndexPage from './IndexPage.vue'

// The session flags the mock hands to the page. Each test sets them before the page is mounted, so they start empty.
interface SessionFlags {
  isManager: Ref<boolean> | null
  isLogged: Ref<boolean> | null
  mustChange: Ref<boolean> | null
  can: ((node: string) => boolean) | null
}

const session = vi.hoisted((): SessionFlags => ({ isManager: null, isLogged: null, mustChange: null, can: null }))
const login = vi.hoisted(() => ({ openLogin: vi.fn() }))

vi.mock('../store/session', () => ({
  useSession: () => ({ isManager: session.isManager, isLogged: session.isLogged, mustChangePassword: session.mustChange, can: session.can, credentials: { value: {} } })
}))

vi.mock('../store/login', () => ({ openLogin: login.openLogin }))

// Both manifests are set by each test through the registry, so loading them from the server is replaced.
vi.mock('../store/pluginUi', async () => {
  const { ref: makeRef } = await import('vue')
  return {
    pluginUiReady: makeRef(true),
    loadPluginUi: vi.fn(),
    clearPluginUi: vi.fn(),
    readerPagesReady: makeRef(true),
    loadReaderPages: vi.fn(),
  }
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

const QUALIFIER = { path: '', watchable: 0 }

const READERS: ReaderManifest = {
  version: 1,
  pages: [
    { plugin: 'docs', id: 'guides', title: 'Guides', description: 'How to publish to this repository.', blocks: [{ type: 'text', text: 'Read me first' }] },
    { plugin: 'docs', id: 'faq', title: 'Questions', blocks: [] },
  ],
}

const ADMIN_PAGES: PluginManifest = {
  pages: [{ plugin: 'mirror', id: 'credentials', title: 'Mirror credentials', area: '*', blocks: [] }],
  sections: [],
  enabledPlugins: ['mirror'],
}

let mounted: VueWrapper[] = []

// The page as the application builds it: the router on memory history, the session answered at once.
const mountAt = async (url: string, { nodes = [], logged = false }: { nodes?: string[]; logged?: boolean } = {}) => {
  session.isManager = ref(canUse(nodes, 'ingot.admin.*'))
  session.can = (node) => canUse(nodes, node)
  session.isLogged = ref(logged)
  session.mustChange = ref(false)
  const router = createAppRouter({
    history: createMemoryHistory(),
    component: IndexPage as Component,
    whenSessionKnown: () => Promise.resolve(),
    isManager: () => session.isManager!.value,
    can: (node) => session.can!(node),
    openLogin: login.openLogin,
  } as AppRouterOptions)
  await router.push(url)
  await router.isReady()
  const wrapper = mount(IndexPage, { props: { qualifier: QUALIFIER }, global: { plugins: [router] } }) as VueWrapper
  mounted.push(wrapper)
  await flushPromises()
  return { wrapper, router }
}

// The rail is the second primary navigation; the first is the bottom bar.
const rail = (wrapper: VueWrapper) => wrapper.findAll('nav[aria-label="Primary"]')[1]
const railGroupLabels = (wrapper: VueWrapper) => rail(wrapper).findAll('[role="group"]').map(group => group.attributes('aria-label'))
const bar = (wrapper: VueWrapper) => wrapper.findAll('nav[aria-label="Primary"]')[0]

beforeEach(() => {
  login.openLogin.mockClear()
})

afterEach(() => {
  mounted.forEach(wrapper => wrapper.unmount())
  mounted = []
  setContributions(null)
  setReaderPages(null)
})

describe('a reader page in the dashboard', () => {
  it('shows a reader page to an anonymous visitor, without a sign in prompt', async () => {
    setReaderPages(READERS)

    const { wrapper } = await mountAt('/plugins/docs/guides')

    expect(wrapper.find('main h1').text(), 'the title of the reader page').toBe('Guides')
    expect(wrapper.text(), 'the text block of the page').toContain('Read me first')
    expect(wrapper.text(), 'no sign in prompt on a reader page').not.toContain('Sign in to open')
    expect(login.openLogin, 'the sign in dialog is not opened').not.toHaveBeenCalled()
  })

  it('shows the not found page for a reader page the manifest does not name', async () => {
    setReaderPages(READERS)

    const { wrapper } = await mountAt('/plugins/docs/unknown')

    expect(wrapper.find('#not-found-title').text(), 'an unknown reader page is not found').toBe('Page not found')
  })

  it('opens the first page of the plugin for an address without a page id', async () => {
    setReaderPages(READERS)

    const { router } = await mountAt('/plugins/docs')
    await flushPromises()

    expect(router.currentRoute.value.fullPath, 'the first reader page of docs').toBe('/plugins/docs/guides')
  })
})

describe('the Plugin pages heading of the rail', () => {
  it('draws no Plugin pages heading without a reader page', async () => {
    const { wrapper } = await mountAt('/')

    expect(railGroupLabels(wrapper), 'no heading while there is no reader page').not.toContain('Plugin pages')
  })

  it('lists the reader pages under Plugin pages for an anonymous visitor', async () => {
    setReaderPages(READERS)

    const { wrapper } = await mountAt('/')

    const group = rail(wrapper).find('[role="group"][aria-label="Plugin pages"]')
    expect(group.findAll('a').map(link => link.text()), 'the reader pages in manifest order').toEqual(['Guides', 'Questions'])
    expect(group.find('a').attributes('href'), 'the link opens the reader address').toBe('/plugins/docs/guides')
  })

  it('keeps reader pages out of the bottom bar', async () => {
    setReaderPages(READERS)

    const { wrapper } = await mountAt('/')

    expect(bar(wrapper).text(), 'no reader page in the bar').not.toContain('Guides')
  })

  it('names the reader heading apart from the Plugins heading of admin pages', async () => {
    setReaderPages(READERS)
    setContributions(ADMIN_PAGES)

    const { wrapper } = await mountAt('/', { nodes: ['ingot.admin.*'], logged: true })

    expect(railGroupLabels(wrapper), 'one heading each, never two called Plugins').toEqual(['Plugin pages', 'Plugins'])
  })

  it('marks the reader page on screen as the current entry of the rail', async () => {
    setReaderPages(READERS)

    const { wrapper } = await mountAt('/plugins/docs/faq')

    expect(rail(wrapper).find('a[aria-current="page"]').text(), 'the reader page is current').toBe('Questions')
  })
})

// Entries of plugin pages have no icon of their own, so the lookup falls back to the settings icon.
describe('the icon of a plugin page entry', () => {
  it('draws an icon for every entry of the rail, plugin pages included', async () => {
    setReaderPages(READERS)
    setContributions(ADMIN_PAGES)
    const { wrapper } = await mountAt('/', { nodes: ['ingot.admin.*'], logged: true })

    const entries = rail(wrapper).findAll('a').filter(entry => entry.text() !== '')
    const bare = entries.filter(entry => !entry.find('svg').exists()).map(entry => entry.text())
    expect(entries.map(entry => entry.text()), 'the plugin pages are listed').toEqual(expect.arrayContaining(['Guides', 'Mirror credentials']))
    expect(bare, 'no rail entry without an icon').toEqual([])
  })

  it('draws an icon for every entry of the bottom bar', async () => {
    const { wrapper } = await mountAt('/', { nodes: ['ingot.admin.*'], logged: true })

    const entries = bar(wrapper).findAll('a, button')
    const bare = entries.filter(entry => !entry.find('svg').exists()).map(entry => entry.text())
    expect(bare, 'no bar entry without an icon').toEqual([])
  })
})
