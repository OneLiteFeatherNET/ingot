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
import { createMemoryHistory } from 'vue-router'
import { createAppRouter } from '../router/create'
import { canUse } from '../store/nodeAccess'
import IndexPage from './IndexPage.vue'
import PageFooter from '../components/footer/PageFooter.vue'

// The session is replaced by a plain flag, so each test decides whether the user is a manager.
const session = vi.hoisted(() => ({ isManager: null, isLogged: null, mustChange: null, can: null }))
const login = vi.hoisted(() => ({ openLogin: vi.fn() }))

vi.mock('../store/session', () => ({
  useSession: () => ({ isManager: session.isManager, isLogged: session.isLogged, mustChangePassword: session.mustChange, can: session.can })
}))

vi.mock('../store/login', () => ({ openLogin: login.openLogin }))

// The views are heavy and talk to the server, so each one is replaced by a stub. A stub says
// which section it is and keeps the props it was given, so the tests can check what the page
// hands down. Factories run before this file's imports, hence the dynamic import of vue.
vi.mock('../components/header/DefaultHeader.vue', async () => {
  const { h } = await import('vue')
  return { __esModule: true, default: { name: 'HeaderStub', render: () => h('header') } }
})

vi.mock('../components/header/HeaderHero.vue', async () => {
  const { h } = await import('vue')
  return { __esModule: true, default: { name: 'HeroStub', render: () => h('p', { 'data-test': 'hero' }, 'hero') } }
})

const sectionStub = vi.hoisted(() => (name, key) => async () => {
  const { h, defineComponent } = await import('vue')
  return {
    __esModule: true,
    default: defineComponent({
      name,
      props: {
        selectedTab: String,
        qualifier: Object,
        search: String,
        tab: String,
        name: String,
        level: String,
        domain: String,
        collection: String,
        entry: String
      },
      emits: ['update:search', 'update:level', 'update:domain'],
      render() {
        return h('p', { 'data-test': 'section', 'data-section': key, 'data-stub': name }, key)
      }
    })
  }
})

vi.mock('../components/browser/FileBrowserView.vue', sectionStub('FileBrowserStub', 'overview'))
vi.mock('../components/overview/RepositoryOverview.vue', sectionStub('RepositoryOverviewStub', 'overview'))
vi.mock('../components/dashboard/DashboardView.vue', sectionStub('DashboardStub', 'dashboard'))
vi.mock('../components/access/AccessView.vue', sectionStub('AccessStub', 'access'))
vi.mock('../components/console/ConsoleView.vue', sectionStub('ConsoleStub', 'console'))
vi.mock('../components/maintenance/MaintenanceView.vue', sectionStub('MaintenanceStub', 'maintenance'))
vi.mock('../components/account/AccountView.vue', sectionStub('AccountStub', 'account'))
vi.mock('../components/audit/AuditView.vue', sectionStub('AuditStub', 'audit'))
vi.mock('../components/search/SearchView.vue', sectionStub('SearchStub', 'search'))
vi.mock('../components/settings/SettingsView.vue', sectionStub('SettingsStub', 'settings'))

const QUALIFIER = { path: '', watchable: 0 }

// Builds the router the application builds, on memory history, with the session answered
// at once. The page is mounted directly, with the router beside it, as the app does.
const mountAt = async (url, { manager = false, logged = manager, mustChange = false, nodes } = {}) => {
  session.isManager = ref(manager)
  // Without a node list the session is a legacy one and the manager flag answers, as in the store.
  session.can = (node) => (nodes === undefined ? session.isManager.value : canUse(nodes, node))
  session.isLogged = ref(logged)
  session.mustChange = ref(mustChange)
  const router = createAppRouter({
    history: createMemoryHistory(),
    component: IndexPage,
    whenSessionKnown: () => Promise.resolve(),
    isManager: () => session.isManager.value,
    can: (node) => session.can(node),
    isLogged: () => logged,
    openLogin: login.openLogin,
    notify: () => {}
  })
  await router.push(url)
  await router.isReady()
  const wrapper = mount(IndexPage, {
    props: { qualifier: QUALIFIER },
    global: { plugins: [router] }
  })
  await flushPromises()
  return { wrapper, router }
}

const shownSection = (wrapper) => {
  const section = wrapper.find('[data-test="section"]')
  return section.exists() ? section.attributes('data-section') : null
}

