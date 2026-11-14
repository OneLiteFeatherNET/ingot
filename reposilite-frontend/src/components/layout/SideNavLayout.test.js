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

import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import SideNavLayout from './SideNavLayout.vue'
import { treeObserverKey } from '../../helpers/useTreeFits'

const shell = { render: () => null }

const routes = [
  { name: 'access', path: '/access', component: shell },
  { name: 'access-users', path: '/access/users', component: shell },
  { name: 'access-machines', path: '/access/machines', component: shell },
  { name: 'access-teams', path: '/access/teams', component: shell },
  { name: 'access-sso', path: '/access/sso', component: shell },
  { name: 'access-ldap', path: '/access/ldap', component: shell }
]

// The fixture is built per test, so no test can change what another one reads.
const fixtureGroups = () => [
  {
    heading: 'Accounts',
    links: [
      { key: 'users', label: 'Users', to: { name: 'access-users' } },
      { key: 'machines', label: 'Machine accounts', to: { name: 'access-machines' } },
      { key: 'teams', label: 'Teams', to: { name: 'access-teams' } }
    ]
  },
  {
    heading: 'Sign in',
    links: [
      { key: 'sso', label: 'SSO', to: { name: 'access-sso' } },
      { key: 'ldap', label: 'LDAP', to: { name: 'access-ldap' } }
    ]
  }
]

const SIDE_NAV = '[aria-label="Access sections"]'

