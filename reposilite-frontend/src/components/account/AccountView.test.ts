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
import { computed, ref } from 'vue'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AccountView from './AccountView.vue'

type AccessOutcome = 'ready' | 'unavailable' | 'pending'

type SessionOptions = {
  mode?: string
  mustChange?: boolean
  admin?: boolean
  teams?: string[]
  access?: AccessOutcome
  nodes?: string[]
  denials?: string[]
  grants?: { team: string; repository: string | null; role: string; pathPrefix?: string | null }[]
  tokenNarrowed?: boolean
}

// The session is replaced by plain refs, so each test decides who is signed in and how.
const holder = vi.hoisted(() => ({ session: null as FakeSession | null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error: { response?: { data?: { message?: string } }; message?: string } | undefined) =>
    error?.response?.data?.message ?? error?.message ?? String(error)
}))

const ALICE = { id: 7, kind: 'USER', name: 'alice', displayName: 'Alice Example' }

const SECTIONS = ['Profile', 'Your access', 'Sign-in and security', 'My tokens']

// The answer of GET /api/identity/me/access for the signed in principal, built per test.
const summaryOf = ({ admin = false, teams = ['developers'], nodes = ['ingot.repository.releases.read'], denials = [], grants = [], tokenNarrowed = false }: SessionOptions) => ({
  principal: { name: 'alice', displayName: 'Alice Example', kind: 'USER', providers: ['local'] },
  instanceAdmin: admin,
  teams: teams.map(name => ({ name, parents: ['everyone'] })),
  nodes,
  denials: denials.map(node => ({ node, overridden: false })),
  grants: grants.length > 0 ? grants : [{ team: 'developers', repository: 'releases', role: 'READ', pathPrefix: null }],
  tokenNarrowed
})

const fakeSession = (options: SessionOptions = {}) => {
  const { mode = 'cookie', admin = false, teams = ['developers'], access = 'ready' } = options
  const accessAnswer = access === 'ready'
    ? Promise.resolve({ data: summaryOf({ ...options, admin, teams }) })
    : access === 'unavailable'
      ? Promise.reject(new Error('404'))
      : new Promise(() => {})
  const me = {
    changePassword: vi.fn().mockResolvedValue({ status: 204 }),
    factors: {
      list: vi.fn().mockResolvedValue({ data: [] }),
      startAuthenticator: vi.fn(),
      confirmAuthenticator: vi.fn(),
      startPasskey: vi.fn(),
      registerPasskey: vi.fn(),
      rename: vi.fn(),
      remove: vi.fn()
    },
    access: vi.fn(() => accessAnswer),
    tokens: {
      list: vi.fn().mockResolvedValue({ data: [] }),
      create: vi.fn(),
      rotate: vi.fn(),
      revoke: vi.fn()
    }
  }
  const details = ref({ teams, instanceAdmin: admin, principal: ALICE })
  return {
    me,
    principal: ref(ALICE),
    details,
    instanceAdmin: computed(() => details.value.instanceAdmin === true),
    authMode: ref(mode),
    mustChangePassword: ref(options.mustChange ?? false),
    passwordMinLength: ref(12),
    client: ref({ identity: { me } })
  }
}

type FakeSession = ReturnType<typeof fakeSession>