// The navigation is made of links, so a section is chosen the way a visitor chooses one.
const chooseLink = async (wrapper, label) => {
  const link = wrapper.findAll('a').find(anchor => anchor.text() === label)
  await link.trigger('click')
  await flushPromises()
}

const navigations = (wrapper) => wrapper.findAll('nav[aria-label="Primary"]')

const SECTION_LABELS = ['Overview', 'Search', 'Dashboard', 'Access', 'Console', 'Maintenance', 'Audit', 'Settings']
// The bottom bar keeps the daily sections and puts the rarer admin pages under More.
const BAR_LABELS = ['Overview', 'Search', 'Dashboard', 'Access']

describe('IndexPage footer and content sheet', () => {
  beforeEach(() => {
    session.isManager = null
  })

  it('places a footer inside the content sheet when the navigation frame shows', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    const sheet = wrapper.find('.pane-sheet')

    expect(sheet.exists(), 'the navigation frame has a content sheet').toBe(true)
    expect(sheet.findAllComponents(PageFooter), 'the sheet closes with the footer').toHaveLength(1)
  })

  it('keeps a second footer below the page, outside the sheet, for narrow windows', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    const footers = wrapper.findAllComponents(PageFooter)
    const outsideSheet = footers.filter(footer => footer.element.closest('.pane-sheet') === null)

    expect(footers, 'one footer in the sheet and one below the page').toHaveLength(2)
    expect(outsideSheet, 'the page footer sits outside the sheet').toHaveLength(1)
  })

  it('makes the sheet at least as tall as the window below the top bar', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    expect(wrapper.find('.pane-sheet').classes(), 'the sheet fills the window below the 4rem top bar')
      .toContain('min-[600px]:min-h-[calc(100dvh_-_4rem)]')
  })
})

describe('IndexPage section from the route', () => {
  beforeEach(() => {
    session.isManager = null
  })

  it.each([
    ['/dashboard', 'dashboard'],
    ['/access/users', 'access'],
    ['/access/machines/ci-bot', 'access'],
    ['/console', 'console'],
    ['/maintenance', 'maintenance'],
    ['/audit', 'audit'],
    ['/settings', 'settings'],
    ['/settings/maven/repositories/releases', 'settings'],
  ])('shows the section of %s for a manager', async (url, section) => {
    const { wrapper } = await mountAt(url, { manager: true })

    expect(shownSection(wrapper), `${url} opens ${section}`).toBe(section)
  })

  it('shows the repository overview at the root, below the hero', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    expect(shownSection(wrapper)).toBe('overview')
    expect(wrapper.find('[data-test="section"]').attributes('data-stub'), 'the root lists the repositories').toBe('RepositoryOverviewStub')
    expect(wrapper.find('[data-test="hero"]').exists(), 'the overview carries the repository hero').toBe(true)
  })

  it('shows the file browser for a browsed repository path', async () => {
    const { wrapper } = await mountAt('/browse/releases/gav', { manager: false })

    expect(shownSection(wrapper)).toBe('overview')
    expect(wrapper.find('[data-test="section"]').attributes('data-stub'), 'a browsed path keeps the folder listing').toBe('FileBrowserStub')
  })

  it('shows the not found page on an address no route claims, and no section', async () => {
    const { wrapper } = await mountAt('/no/such/place', { manager: true })

    expect(wrapper.text()).toContain('Page not found')
    expect(shownSection(wrapper)).toBeNull()
  })
})