describe('SideNavLayout', () => {
  // A fresh router per test, on memory history, opened at the address of the page under test.
  const mountLayout = async (props = {}, url = '/access/users') => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push(url)
    await router.isReady()
    return mount(SideNavLayout, {
      props: {
        label: 'Access sections',
        groups: fixtureGroups(),
        ...props
      },
      slots: { default: '<p class="page-body">Page body</p>' },
      global: { plugins: [router] }
    })
  }

  it('renders the links of each group as a list, named by the visible heading of that group', async () => {
    const wrapper = await mountLayout({ current: 'users' })

    const lists = wrapper.findAll('ul[aria-labelledby]')
    const names = lists.map(list => wrapper.get(`#${list.attributes('aria-labelledby')}`).text())
    expect(names, 'one list per heading, named by the heading').toEqual(['Accounts', 'Sign in'])
    expect(lists[0].findAll('a').map(link => link.text())).toEqual(['Users', 'Machine accounts', 'Teams'])
    expect(lists[1].findAll('a').map(link => link.text())).toEqual(['SSO', 'LDAP'])
  })

  it('draws no group heading as a heading element, and no group without a visible name', async () => {
    const wrapper = await mountLayout({ current: 'users' })

    expect(wrapper.findAll('h1, h2, h3, h4, h5, h6'), 'the group headings are paragraphs').toEqual([])
    expect(wrapper.findAll('[role="group"]'), 'the lists are named by aria-labelledby, not by aria-label').toEqual([])
  })

  it('marks only the current link with aria-current page', async () => {
    const wrapper = await mountLayout({ current: 'machines' })

    const marks = wrapper.findAll(`${SIDE_NAV} a`).map(link => link.attributes('aria-current') ?? null)

    expect(marks, 'the link of the open page is the only one marked').toEqual([null, 'page', null, null, null])
  })

  it('makes each link a real link to the address of its route', async () => {
    const wrapper = await mountLayout({ current: 'users' })

    const hrefs = wrapper.findAll(`${SIDE_NAV} a`).map(link => link.attributes('href'))

    expect(hrefs, 'every link is an address the browser can open in a new tab').toEqual([
      '/access/users', '/access/machines', '/access/teams', '/access/sso', '/access/ldap'
    ])
  })

  it('draws the side navigation 208 pixels wide beside the page, with no scroll box of its own', async () => {
    const wrapper = await mountLayout({ current: 'users', narrow: false })

    const classes = wrapper.find(SIDE_NAV).classes()
    expect(classes, 'the column is 208 pixels wide').toContain('w-52')
    expect(classes, 'the column stops one rem below the top bar when it is sticky').toContain('top-[calc(var(--spacing-md-top-bar)+--spacing(4))]')
    expect(classes.some(name => name.startsWith('max-h-')), 'no maximum height that would make a scroll box').toBe(false)
    expect(classes, 'a tall column scrolls with the page, not inside itself').not.toContain('overflow-y-auto')
  })

  it('does not make the index sticky on narrow widths', async () => {
    const wrapper = await mountLayout({ current: undefined, narrow: true })

    expect(wrapper.find(SIDE_NAV).classes()).not.toContain('sticky')
  })

  it('lays the side navigation and the page in a row only from 1240 pixels', async () => {
    const wrapper = await mountLayout({ current: 'users', narrow: false })

    const classes = wrapper.element.classList
    expect(classes, 'the row starts at the 1240 pixel breakpoint').toContain('large:flex-row')
    expect(classes, 'no row at the old 768 pixel breakpoint').not.toContain('md:flex-row')
  })

  it('shows the side navigation and the page side by side on wide widths', async () => {
    const wrapper = await mountLayout({ current: 'users', narrow: false })

    expect(wrapper.find(SIDE_NAV).exists(), 'the side navigation is shown').toBe(true)
    expect(wrapper.find('.page-body').exists(), 'the page is shown').toBe(true)
  })

  it('shows the page without the side navigation on narrow widths when a page is open', async () => {
    const wrapper = await mountLayout({ current: 'users', narrow: true })

    expect(wrapper.find(SIDE_NAV).exists(), 'the side navigation is replaced by the back arrow').toBe(false)
    expect(wrapper.find('.page-body').exists(), 'the page is shown').toBe(true)
  })

  it('shows the index of the groups on narrow widths when no page is open', async () => {
    const wrapper = await mountLayout({ current: undefined, narrow: true })

    const indexLinks = wrapper.findAll(`${SIDE_NAV} a`).map(link => link.text())

    expect(indexLinks, 'the index lists the links of every group').toEqual(['Users', 'Machine accounts', 'Teams', 'SSO', 'LDAP'])
    expect(wrapper.find('.page-body').exists(), 'no page is drawn under the index').toBe(false)
  })

  it('draws the page and the index together on Overview below 1240 pixels, with Overview first and marked current', async () => {
    const groups = [{ id: 'overview', label: 'Overview', links: [{ key: 'overview', label: 'Overview', to: { name: 'access' } }] }, ...fixtureGroups()]
    const wrapper = await mountLayout({ groups, current: 'overview', overview: true, narrow: true })

    expect(wrapper.find('.page-body').exists(), 'the page is drawn beside the index').toBe(true)
    expect(wrapper.find(SIDE_NAV).exists(), 'the index is drawn').toBe(true)
    expect(wrapper.get(`${SIDE_NAV} a`).text(), 'Overview is the first link of the index').toBe('Overview')
    expect(wrapper.get(`${SIDE_NAV} a`).attributes('aria-current'), 'and it is marked current').toBe('page')
    expect(wrapper.get('ul[aria-label="Overview"]').exists(), 'its list is named Overview, as it has no heading').toBe(true)
  })

  it('draws the group of Overview without a heading paragraph', async () => {
    const groups = [{ id: 'overview', label: 'Overview', links: [{ key: 'overview', label: 'Overview', to: { name: 'access' } }] }, ...fixtureGroups()]
    const wrapper = await mountLayout({ groups, current: 'overview', overview: true, narrow: true })

    const overviewGroup = wrapper.get('ul[aria-label="Overview"]').element.parentElement
    expect(overviewGroup.querySelector('p'), 'no visible heading above Overview').toBeNull()
  })
})