describe('AccountView', () => {
  let wrapper: VueWrapper

  const mountView = async (options?: SessionOptions) => {
    holder.session = fakeSession(options)
    // The Explain links of Your access are router links, so the page is mounted with a router like the dashboard's.
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/access/check', name: 'access-check', component: { render: () => null } },
        { path: '/:rest(.*)', component: { render: () => null } }
      ]
    })
    wrapper = mount(AccountView, { attachTo: document.body, global: { plugins: [router] } }) as VueWrapper
    await flushPromises()
    return holder.session
  }

  const sectionOf = (headingId: string) => wrapper.find(`[aria-labelledby="${headingId}"]`)

  afterEach(() => {
    wrapper?.unmount()
    document.body.innerHTML = ''
  })

  beforeEach(() => {
    holder.session = null
  })

  it('renders the page on the page shell, with no centring and no width cap on its container', async () => {
    await mountView()

    const page = wrapper.findComponent({ name: 'MdPage' }) as VueWrapper
    expect(page.exists(), 'the page root is an MdPage').toBe(true)
    const classes = Array.from((page.element as HTMLElement).classList)
    expect(classes, 'the page keeps the shared container gutter').toContain('container')
    expect(classes.filter(name => name === 'mx-auto' || name.startsWith('max-w-')), 'the page container is neither centred nor capped').toEqual([])
  })

  it('gives the sign-in and security form block the reading measure, left aligned', async () => {
    await mountView()

    const block = wrapper.get('[data-test="security-section"]').element as HTMLElement
    expect(Array.from(block.classList), 'the form block is capped at the reading measure').toContain('max-w-md-reading')
    expect(block.contains(wrapper.get('#account-current-password').element), 'the password form sits in that block').toBe(true)
    expect(Array.from(block.classList), 'the block is not centred').not.toContain('mx-auto')
  })

  it('lays the cards out in one column, two from 840 pixels and three from 1240 pixels', async () => {
    await mountView()

    const grid = (wrapper.find('[data-test="security-section"]').element as HTMLElement).parentElement as HTMLElement
    expect(Array.from(grid.classList), 'one column in a compact window, two from the expanded window, three from the large window').toEqual(
      expect.arrayContaining(['grid', 'grid-cols-1', 'expanded:grid-cols-2', 'large:grid-cols-3'])
    )
  })

  it('spans the tokens card over the full width below the two cards', async () => {
    await mountView()

    const tokens = wrapper.find('section[aria-labelledby="token-list-title"]').element.parentElement as HTMLElement
    expect(Array.from(tokens.classList), 'the token table takes the whole row').toContain('col-span-full')
  })

  it('caps the password form fields at the form width, not the page', async () => {
    await mountView()

    const form = wrapper.find('#account-current-password').element.closest('form') as HTMLElement
    expect(Array.from(form.classList), 'the fields of a wide card stop at the form width').toContain('max-w-xl')
  })

  it('titles the page My account with its only level one heading', async () => {
    await mountView()

    expect(wrapper.findAll('h1').map(heading => heading.text()), 'the page has exactly one level one heading').toEqual(['My account'])
  })

  it('shows the four sections in order: Profile, Your access, Sign-in and security, My tokens', async () => {
    await mountView()

    const headings = wrapper.findAll('h3').map(heading => heading.text()).filter(text => SECTIONS.includes(text))

    expect(headings, 'each section has its heading, once, in this order').toEqual(SECTIONS)
  })

  it('shows each profile value once: the display name, the kind and the sign-in sources, with no team or admin tag', async () => {
    await mountView({ admin: true, teams: ['developers', 'release'] })

    const profile = sectionOf('profile-title')
    expect(profile.findAll('[data-test="team"]'), 'the teams are in Your access').toHaveLength(0)
    expect(profile.find('[data-test="instance-admin"]').exists(), 'the admin tag is in Your access').toBe(false)
    expect(profile.text()).toContain('Alice Example')
  })

  it('draws the teams and the instance admin tag in Your access', async () => {
    await mountView({ admin: true, teams: ['developers', 'release'] })

    const access = sectionOf('access-title')
    const teams = access.findAll('[data-test="team"]').map(chip => chip.text())
    expect(teams, 'each team is named in Your access').toEqual(expect.arrayContaining([expect.stringContaining('developers'), expect.stringContaining('release')]))
    expect(access.find('[data-test="instance-admin"]').exists()).toBe(true)
  })

  it('shows the teams with their direct parents, which is what the rights come through', async () => {
    await mountView()

    expect(wrapper.find('[data-test="team"]').text(), 'the team names its parent').toContain('inherits from everyone')
  })

  it('shows the password form in a cookie session', async () => {
    await mountView({ mode: 'cookie' })

    expect(wrapper.find('#account-current-password').exists()).toBe(true)
  })

  it('hides the password form in a token session', async () => {
    await mountView({ mode: 'token' })

    expect(wrapper.find('#account-current-password').exists(), 'a token session has no password').toBe(false)
    expect(wrapper.text()).toContain('signed in with an access token')
  })

  it('places the second factors card inside the sign-in and security section, below the password card', async () => {
    await mountView()

    const card = wrapper.findComponent({ name: 'SecondFactorsCard' })
    const section = sectionOf('security-title').element as HTMLElement

    expect(card.exists(), 'the second factors card is on the page').toBe(true)
    expect(section.contains(card.element as Node), 'the card sits in the sign-in and security section').toBe(true)
    const password = section.querySelector('#account-current-password') as HTMLElement
    expect(password.compareDocumentPosition(card.element as HTMLElement) & Node.DOCUMENT_POSITION_FOLLOWING, 'the card follows the password card').toBeTruthy()
  })

  it('passes the signed in account name to the second factors card', async () => {
    await mountView()

    expect(wrapper.findComponent({ name: 'SecondFactorsCard' }).props('accountName'), 'the passkey is named after the account').toBe('alice')
  })

  it('gives the second factors card the identity endpoints, so it loads the factors of the signed in user', async () => {
    const session = await mountView()

    expect(session.me.factors.list, 'the card asks for the factors once, when it opens').toHaveBeenCalledTimes(1)
  })

  it('keeps the slot for two-step sign-in around the card, named for the change that fills it', async () => {
    await mountView()

    const slot = wrapper.find('[data-test="sign-in-slot"]')

    expect(slot.attributes('data-ingot-slot'), 'the slot is named for the change that fills it').toBe('add-multi-factor-authentication')
    expect(slot.findComponent({ name: 'SecondFactorsCard' }).exists(), 'the card fills the slot').toBe(true)
    expect(slot.text(), 'the placeholder note is gone').not.toContain('Two-step sign-in is not set up')
  })

  it('shows no banner when no new password is required', async () => {
    await mountView()

    expect(wrapper.text()).not.toContain('Set a new password before you continue')
  })

  it('shows the banner and puts the sign-in and security section at the top when a new password is required', async () => {
    await mountView({ mustChange: true })

    const sections = wrapper.findAll('section').map(section => section.attributes('aria-labelledby'))

    expect(wrapper.find('[data-test="must-change"]').text()).toContain('Set a new password before you continue')
    expect(sections.indexOf('security-title'), 'the password card comes before the profile').toBeLessThan(sections.indexOf('profile-title'))
  })

  it('keeps the sign-in and security section below the profile when no change is required', async () => {
    await mountView()

    const sections = wrapper.findAll('section').map(section => section.attributes('aria-labelledby'))

    expect(sections.indexOf('profile-title')).toBeLessThan(sections.indexOf('security-title'))
  })

  it('lifts the requirement once the password is changed', async () => {
    const session = await mountView({ mustChange: true })

    await wrapper.find('#account-current-password').setValue('old-password-12')
    await wrapper.find('#account-new-password').setValue('new-password-123')
    await wrapper.find('#account-confirm-password').setValue('new-password-123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(session.me.changePassword).toHaveBeenCalledTimes(1)
    expect(session.mustChangePassword.value, 'other pages open again').toBe(false)
    expect(wrapper.find('[data-test="must-change"]').exists()).toBe(false)
  })

  it('lists the tokens of the signed in user through the self service endpoints', async () => {
    const session = await mountView()

    expect(session.me.tokens.list).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('My tokens')
  })

  it('asks for the own access summary with no principal, so the page takes none from the address', async () => {
    const session = await mountView()

    expect(session.me.access, 'the summary is requested once').toHaveBeenCalledTimes(1)
    expect(session.me.access).toHaveBeenCalledWith()
  })

  it('states the allowed rights in plain words and lists the denials apart', async () => {
    await mountView({ nodes: ['ingot.repository.releases.read'], denials: ['ingot.admin.users'] })

    const allowed = wrapper.findAll('[data-test="allowed-permission"]').map(item => item.text())
    const denied = wrapper.findAll('[data-test="denied-permission"]').map(item => item.text())

    expect(allowed, 'the allowed right is in plain words').toEqual(expect.arrayContaining([expect.stringContaining('Read from releases')]))
    expect(denied, 'the denial is listed apart').toEqual([expect.stringContaining('Manage users')])
  })

  it('names the repository access with its role and the team it comes through', async () => {
    await mountView()

    const scope = wrapper.find('[data-test="scope"]').text()

    expect(scope).toContain('Read from releases')
    expect(scope, 'the team the access comes through').toContain('developers')
  })

  it('shows a grant without a repository as one plain line, not as a dropped row', async () => {
    await mountView({ grants: [{ team: 'ops', repository: null, role: 'ADMIN' }] })

    expect(wrapper.find('[data-test="unknown"]').text()).toContain('ops holds ADMIN without a repository')
  })

  it('offers only Explain links in Your access, no input and no button', async () => {
    await mountView({ nodes: ['ingot.admin.users'], denials: ['ingot.admin.teams'] })

    const access = sectionOf('access-title')
    const links = access.findAll('a')

    expect(access.findAll('input, button, select, textarea'), 'the section is read only').toHaveLength(0)
    expect(links.length, 'each right has an Explain link').toBeGreaterThan(0)
    links.forEach(link => expect(link.text()).toBe('Explain'))
  })

  it('links every Explain to the how-to page, also for a principal who may use the Access area', async () => {
    await mountView({ nodes: ['ingot.admin.users'] })

    const hrefs = sectionOf('access-title').findAll('a').map(link => link.attributes('href') ?? '')

    expect(hrefs.length).toBeGreaterThan(0)
    hrefs.forEach(href => expect(href, 'the how-to page is the target, whatever the nodes').toContain('check-access'))
  })

  it('links an Explain to the how-to page for a principal who may not use the Access area', async () => {
    await mountView({ nodes: ['ingot.repository.releases.read'] })

    const hrefs = sectionOf('access-title').findAll('a').map(link => link.attributes('href') ?? '')

    expect(hrefs.length).toBeGreaterThan(0)
    hrefs.forEach(href => expect(href, 'the how-to page is the target').toContain('check-access'))
  })

  it('names a token session as one that narrows the list', async () => {
    await mountView({ tokenNarrowed: true })

    expect(sectionOf('access-title').text()).toContain('This access token narrows the list')
  })

  it('says so when the server does not have the access summary', async () => {
    await mountView({ access: 'unavailable' })

    expect(sectionOf('access-title').text()).toContain('The access summary is not available on this server')
  })

  it('shows a loading line in Your access while the summary is on its way, and the rest of the page does not wait', async () => {
    await mountView({ access: 'pending' })

    expect(sectionOf('access-title').text()).toContain('Loading your access')
    expect(wrapper.find('[data-test="sign-in-slot"]').exists(), 'the sign-in section is drawn meanwhile').toBe(true)
  })
})