describe('IndexPage manager routes', () => {
  beforeEach(() => {
    session.isManager = null
    login.openLogin.mockClear()
  })

  it('asks an anonymous visitor to sign in in place of the section', async () => {
    const { wrapper } = await mountAt('/access/users', { manager: false, logged: false })

    expect(shownSection(wrapper), 'the section must not render without the permission').toBeNull()
    expect(wrapper.text()).toContain('Sign in to open Access')
  })

  it('explains the sign in prompt of a manager section with permission nodes, not with a manager role', async () => {
    const { wrapper } = await mountAt('/access/users', { manager: false, logged: false })

    expect(wrapper.text()).toContain('right permission nodes')
    expect(wrapper.text(), 'access is node based, so the prompt must not call the section manager only').not.toContain('only open to managers')
  })

  it('asks for the sign in dialog as soon as an anonymous visitor arrives on a manager section', async () => {
    await mountAt('/access/users', { manager: false, logged: false })

    expect(login.openLogin, 'the dialog lives in the page, so it is asked for once the page is shown').toHaveBeenCalledTimes(1)
  })

  it('does not ask for the sign in dialog when the visitor may see the section', async () => {
    await mountAt('/access/users', { manager: true })

    expect(login.openLogin).not.toHaveBeenCalled()
  })

  it('opens the sign in dialog from the prompt', async () => {
    const { wrapper } = await mountAt('/console', { manager: false, logged: false })
    // Arriving on the page already opens the dialog once; the button has to open it again.
    login.openLogin.mockClear()

    await wrapper.findAll('button').find(button => button.text() === 'Sign in').trigger('click')

    expect(login.openLogin).toHaveBeenCalledTimes(1)
  })

  it('stays on the requested URL while the visitor signs in', async () => {
    const { router } = await mountAt('/access/machines/ci-bot', { manager: false, logged: false })

    expect(router.currentRoute.value.fullPath).toBe('/access/machines/ci-bot')
  })

  it('sends a visitor whose sign in does not grant the permission to overview', async () => {
    const { router } = await mountAt('/access/users', { manager: false, logged: false })

    session.isLogged.value = true
    await flushPromises()

    expect(router.currentRoute.value.fullPath, 'a token without the permission cannot open the section').toBe('/')
  })

  it('shows the section once the visitor has become a manager', async () => {
    const { wrapper } = await mountAt('/access/users', { manager: false, logged: false })

    session.isManager.value = true
    await flushPromises()

    expect(shownSection(wrapper)).toBe('access')
  })
})

describe('IndexPage account page', () => {
  beforeEach(() => {
    session.isManager = null
    login.openLogin.mockClear()
  })

  it('opens the account page for a signed in user who is not a manager', async () => {
    const { wrapper } = await mountAt('/account', { manager: false, logged: true })

    expect(shownSection(wrapper)).toBe('account')
  })

  it('asks an anonymous visitor to sign in in place of the account page', async () => {
    const { wrapper } = await mountAt('/account', { manager: false, logged: false })

    expect(shownSection(wrapper), 'the account page must not render without a session').toBeNull()
    expect(wrapper.text()).toContain('Sign in to open My account')
    expect(login.openLogin).toHaveBeenCalledTimes(1)
  })

  it('does not call the account page a manager section', async () => {
    const { wrapper } = await mountAt('/account', { manager: false, logged: false })

    expect(wrapper.text()).not.toContain('permission nodes')
  })

  it('shows the account page after the visitor signs in, without a redirect', async () => {
    const { wrapper, router } = await mountAt('/account', { manager: false, logged: false })

    session.isLogged.value = true
    await flushPromises()

    expect(shownSection(wrapper)).toBe('account')
    expect(router.currentRoute.value.fullPath).toBe('/account')
  })

  it('moves to the account page when the server asks for a new password after the page loaded', async () => {
    const { router } = await mountAt('/browse/releases', { manager: false, logged: true })

    session.mustChange.value = true
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('account')
  })

  it('opens the account page at once when a new password is required on arrival', async () => {
    const { router } = await mountAt('/', { manager: false, logged: true, mustChange: true })

    expect(router.currentRoute.value.name).toBe('account')
  })
})