// The sticky rule (D1): from 1240 pixels the side navigation is sticky only while its full height fits between the top
// bar and the save bar. The size observer is a fake that the test triggers by hand. The heights are fixed per test: the
// navigation through the offsetHeight of its element, the window through window.innerHeight and the save bar through
// its own element; each is restored after the test. The window is 1000 pixels high, so the room is 1000 - 80 - 16 = 904.
describe('SideNavLayout sticky rule', () => {
  const mounted = []
  let navHeight = 0
  let barHeight = 0
  let savedInnerHeight
  let heightSpy

  beforeEach(() => {
    navHeight = 0
    barHeight = 0
    savedInnerHeight = window.innerHeight
    window.innerHeight = 1000
    heightSpy = vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockImplementation(function () {
      if (this.getAttribute('data-ingot-component') === 'side-nav-tree') return navHeight
      if (this.classList.contains('settings-save-bar')) return barHeight
      return 0
    })
  })

  afterEach(() => {
    mounted.splice(0).forEach(({ wrapper, bar }) => {
      wrapper.unmount()
      bar?.remove()
    })
    heightSpy.mockRestore()
    window.innerHeight = savedInnerHeight
  })

  // A fake of the size observer: it keeps the callbacks it is given, and `report` calls them as a size change would.
  const fakeObserver = () => {
    const observed = []
    const observe = (element, onChange) => {
      const entry = { element, onChange }
      observed.push(entry)
      return () => observed.splice(observed.indexOf(entry), 1)
    }
    const report = () => observed.forEach(entry => entry.onChange())
    return { observe, report }
  }

  const mountSticky = async (props = {}) => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/access/users')
    await router.isReady()
    const observer = fakeObserver()
    const wrapper = mount(SideNavLayout, {
      props: { label: 'Access sections', groups: fixtureGroups(), current: 'users', narrow: false, ...props },
      slots: { default: '<p class="page-body">Page body</p>' },
      global: { plugins: [router], provide: { [treeObserverKey]: observer.observe } },
      attachTo: document.body
    })
    const entry = { wrapper, bar: null }
    mounted.push(entry)
    await flushPromises()
    return { wrapper, observer, entry }
  }

  const isSticky = (wrapper) => wrapper.find(SIDE_NAV).classes().includes('sticky')

  it('has no scroll box of its own while the tree fits', async () => {
    navHeight = 800
    const { wrapper } = await mountSticky()

    const classes = wrapper.find(SIDE_NAV).classes()
    expect(classes.some(name => name.startsWith('max-h-')), 'no maximum height').toBe(false)
    expect(classes, 'no inner scrollbar').not.toContain('overflow-y-auto')
  })

  it('is sticky while the whole tree fits between the bars', async () => {
    navHeight = 800
    const { wrapper } = await mountSticky()

    expect(isSticky(wrapper), 'an 800 pixel tree fits in 904 pixels of room').toBe(true)
  })

  it('is not sticky when the tree is taller than the room between the bars', async () => {
    navHeight = 1000
    const { wrapper } = await mountSticky()

    expect(isSticky(wrapper), 'a 1000 pixel tree does not fit in 904 pixels of room').toBe(false)
  })

  it('switches to static when the observer reports that the tree has grown', async () => {
    navHeight = 800
    const { wrapper, observer } = await mountSticky()
    expect(isSticky(wrapper), 'sticky before the change').toBe(true)

    navHeight = 1000
    observer.report()
    await flushPromises()

    expect(isSticky(wrapper), 'static once the tree is taller than the room').toBe(false)
  })

  it('does not change its state while the page scrolls', async () => {
    navHeight = 800
    const { wrapper } = await mountSticky()

    navHeight = 1000
    window.dispatchEvent(new Event('scroll'))
    document.dispatchEvent(new Event('scroll'))
    await flushPromises()

    expect(isSticky(wrapper), 'the decision waits for a size change, not for a scroll').toBe(true)
  })

  it('takes the save bar out of the room when it appears', async () => {
    navHeight = 800
    const { wrapper, entry } = await mountSticky()
    expect(isSticky(wrapper), 'sticky without a save bar').toBe(true)

    const bar = document.createElement('div')
    bar.className = 'settings-save-bar'
    document.body.append(bar)
    entry.bar = bar
    barHeight = 200
    await wrapper.setProps({ unsaved: ['settings:javadoc'] })
    await flushPromises()

    expect(isSticky(wrapper), 'an 800 pixel tree does not fit beside a 216 pixel save bar').toBe(false)
  })
})

describe('SideNavLayout index rows', () => {
  // The rows of the filter tree carry a description; the index shows it below 1240 pixels, and the side column does not.
  const mountRows = async (props) => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/access/users')
    await router.isReady()
    return mount(SideNavLayout, {
      props: { label: 'Access sections', groups: filterGroups(), ...props },
      slots: { default: '<p class="page-body">Page body</p>' },
      global: { plugins: [router] }
    })
  }

  const row = (wrapper, label) => wrapper.findAll(`${SIDE_NAV} a`).find(link => link.text().startsWith(label))

  it('shows the description under the label of a row on narrow widths', async () => {
    const wrapper = await mountRows({ current: undefined, narrow: true })

    expect(row(wrapper, 'Users').text(), 'the label, then the description').toContain('People who sign in')
  })

  it('names a row with a description by its label, and points the description to it', async () => {
    const wrapper = await mountRows({ current: undefined, narrow: true })

    const users = row(wrapper, 'Users')
    expect(users.attributes('aria-label'), 'the name is the label, not the label with the description').toBe('Users')
    const described = wrapper.get(`[id="${users.attributes('aria-describedby')}"]`)
    expect(described.text(), 'the description is the description of the row').toBe('People who sign in')
  })

  it('shows only the label of a row without a description', async () => {
    const wrapper = await mountRows({ current: undefined, narrow: true })

    expect(row(wrapper, 'Teams').text(), 'a row without a description is one line').toBe('Teams')
  })

  it('shows only the labels in the side column from 1240 pixels', async () => {
    const wrapper = await mountRows({ current: 'teams', narrow: false })

    expect(row(wrapper, 'Users').text(), 'the side column draws labels only').toBe('Users')
  })
})

