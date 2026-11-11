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
const session = vi.hoisted(() => ({ isManager: null, isLogged: null, mustChange: null, can: null, client: null }))
const login = vi.hoisted(() => ({ openLogin: vi.fn() }))

vi.mock('../store/session', () => ({
  useSession: () => ({ isManager: session.isManager, isLogged: session.isLogged, mustChangePassword: session.mustChange, can: session.can, client: session.client })
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
        selectedTab: { type: String, default: undefined },
        qualifier: { type: Object, default: undefined },
        search: { type: String, default: undefined },
        tab: { type: String, default: undefined },
        name: { type: String, default: undefined },
        level: { type: String, default: undefined },
        domain: { type: String, default: undefined },
        collection: { type: String, default: undefined },
        entry: { type: String, default: undefined }
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
vi.mock('../components/dashboard/AnalyticsView.vue', sectionStub('AnalyticsStub', 'analytics'))
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
const mountAt = async (url, { manager = false, logged = manager, mustChange = false, nodes, client = null } = {}) => {
  session.client = client ? ref(client) : null
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

// The four entries of the rail, which the bottom bar shows too for an administrator. Administration holds the rest.
const SECTION_LABELS = ['Browse', 'Search', 'Dashboard', 'Administration']

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
    ['/analytics', 'analytics'],
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

  it('keeps a visitor whose sign in does not grant the permission at the address, with the no access state', async () => {
    const { wrapper, router } = await mountAt('/access/users', { manager: false, logged: false })

    session.isLogged.value = true
    await flushPromises()

    expect(router.currentRoute.value.fullPath, 'no redirect to overview any more').toBe('/access/users')
    expect(wrapper.text()).toContain('You do not have access to this page')
  })

  it('shows the section once the visitor has become a manager', async () => {
    const { wrapper } = await mountAt('/access/users', { manager: false, logged: false })

    session.isManager.value = true
    await flushPromises()

    expect(shownSection(wrapper)).toBe('access')
  })
})

describe('IndexPage no access state', () => {
  beforeEach(() => {
    session.isManager = null
    login.openLogin.mockClear()
  })

  it('keeps a signed in visitor without the console node at /console and says that there is no access', async () => {
    const { wrapper, router } = await mountAt('/console', { logged: true, nodes: ['ingot.admin.audit'] })

    expect(router.currentRoute.value.fullPath).toBe('/console')
    expect(wrapper.text()).toContain('You do not have access to this page')
    expect(shownSection(wrapper), 'the console must not render').toBeNull()
  })

  it('names the page that is denied', async () => {
    const { wrapper } = await mountAt('/console', { logged: true, nodes: ['ingot.admin.audit'] })

    expect(wrapper.find('[data-test="no-access"] h2').text()).toBe('Console')
  })

  it('offers a link to Browse and does not navigate to overview by itself', async () => {
    const { wrapper, router } = await mountAt('/console', { logged: true, nodes: ['ingot.admin.audit'] })
    const push = vi.spyOn(router, 'push')
    const replace = vi.spyOn(router, 'replace')

    const link = wrapper.find('[data-test="no-access"]').findAll('a').find(anchor => anchor.text() === 'Browse')

    expect(link.attributes('href')).toBe('/')
    expect(push).not.toHaveBeenCalled()
    expect(replace).not.toHaveBeenCalled()
  })

  it('shows the sign in prompt, and not the no access message, to an anonymous visitor', async () => {
    const { wrapper } = await mountAt('/console', { manager: false, logged: false })

    expect(wrapper.text()).toContain('Sign in to open Console')
    expect(wrapper.text()).not.toContain('You do not have access to this page')
  })

  it('shows no toast for the denial', async () => {
    const { snackbar } = await import('../store/snackbar')
    await mountAt('/console', { logged: true, nodes: ['ingot.admin.audit'] })

    expect(snackbar.entries.value.map(entry => entry.message)).toEqual([])
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

  const railLabels = (wrapper) => navigations(wrapper)[1].findAll('a').map(link => link.text())
  const barLabels = (wrapper) => navigations(wrapper)[0].findAll('a').map(link => link.text())
  const barButtons = (wrapper) => navigations(wrapper)[0].findAll('button')

  it('offers the rail with the four entries, in order, to a manager', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    expect(railLabels(wrapper), 'Browse, Search, Dashboard and Administration').toEqual(SECTION_LABELS)
  })

  it('draws the rail without group headings, since the four entries form one block', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    expect(navigations(wrapper)[1].findAll('[role="group"]')).toHaveLength(0)
  })

  it('hides Dashboard from a user without ingot.admin.dashboard and keeps Administration for maintenance', async () => {
    const { wrapper } = await mountAt('/', { logged: true, nodes: ['ingot.admin.maintenance'] })

    expect(railLabels(wrapper)).toEqual(['Browse', 'Search', 'Administration'])
  })

  it('shows every entry to a user with ingot.admin.*', async () => {
    const { wrapper } = await mountAt('/', { logged: true, nodes: ['ingot.admin.*'] })

    expect(railLabels(wrapper)).toEqual(SECTION_LABELS)
  })

  it('shows Administration to a visitor with the audit node alone, and the entry opens the audit log', async () => {
    const { wrapper, router } = await mountAt('/', { logged: true, nodes: ['ingot.admin.audit'] })

    expect(railLabels(wrapper)).toEqual(['Browse', 'Search', 'Administration'])

    await chooseLink(navigations(wrapper)[1], 'Administration')

    expect(router.currentRoute.value.fullPath, 'the entry opens the one page the visitor may open').toBe('/audit')
    expect(shownSection(wrapper)).toBe('audit')
  })

  it('hides Administration from a visitor with no Administration page, and keeps Browse and Search', async () => {
    const { wrapper } = await mountAt('/', { logged: true, nodes: ['ingot.repository.releases.read'] })

    expect(railLabels(wrapper)).toEqual(['Browse', 'Search'])
  })

  it('shows an anonymous visitor only Browse and Search in the rail', async () => {
    const { wrapper } = await mountAt('/', { manager: false })

    expect(railLabels(wrapper)).toEqual(['Browse', 'Search'])
  })

  it('shows an anonymous visitor none of the admin entries in the rail or the bar', async () => {
    const { wrapper } = await mountAt('/', { manager: false })

    const shown = navigations(wrapper).flatMap(nav => nav.findAll('a').map(link => link.text()))

    for (const label of ['Dashboard', 'Administration', 'Analytics', 'Access', 'Console', 'Maintenance', 'Audit log']) {
      expect(shown, `${label} needs a sign in`).not.toContain(label)
    }
  })

  it('puts the four entries in the bottom bar, with no More item, for an administrator', async () => {
    const { wrapper } = await mountAt('/', { manager: true })

    expect(barLabels(wrapper)).toEqual(SECTION_LABELS)
    expect(barButtons(wrapper), 'nothing is left over for More').toHaveLength(0)
  })

  it('puts Browse, Search and Administration in the bottom bar for the audit node alone, without More', async () => {
    const { wrapper } = await mountAt('/', { logged: true, nodes: ['ingot.admin.audit'] })

    expect(barLabels(wrapper)).toEqual(['Browse', 'Search', 'Administration'])
    expect(barButtons(wrapper)).toHaveLength(0)
  })

  it('puts only Browse and Search in the bottom bar for an anonymous visitor, without More', async () => {
    const { wrapper } = await mountAt('/', { manager: false })

    expect(barLabels(wrapper)).toEqual(['Browse', 'Search'])
    expect(barButtons(wrapper)).toHaveLength(0)
  })

  it.each([
    ['/access/users'],
    ['/audit'],
    ['/console'],
    ['/maintenance'],
    ['/analytics'],
    ['/settings'],
  ])('marks Administration as the current entry of both navigations at %s', async (url) => {
    const { wrapper } = await mountAt(url, { manager: true })

    const current = navigations(wrapper).map(nav => nav.findAll('[aria-current="page"]').map(item => item.text()))

    expect(current).toEqual([['Administration'], ['Administration']])
  })

  it('keeps the browsed repository path on the Browse link and opens the hub from Administration', async () => {
    const { wrapper } = await mountAt('/browse/org/repo', { manager: true })

    const browse = navigations(wrapper)[0].findAll('a').find(link => link.text() === 'Browse')
    const administration = navigations(wrapper)[0].findAll('a').find(link => link.text() === 'Administration')

    expect(browse.attributes('href'), 'returning to Browse keeps the place in the browser').toBe('/browse/org/repo')
    expect(administration.attributes('href'), 'an administrator opens the hub').toBe('/settings')
  })

  it('switches the section through the router when a rail item is chosen', async () => {
    const { wrapper, router } = await mountAt('/', { manager: true })

    await chooseLink(navigations(wrapper)[1], 'Dashboard')

    expect(router.currentRoute.value.fullPath).toBe('/dashboard')
    expect(shownSection(wrapper)).toBe('dashboard')
  })

  it('opens the search page from the rail without a sign in', async () => {
    const { wrapper, router } = await mountAt('/', { manager: false })

    await chooseLink(navigations(wrapper)[1], 'Search')

    expect(router.currentRoute.value.fullPath).toBe('/search')
    expect(shownSection(wrapper)).toBe('search')
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

describe('IndexPage Administration tree', () => {
  beforeEach(() => {
    session.isManager = null
  })

  const treeOf = (wrapper) => wrapper.find('nav[aria-label="Administration"]')
  const treeGroups = (wrapper) => treeOf(wrapper).findAll('[role="group"]')

  it.each([
    ['/audit', 'Audit log'],
    ['/console', 'Console'],
    ['/maintenance', 'Maintenance'],
    ['/analytics', 'Analytics'],
  ])('shows the Administration tree beside %s, with its page marked as the current page', async (url, label) => {
    const { wrapper } = await mountAt(url, { manager: true })

    expect(treeOf(wrapper).exists(), 'the page has the side navigation of Administration').toBe(true)
    expect(treeOf(wrapper).findAll('[aria-current="page"]').map(item => item.text())).toEqual([label])
  })

  it('lists the Administration pages in the groups Repository, Security and Support', async () => {
    const { wrapper } = await mountAt('/audit', { manager: true })

    expect(treeGroups(wrapper).map(group => group.attributes('aria-label')), 'System is left out, it has no page here')
      .toEqual(['Repository', 'Security', 'Support'])
    expect(treeGroups(wrapper).map(group => group.findAll('a').map(link => link.text()))).toEqual([
      ['Repositories'],
      ['Users', 'Machine accounts', 'Teams', 'SSO', 'LDAP', 'Security settings', 'Permission check', 'Audit log'],
      ['Console', 'Maintenance', 'Analytics']
    ])
  })

  it('lists only the Administration pages the visitor may open', async () => {
    const { wrapper } = await mountAt('/audit', { logged: true, nodes: ['ingot.admin.audit'] })

    expect(treeGroups(wrapper).map(group => group.attributes('aria-label'))).toEqual(['Security'])
    expect(treeGroups(wrapper)[0].findAll('a').map(link => link.text())).toEqual(['Audit log'])
  })

  it('opens another Administration page from the tree', async () => {
    const { wrapper, router } = await mountAt('/audit', { manager: true })

    await chooseLink(treeOf(wrapper), 'Console')

    expect(router.currentRoute.value.fullPath).toBe('/console')
    expect(shownSection(wrapper)).toBe('console')
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
    // A maven repositories address now moves to the Repositories page, so a domain without that move keeps the entry.
    const { wrapper } = await mountAt('/settings/cleanup/policies/nightly', { manager: true })

    const settings = wrapper.findComponent({ name: 'SettingsStub' })

    expect(settings.props('domain')).toBe('cleanup')
    expect(settings.props('collection')).toBe('policies')
    expect(settings.props('entry')).toBe('nightly')
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

  it('publishes the bar height to a visitor, who has the Repositories and Search sections', async () => {
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

describe('IndexPage main landmark', () => {
  beforeEach(() => {
    session.isManager = null
  })

  it('wraps the page content in exactly one main landmark that takes focus programmatically', async () => {
    const { wrapper } = await mountAt('/dashboard', { manager: true })

    expect(wrapper.findAll('main#main[tabindex="-1"]'), 'one main element with the skip link target').toHaveLength(1)
  })

  it('keeps the page header outside the main landmark', async () => {
    const { wrapper } = await mountAt('/dashboard', { manager: true })
    const main = wrapper.find('main')

    expect(wrapper.find('header').exists(), 'the header is on the page').toBe(true)
    expect(main.find('header').exists(), 'the header is not inside main').toBe(false)
  })

  it('keeps the shown section inside the main landmark', async () => {
    const { wrapper } = await mountAt('/dashboard', { manager: true })

    expect(wrapper.find('main [data-test="section"]').exists(), 'the content is inside main').toBe(true)
  })
})

// A client that lists two settings domains and gives their titles, as the server does.
const clientWithDomains = () => ({
  settings: {
    domains: vi.fn(async () => ({ data: ['web', 'maven'] })),
    schema: vi.fn(async (domain) => ({ data: { title: domain === 'web' ? 'Web' : 'Maven' } }))
  }
})

const treeLabels = (wrapper) => wrapper.findAll('nav[aria-label="Administration"] a').map(link => link.text())

describe('IndexPage Administration tree', () => {
  beforeEach(() => {
    session.isManager = null
  })

  it('shows the same tree, with the settings domains, on every Administration page', async () => {
    const trees = {}
    for (const url of ['/console', '/audit', '/maintenance', '/analytics']) {
      const { wrapper } = await mountAt(url, { manager: true, client: clientWithDomains() })
      trees[url] = treeLabels(wrapper)
    }

    expect(trees['/console'], 'the settings domains are in the tree of the console page').toContain('Web')
    expect(trees['/audit'], 'the tree of the audit log equals the tree of the console').toEqual(trees['/console'])
    expect(trees['/maintenance'], 'the tree of maintenance equals the tree of the console').toEqual(trees['/console'])
    expect(trees['/analytics'], 'the tree of analytics equals the tree of the console').toEqual(trees['/console'])
  })

  it('marks only the Audit log entry on /audit, and not the audit settings domain', async () => {
    const { wrapper } = await mountAt('/audit', { manager: true, client: clientWithDomains() })

    const marked = wrapper.findAll('nav[aria-label="Administration"] a').filter(link => link.attributes('aria-current') === 'page')
    expect(marked.map(link => link.text())).toEqual(['Audit log'])
  })

  it('draws one Administration heading on the console page, and the page description on maintenance', async () => {
    const { wrapper: consolePage } = await mountAt('/console', { manager: true, client: clientWithDomains() })
    expect(consolePage.findAll('h1').map(heading => heading.text())).toEqual(['Administration'])

    const { wrapper: maintenance } = await mountAt('/maintenance', { manager: true, client: clientWithDomains() })
    expect(maintenance.text()).toContain('Storage usage, the search index and cleanup for this Ingot instance.')
    expect(maintenance.text(), 'the subtitle does not speak of managers').not.toContain('manager')
  })

  it('requests the settings domains once, when the visitor goes on from the console to the settings hub', async () => {
    const client = clientWithDomains()
    const { router } = await mountAt('/console', { manager: true, client })

    await router.push('/settings')
    await flushPromises()

    expect(client.settings.domains, 'one request for the session').toHaveBeenCalledTimes(1)
  })

  it('makes no request for the settings domains to a visitor without the settings node', async () => {
    const client = clientWithDomains()
    await mountAt('/audit', { logged: true, nodes: ['ingot.admin.audit'], client })

    expect(client.settings.domains, 'the visitor without the node causes no request that would answer 403').not.toHaveBeenCalled()
  })
})