describe('IndexPage navigation', () => {
  beforeEach(() => {
    session.isManager = null
  })

  it('offers the rail with every section, in order, to a manager', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    const [, rail] = navigations(wrapper)

    expect(rail.findAll('a').map(link => link.text()), 'the rail has room for every section')
      .toEqual(SECTION_LABELS)
  })

  const railLabels = (wrapper) => navigations(wrapper)[1].findAll('a').map(link => link.text())

  it('hides Maintenance from a user without ingot.admin.maintenance', async () => {
    const { wrapper } = await mountAt('/', { logged: true, nodes: ['ingot.admin.audit'] })

    expect(railLabels(wrapper)).not.toContain('Maintenance')
  })

  it('shows Maintenance to a user with ingot.admin.maintenance', async () => {
    const { wrapper } = await mountAt('/', { logged: true, nodes: ['ingot.admin.maintenance'] })

    expect(railLabels(wrapper)).toEqual(['Overview', 'Search', 'Maintenance'])
  })

  it('hides Access from a user with no access node', async () => {
    const { wrapper } = await mountAt('/', { logged: true, nodes: ['ingot.admin.console', 'ingot.admin.audit'] })

    expect(railLabels(wrapper)).toEqual(['Overview', 'Search', 'Console', 'Audit'])
  })

  it('shows every section to a user with ingot.admin.*', async () => {
    const { wrapper } = await mountAt('/', { logged: true, nodes: ['ingot.admin.*'] })

    expect(railLabels(wrapper)).toEqual(SECTION_LABELS)
  })

  it('leaves out the sections a user cannot use from the bottom bar and from More', async () => {
    const { wrapper } = await mountAt('/', { logged: true, nodes: ['ingot.admin.console', 'ingot.admin.audit'] })

    const [bar] = navigations(wrapper)
    await bar.findAll('button').find(button => button.text() === 'More').trigger('click')

    expect(bar.findAll('a').map(link => link.text())).toEqual(['Overview', 'Search'])
    expect(wrapper.find('nav[aria-label="More sections"]').findAll('a').map(link => link.text())).toEqual(['Console', 'Audit'])
  })

  it('shows the public sections only to a user with repository nodes but no admin node', async () => {
    const { wrapper } = await mountAt('/', { logged: true, nodes: ['ingot.repository.releases.read'] })

    expect(railLabels(wrapper)).toEqual(['Overview', 'Search'])
  })

  it('shows the legacy manager every section when the session has no node list', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    expect(railLabels(wrapper)).toEqual(SECTION_LABELS)
  })

  it('moves a user without the node of the page to overview', async () => {
    const { router } = await mountAt('/maintenance', { logged: true, nodes: ['ingot.admin.audit'] })

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('keeps the daily sections in the bar and puts the rarer ones under More', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    const [bar] = navigations(wrapper)

    expect(bar.findAll('a').map(link => link.text())).toEqual(BAR_LABELS)
    expect(bar.findAll('button').map(button => button.text()), 'the bar has one More item for the overflow')
      .toEqual(['More'])
  })

  it('opens the overflow sections from More and closes the panel once one is chosen', async () => {
    const { wrapper, router } = await mountAt('/', { manager: true })

    const [bar] = navigations(wrapper)
    await bar.findAll('button').find(button => button.text() === 'More').trigger('click')

    const panel = wrapper.find('nav[aria-label="More sections"]')
    expect(panel.findAll('a').map(link => link.text())).toEqual(['Console', 'Maintenance', 'Audit', 'Settings'])

    await panel.findAll('a').find(link => link.text() === 'Audit').trigger('click')

    expect(wrapper.find('nav[aria-label="More sections"]').exists()).toBe(false)
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('audit')
  })

  it('offers a visitor Overview and Search, the two sections that need no sign in', async () => {
    const { wrapper } = await mountAt('/', { manager: false })

    expect(railLabels(wrapper)).toEqual(['Overview', 'Search'])
  })

  it('opens the search page from the rail without a sign in', async () => {
    const { wrapper, router } = await mountAt('/', { manager: false })

    await chooseLink(navigations(wrapper)[1], 'Search')

    expect(router.currentRoute.value.fullPath).toBe('/search')
    expect(shownSection(wrapper)).toBe('search')
  })

  it('marks the active section as the current page in both navigations', async () => {
    const { wrapper } = await mountAt('/access/users', { manager: true })

    const current = navigations(wrapper).map(nav =>
      nav.findAll('[aria-current="page"]').map(item => item.text()))

    expect(current).toEqual([['Access'], ['Access']])
  })

  it('keeps the browsed repository path on the overview link', async () => {
    const { wrapper } = await mountAt('/browse/org/repo', { manager: true })

    const overview = navigations(wrapper)[0].findAll('a').find(link => link.text() === 'Overview')
    const access = navigations(wrapper)[0].findAll('a').find(link => link.text() === 'Access')

    expect(overview.attributes('href'), 'returning to overview keeps the place in the browser').toBe('/browse/org/repo')
    expect(access.attributes('href'), 'the Access entry links to /access, which opens the user list').toBe('/access')
  })

  it('switches the section through the router when a rail item is chosen', async () => {
    const { wrapper, router } = await mountAt('/', { manager: true })

    await chooseLink(navigations(wrapper)[1], 'Console')

    expect(router.currentRoute.value.fullPath).toBe('/console')
    expect(shownSection(wrapper)).toBe('console')
  })

  it('shows the previous section again when the browser goes back', async () => {
    const { wrapper, router } = await mountAt('/dashboard', { manager: true })

    await router.push('/access/users')
    await flushPromises()
    await router.back()
    await flushPromises()

    expect(shownSection(wrapper), 'the back button must restore the section from the URL').toBe('dashboard')
  })
})