// The filter tree: its links carry the description and keywords the filter matches. Built per test.
const filterGroups = () => [
  {
    heading: 'Accounts',
    links: [
      { key: 'users', label: 'Users', description: 'People who sign in', keywords: 'access', to: { name: 'access-users' } },
      { key: 'machines', label: 'Machine accounts', description: '', keywords: 'ci, bot', to: { name: 'access-machines' } },
      { key: 'teams', label: 'Teams', description: '', keywords: 'access, group', to: { name: 'access-teams' } }
    ]
  },
  {
    heading: 'Sign in',
    links: [
      { key: 'sso', label: 'SSO', description: '', keywords: 'oidc, openid', to: { name: 'access-sso' } },
      { key: 'ldap', label: 'LDAP', description: '', keywords: 'active directory', to: { name: 'access-ldap' } }
    ]
  }
]

describe('SideNavLayout find field', () => {
  // Mounted into the document, so focus can be checked. Each test unmounts what it mounted.
  const mounted = []
  afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()))

  const mountFilter = async (props = {}) => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/access/users')
    await router.isReady()
    const wrapper = mount(SideNavLayout, {
      props: { label: 'Access sections', groups: filterGroups(), ...props },
      slots: { default: '<p class="page-body">Page body</p>' },
      global: { plugins: [router] },
      attachTo: document.body
    })
    mounted.push(wrapper)
    return { wrapper, router }
  }

  const field = (wrapper) => wrapper.get('input[type="search"]')
  const announcement = (wrapper) => wrapper.get('[aria-live="polite"]').text()

  it('labels the search field Find an administration page', async () => {
    const { wrapper } = await mountFilter({ current: 'users' })

    const id = field(wrapper).attributes('id')
    expect(wrapper.get(`label[for="${id}"]`).text(), 'the field is labelled by its visible label').toBe('Find an administration page')
  })

  it('announces one page for one match', async () => {
    const { wrapper } = await mountFilter({ current: 'users' })

    await field(wrapper).setValue('oidc')

    expect(announcement(wrapper)).toBe('1 page matches')
  })

  it('announces the number of pages for several matches', async () => {
    const { wrapper } = await mountFilter({ current: 'users' })

    await field(wrapper).setValue('access')

    expect(announcement(wrapper), 'Users and Teams match').toBe('2 pages match')
  })

  it('announces that no page matches the text, and lists only the current entry', async () => {
    const { wrapper } = await mountFilter({ current: 'users' })

    await field(wrapper).setValue('xyz')

    expect(announcement(wrapper)).toBe('No administration page matches "xyz"')
    expect(wrapper.findAll(`${SIDE_NAV} a`).map(link => link.text()), 'the current entry stays visible').toEqual(['Users'])
  })

  it('clears the field on Escape, lists every entry again, and keeps the focus in the field', async () => {
    const { wrapper } = await mountFilter({ current: 'users' })
    await field(wrapper).setValue('oidc')
    field(wrapper).element.focus()

    await field(wrapper).trigger('keydown', { key: 'Escape' })

    expect(field(wrapper).element.value, 'the field is empty').toBe('')
    expect(wrapper.findAll(`${SIDE_NAV} a`).length, 'every entry is listed').toBe(5)
    expect(document.activeElement, 'the focus stays in the field').toBe(field(wrapper).element)
  })

  it('opens the first matching entry on Enter', async () => {
    const { wrapper, router } = await mountFilter({ current: 'users' })
    await field(wrapper).setValue('ldap')

    await field(wrapper).trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(router.currentRoute.value.name, 'the first match is opened').toBe('access-ldap')
  })
})

