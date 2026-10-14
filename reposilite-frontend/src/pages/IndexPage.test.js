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
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import IndexPage from './IndexPage.vue'

// The session is replaced by a plain flag, so each test decides whether the user is a manager.
const session = vi.hoisted(() => ({ isManager: null }))

vi.mock('../store/session', () => ({
  useSession: () => ({ isManager: session.isManager })
}))

// The views are heavy and talk to the server. Each one is replaced by a line of text that
// says which section is on screen. The stub builds its node with the Vue instance it is
// given, because the mock factories run before this file's imports are resolved.
const stubs = vi.hoisted(() => ({
  view: (h, label, testId = 'view') => ({
    __esModule: true,
    default: { render: () => h('p', { 'data-test': testId }, label) }
  })
}))

vi.mock('../components/header/DefaultHeader.vue', async () => stubs.view((await import('vue')).h, 'header', 'header'))
vi.mock('../components/header/HeaderHero.vue', async () => stubs.view((await import('vue')).h, 'hero'))
vi.mock('../components/browser/FileBrowserView.vue', async () => stubs.view((await import('vue')).h, 'file browser'))
vi.mock('../components/dashboard/DashboardView.vue', async () => stubs.view((await import('vue')).h, 'dashboard section'))
vi.mock('../components/tokens/TokensView.vue', async () => stubs.view((await import('vue')).h, 'tokens section'))
vi.mock('../components/console/ConsoleView.vue', async () => stubs.view((await import('vue')).h, 'console section'))
vi.mock('../components/settings/SettingsView.vue', async () => stubs.view((await import('vue')).h, 'settings section'))

const mountAt = async (url, { manager }) => {
  session.isManager = ref(manager)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:qualifier(.*)', name: 'Index', component: IndexPage }]
  })
  await router.push(url)
  await router.isReady()
  const wrapper = mount(IndexPage, {
    props: { qualifier: {} },
    global: { plugins: [router] }
  })
  await flushPromises()
  return { wrapper, router }
}

const shownView = (wrapper) =>
  wrapper.find('[data-test="view"]').text()

// The tab bar is made of links, so a section is chosen the way a visitor chooses one.
const chooseTab = async (wrapper, label) => {
  const link = wrapper.findAll('a').find(anchor => anchor.text() === label)
  await link.trigger('click')
  await flushPromises()
}

describe('IndexPage section from the query', () => {
  beforeEach(() => {
    session.isManager = null
  })

  it('shows the manager section named in the query for a manager', async () => {
    const { wrapper } = await mountAt('/?tab=dashboard', { manager: true })

    expect(shownView(wrapper), 'a manager deep link must open the section it names')
      .toBe('dashboard section')
  })

  it('shows overview instead of a manager section for a user who is not a manager', async () => {
    const { wrapper } = await mountAt('/?tab=dashboard', { manager: false })

    expect(shownView(wrapper), 'a hidden section must not leave the page empty')
      .toBe('hero')
  })

  it('shows overview when no tab is named in the query', async () => {
    const { wrapper } = await mountAt('/org/repo', { manager: true })

    expect(shownView(wrapper)).toBe('hero')
  })

  it('writes the tab the user selects into the query', async () => {
    const { wrapper, router } = await mountAt('/', { manager: true })

    await chooseTab(wrapper, 'Tokens')

    expect(router.currentRoute.value.query.tab, 'selecting a tab must update the URL')
      .toBe('tokens')
  })

  it('keeps the browsed repository path when Overview is selected', async () => {
    const { wrapper, router } = await mountAt('/org/repo?tab=dashboard', { manager: true })

    await chooseTab(wrapper, 'Overview')

    expect(router.currentRoute.value.path, 'returning to Overview must not drop the browsed path')
      .toBe('/org/repo')
    expect(router.currentRoute.value.query.tab).toBeUndefined()
  })

  it('marks the selected section as the current page', async () => {
    const { wrapper } = await mountAt('/?tab=dashboard', { manager: true })

    const current = wrapper.findAll('a').find(anchor => anchor.attributes('aria-current') === 'page')

    expect(current?.text(), 'assistive technology must be told which section is open').toBe('Dashboard')
  })

  it('shows the previous section again when the browser goes back', async () => {
    const { wrapper, router } = await mountAt('/?tab=dashboard', { manager: true })

    await router.push('/?tab=tokens')
    await flushPromises()
    await router.back()
    await flushPromises()

    expect(shownView(wrapper), 'the back button must restore the section from the URL')
      .toBe('dashboard section')
  })
})

// The bar and the rail are both in the page. CSS shows one of them, so each test checks both.
const navigations = (wrapper) => wrapper.findAll('nav[aria-label="Primary"]')

const SECTION_LABELS = ['Overview', 'Dashboard', 'Tokens', 'Console', 'Settings']

describe('IndexPage adaptive navigation', () => {
  beforeEach(() => {
    session.isManager = null
  })

  it('has no tab row above the content any more', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    expect(wrapper.find('nav[aria-label="Sections"]').exists(), 'the sections now live in the bar and the rail')
      .toBe(false)
  })

  it('offers the bar and the rail, each with every section, to a manager', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    const labels = navigations(wrapper).map(nav => nav.findAll('a').map(link => link.text()))

    expect(labels, 'the bar and the rail must list the same five sections in the same order')
      .toEqual([SECTION_LABELS, SECTION_LABELS])
  })

  it('shows no navigation at all when the user has only one section', async () => {
    const { wrapper } = await mountAt('/', { manager: false })

    expect(navigations(wrapper), 'a single destination needs no navigation').toHaveLength(0)
  })

  it('marks the active section as the current page in both navigations', async () => {
    const { wrapper } = await mountAt('/?tab=tokens', { manager: true })

    const current = navigations(wrapper).map(nav =>
      nav.findAll('[aria-current="page"]').map(item => item.text()))

    expect(current).toEqual([['Tokens'], ['Tokens']])
  })

  it('switches the section when a rail item is chosen', async () => {
    const { wrapper, router } = await mountAt('/', { manager: true })

    const rail = navigations(wrapper)[1]
    await rail.findAll('a').find(link => link.text() === 'Console').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.tab, 'the rail must update the URL like the bar does')
      .toBe('console')
    expect(shownView(wrapper)).toBe('console section')
  })

  it('links each section to the repository path that was browsed', async () => {
    const { wrapper } = await mountAt('/org/repo?tab=dashboard', { manager: true })

    const tokens = navigations(wrapper)[0].findAll('a').find(link => link.text() === 'Tokens')

    expect(tokens.attributes('href'), 'a section link must keep the path being browsed')
      .toBe('/org/repo?tab=tokens')
  })
})

const rootBarHeight = () => document.documentElement.style.getPropertyValue('--app-bottom-bar-height')

describe('IndexPage bottom bar height', () => {
  beforeEach(() => {
    session.isManager = null
  })

  afterEach(() => {
    document.documentElement.style.removeProperty('--app-bottom-bar-height')
  })

  it('publishes the bar height on the document root when the page has several sections', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    expect(rootBarHeight(), 'the snackbar reads this variable to clear the bar').toBe('80px')
    wrapper.unmount()
  })

  it('publishes zero when the user has only one section', async () => {
    const { wrapper } = await mountAt('/', { manager: false })

    expect(rootBarHeight(), 'no bar means no offset').toBe('0px')
    wrapper.unmount()
  })

  it('removes the variable when the page unmounts', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    wrapper.unmount()

    expect(rootBarHeight(), 'a page that is gone must not leave an offset behind').toBe('')
  })
})