describe('IndexPage view state from the URL', () => {
  beforeEach(() => {
    session.isManager = null
  })

  it('hands the access view the tab and the name of the principal that is open', async () => {
    const { wrapper } = await mountAt('/access/machines/ci-bot', { manager: true })

    const access = wrapper.findComponent({ name: 'AccessStub' })

    expect(access.props('tab')).toBe('machines')
    expect(access.props('name')).toBe('ci-bot')
  })

  it('hands the access view no name on a list', async () => {
    const { wrapper } = await mountAt('/access/users', { manager: true })

    const access = wrapper.findComponent({ name: 'AccessStub' })

    expect(access.props('tab')).toBe('users')
    expect(access.props('name')).toBeUndefined()
  })

  it('hands the access view the check tab for the check address', async () => {
    const { wrapper } = await mountAt('/access/check?principal=alice', { manager: true })

    const access = wrapper.findComponent({ name: 'AccessStub' })

    expect(access.props('tab')).toBe('check')
  })

  it('follows the old tokens address to the machine accounts', async () => {
    const { wrapper, router } = await mountAt('/tokens/ci-bot', { manager: true })

    expect(router.currentRoute.value.fullPath).toBe('/access/machines/ci-bot')
    expect(wrapper.findComponent({ name: 'AccessStub' }).props('tab')).toBe('machines')
  })

  it('hands the console the shown levels and the filter from the URL', async () => {
    const { wrapper } = await mountAt('/console?level=info,error&q=boom', { manager: true })

    const console = wrapper.findComponent({ name: 'ConsoleStub' })

    expect(console.props('level')).toBe('info,error')
    expect(console.props('search')).toBe('boom')
  })

  it('leaves the console level unset while every level is shown', async () => {
    const { wrapper } = await mountAt('/console', { manager: true })

    expect(wrapper.findComponent({ name: 'ConsoleStub' }).props('level')).toBeUndefined()
  })

  it('writes a level switch into the URL', async () => {
    const { wrapper, router } = await mountAt('/console', { manager: true })

    wrapper.findComponent({ name: 'ConsoleStub' }).vm.$emit('update:level', 'info')
    await flushPromises()

    expect(router.currentRoute.value.query.level).toBe('info')
  })

  it('writes an empty level when every level is hidden, which differs from showing them all', async () => {
    const { wrapper, router } = await mountAt('/console', { manager: true })

    wrapper.findComponent({ name: 'ConsoleStub' }).vm.$emit('update:level', '')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ level: '' })
  })

  it('writes the filter text into the URL and drops it when cleared', async () => {
    const { wrapper, router } = await mountAt('/console?q=boom', { manager: true })
    const console = wrapper.findComponent({ name: 'ConsoleStub' })

    console.vm.$emit('update:search', 'bang')
    await flushPromises()
    expect(router.currentRoute.value.query.q).toBe('bang')

    console.vm.$emit('update:search', '')
    await flushPromises()
    expect(router.currentRoute.value.query.q).toBeUndefined()
  })

  it('hands the settings view the domain, collection and entry of an entry URL', async () => {
    const { wrapper } = await mountAt('/settings/maven/repositories/releases', { manager: true })

    const settings = wrapper.findComponent({ name: 'SettingsStub' })

    expect(settings.props('domain')).toBe('maven')
    expect(settings.props('collection')).toBe('repositories')
    expect(settings.props('entry')).toBe('releases')
  })

  it('moves to the domain the settings view chose and drops the entry that was open', async () => {
    const { wrapper, router } = await mountAt('/settings/maven/repositories/releases', { manager: true })

    wrapper.findComponent({ name: 'SettingsStub' }).vm.$emit('update:domain', 'proxy')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/settings/proxy')
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

  it('publishes the bar height to a visitor, who has the Overview and Search sections', async () => {
    const { wrapper } = await mountAt('/', { manager: false })

    expect(rootBarHeight(), 'a bar with two destinations still takes its height').toBe('80px')
    wrapper.unmount()
  })

  it('removes the variable when the page unmounts', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    wrapper.unmount()

    expect(rootBarHeight(), 'a page that is gone must not leave an offset behind').toBe('')
  })
})