describe('SideNavLayout unsaved marks', () => {
  const unsavedGroups = () => [
    {
      heading: 'Artifacts',
      links: [
        { key: 'settings:javadoc', label: 'Javadoc', to: { name: 'access-users' } },
        { key: 'teams', label: 'Teams', to: { name: 'access-teams' } }
      ]
    }
  ]

  const mountMarked = async (props) => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/access/users')
    await router.isReady()
    return mount(SideNavLayout, {
      props: { label: 'Access sections', groups: unsavedGroups(), current: 'teams', ...props },
      slots: { default: '<p class="page-body">Page body</p>' },
      global: { plugins: [router] }
    })
  }

  it('marks a link whose domain has an unsaved draft with the text Unsaved', async () => {
    const wrapper = await mountMarked({ unsaved: ['settings:javadoc'] })

    const javadoc = wrapper.findAll(`${SIDE_NAV} a`).find(link => link.text().includes('Javadoc'))
    expect(javadoc.text(), 'the mark is drawn in the link').toContain('Unsaved')
  })

  it('names a marked link Javadoc, unsaved changes for a screen reader', async () => {
    const wrapper = await mountMarked({ unsaved: ['settings:javadoc'] })

    const javadoc = wrapper.findAll(`${SIDE_NAV} a`).find(link => link.text().includes('Javadoc'))
    expect(javadoc.attributes('aria-label'), 'the accessible name ends with unsaved changes').toBe('Javadoc, unsaved changes')
  })

  it('draws no mark and no changed name on a link without a draft', async () => {
    const wrapper = await mountMarked({ unsaved: ['settings:javadoc'] })

    const teams = wrapper.findAll(`${SIDE_NAV} a`).find(link => link.text().includes('Teams'))
    expect(teams.text(), 'no mark on Teams').not.toContain('Unsaved')
    expect(teams.attributes('aria-label'), 'the name is not changed').toBeUndefined()
  })
})

// The lead is content that the caller places above the page: on Overview below 1240 pixels it comes before the index,
// because it is the reason to land there; on wide widths it is the first thing of the page, beside the index.
describe('SideNavLayout lead', () => {
  const overviewGroups = () => [
    { id: 'overview', label: 'Overview', links: [{ key: 'overview', label: 'Overview', to: { name: 'access' } }] },
    ...fixtureGroups()
  ]

  const mountLead = async (props = {}) => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/access')
    await router.isReady()
    return mount(SideNavLayout, {
      props: { label: 'Access sections', groups: overviewGroups(), current: 'overview', overview: true, ...props },
      slots: {
        lead: '<p class="lead-body">Needs attention</p>',
        default: '<p class="page-body">Page body</p>'
      },
      global: { plugins: [router] }
    })
  }

  const follows = (first, second) => first.element.compareDocumentPosition(second.element) & Node.DOCUMENT_POSITION_FOLLOWING

  it('draws the lead above the index on Overview below 1240 pixels', async () => {
    const wrapper = await mountLead({ narrow: true })

    expect(wrapper.find('.lead-body').exists(), 'the lead is drawn').toBe(true)
    expect(follows(wrapper.get('.lead-body'), wrapper.get(SIDE_NAV)), 'the index follows the lead').toBeTruthy()
  })

  it('draws the page body after the index on Overview below 1240 pixels', async () => {
    const wrapper = await mountLead({ narrow: true })

    expect(follows(wrapper.get(SIDE_NAV), wrapper.get('.page-body')), 'the page body follows the index').toBeTruthy()
  })

  it('draws the lead inside the page, after the index, on wide widths', async () => {
    const wrapper = await mountLead({ narrow: false })

    expect(follows(wrapper.get(SIDE_NAV), wrapper.get('.lead-body')), 'the lead is beside the index, after it in reading order').toBeTruthy()
    expect(follows(wrapper.get('.lead-body'), wrapper.get('.page-body')), 'and before the page body').toBeTruthy()
  })

  it('draws no lead when the caller passes none', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/access')
    await router.isReady()
    const wrapper = mount(SideNavLayout, {
      props: { label: 'Access sections', groups: overviewGroups(), current: 'overview', narrow: true, overview: true },
      slots: { default: '<p class="page-body">Page body</p>' },
      global: { plugins: [router] }
    })

    expect(wrapper.find('.lead-body').exists()).toBe(false)
    expect(wrapper.find('.page-body').exists(), 'the page is still drawn').toBe(true)
  })
})
