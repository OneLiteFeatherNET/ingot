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

import { describe, it, expect } from 'vitest'
import { mount, flushPromises, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import AdministrationFrame from './AdministrationFrame.vue'

type FrameProps = Partial<InstanceType<typeof AdministrationFrame>['$props']>

const shell = { render: () => null }

const routes = [
  { name: 'access-users', path: '/access/users', component: shell },
  { name: 'access-user', path: '/access/users/:name', component: shell },
  { name: 'audit', path: '/audit', component: shell },
  { name: 'settings', path: '/settings/:domain?', component: shell }
]

// The fixture is built per test, so no test can change what another one reads.
const groups = () => [
  {
    id: 'Security',
    heading: 'Access',
    links: [{ key: 'page:users', label: 'Users', to: { name: 'access-users' } }]
  },
  {
    id: 'Support',
    heading: 'Monitoring and support',
    links: [
      { key: 'page:audit', label: 'Audit log', to: { name: 'audit' } },
      { key: 'settings:audit', label: 'Audit settings', to: { name: 'settings', params: { domain: 'audit' } } }
    ]
  }
]

// The element that holds the side navigation and the page: the wrapper of the layout, which follows the header.
const headerColumn = (wrapper: VueWrapper) => wrapper.get('header').element.parentElement as Element
const measureOf = (wrapper: VueWrapper) => headerColumn(wrapper).nextElementSibling as Element
// The MdPage of the frame: the first element of the section, which holds the header column and the layout.
const pageOf = (wrapper: VueWrapper) => wrapper.get('section').element.firstElementChild as Element

// The label of a crumb: its text without the separator after it, which is decorative and aria-hidden.
const crumbLabel = (crumb: { element: Element }) => {
  const copy = crumb.element.cloneNode(true) as HTMLElement
  copy.querySelectorAll('[aria-hidden="true"]').forEach(node => node.remove())
  return (copy.textContent as string).trim()
}

const mountFrame = async (props: FrameProps, url = '/audit') => {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(url)
  await router.isReady()
  return mount(AdministrationFrame, {
    props: {
      description: 'Who did what on this instance.',
      label: 'Administration',
      sectionLabel: 'Administration',
      groups: groups(),
      indexTo: { name: 'settings' },
      ...props
    },
    slots: { default: '<p data-test="page">page content</p>' },
    global: { plugins: [router] }
  }) as VueWrapper
}

describe('AdministrationFrame', () => {
  it('draws one heading that names the page by the label of its tree entry', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    expect(wrapper.findAll('h1').map(heading => heading.text()), 'the one h1 of the frame').toEqual(['Audit log'])
  })

  it('draws the description of the page under the heading', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', description: 'Who did what on this instance.' })

    expect(wrapper.text()).toContain('Who did what on this instance.')
  })

  it('draws the page content beside the side navigation', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    expect(wrapper.find('[data-test="page"]').exists(), 'the slot is drawn').toBe(true)
    expect(wrapper.find('[aria-label="Administration"]').exists(), 'the side navigation is drawn').toBe(true)
  })

  it('keeps a reading page at a 960 pixel measure around the layout only, at the start of the content column', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', reading: true })

    const measure = measureOf(wrapper)
    expect(measure.classList, 'a reading page (text and forms) is at most 960 pixels wide').toContain('max-w-md-reading')
    expect(measure.classList, 'it stays at the start of the column, not centred').not.toContain('mx-auto')
    expect(measure.querySelector('[aria-label="Administration"]'), 'the layout is measured').not.toBeNull()
    expect(measure.querySelector('header'), 'the header is not measured').toBeNull()
  })

  it('draws no reading measure on a wide page', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', reading: false })

    expect(measureOf(wrapper).classList).not.toContain('max-w-md-reading')
  })

  it('does not make the page header sticky, so it scrolls away', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    expect(wrapper.find('header').classes(), 'the header is not sticky').not.toContain('sticky')
  })

  it('makes the side navigation the only sticky element of the page', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    const sticky = wrapper.findAll('.sticky')
    expect(sticky.length, 'exactly one sticky element').toBe(1)
    expect(sticky[0].attributes('aria-label'), 'the sticky element is the side navigation').toBe('Administration')
  })

  it('marks only the current entry with aria-current page', async () => {
    const wrapper = await mountFrame({ current: 'settings:audit' }, '/settings/audit')

    const marked = wrapper.findAll('[aria-label="Administration"] a').filter(link => link.attributes('aria-current') === 'page')
    expect(marked.map(link => link.text()), 'exactly one entry is current').toEqual(['Audit settings'])
  })

  it('draws no side navigation on a page without entries, such as the hub', async () => {
    const wrapper = await mountFrame({ groups: [], current: undefined }, '/settings')

    expect(wrapper.find('[aria-label="Administration"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="page"]').exists(), 'the page is still drawn').toBe(true)
  })
})

describe('AdministrationFrame page header', () => {
  it('labels the frame section by its one heading', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    const heading = wrapper.get('h1')
    expect(wrapper.get('section').attributes('aria-labelledby'), 'the region is named by the heading').toBe(heading.attributes('id'))
  })

  it('takes the heading from the title prop when one is given', async () => {
    const wrapper = await mountFrame({ current: 'settings:audit', title: 'Audit settings' }, '/settings/audit')

    expect(wrapper.findAll('h1').map(heading => heading.text())).toEqual(['Audit settings'])
  })

  it('draws the breadcrumb above the heading', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    const breadcrumb = wrapper.get('[aria-label="Breadcrumb"]').element
    const heading = wrapper.get('h1').element
    expect(breadcrumb.compareDocumentPosition(heading) & Node.DOCUMENT_POSITION_FOLLOWING, 'the heading follows the breadcrumb').toBeTruthy()
  })

  it('draws the group heading as plain text after the link to Administration', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    const crumbs = wrapper.findAll('[aria-label="Breadcrumb"] li')
    expect(crumbs.map(crumbLabel)).toEqual(['Administration', 'Monitoring and support'])
    expect(crumbs[0].find('a').attributes('href'), 'Administration links to the hub').toBe('/settings')
    expect(crumbs[1].find('a').exists(), 'the group is not a link').toBe(false)
  })

  it('keeps the header actions container out of the tab order and draws no focus outline on it', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', shortcuts: [{ label: 'Audit settings', to: { name: 'settings', params: { domain: 'audit' } } }] })

    const actions = wrapper.get('header > div:last-child')
    expect(actions.attributes('tabindex'), 'a container is not a focus stop').toBeUndefined()
    const focusClasses = actions.classes().filter(name => /outline|ring|focus/.test(name))
    expect(focusClasses, 'no focus style on the container: only the focused control draws its ring').toEqual([])
  })

  it('draws the actions row on a phone when the page has shortcuts', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', shortcuts: [{ label: 'Audit log', to: { name: 'audit' } }] })

    const actions = wrapper.get('header > div:last-child')
    expect(actions.classes(), 'the row holds the shortcuts on every width').toContain('flex')
    expect(actions.classes(), 'and is not hidden on a phone').not.toContain('hidden')
  })

  it('draws a back arrow with a 48 pixel target on narrow widths', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', narrow: true })

    const back = wrapper.get('[aria-label="Back to Administration"]')
    expect(back.attributes('href'), 'the arrow leads to the index').toBe('/settings')
    expect(back.classes(), 'the target is 48 pixels square').toContain('size-12')
  })

  it('draws no back arrow on Overview below 1240 pixels, where the index is on the same page', async () => {
    const overview = [{ id: 'overview', label: 'Overview', links: [{ key: 'page:overview', label: 'Overview', to: { name: 'settings' } }] }, ...groups()]
    const wrapper = await mountFrame({ groups: overview, current: 'page:overview', overview: true, narrow: true, title: '' }, '/settings')

    expect(wrapper.find('[aria-label="Back to Administration"]').exists(), 'no back arrow on Overview').toBe(false)
    expect(wrapper.find('[data-test="page"]').exists(), 'the page is drawn').toBe(true)
  })

  it('draws no back arrow on wide widths', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', narrow: false })

    expect(wrapper.find('[aria-label="Back to Administration"]').exists()).toBe(false)
  })

  it('draws the shortcut links of the page in a related pages block, each to its own address', async () => {
    const wrapper = await mountFrame({
      current: 'page:audit',
      shortcuts: [{ label: 'Audit settings', to: { name: 'settings', params: { domain: 'audit' } } }]
    })

    const links = wrapper.findAll('[aria-label="Related pages"] a')
    expect(links.map(link => link.text())).toEqual(['Audit settings'])
    expect(links[0].attributes('href')).toBe('/settings/audit')
  })

  it('draws no related pages block without shortcuts', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    expect(wrapper.find('[aria-label="Related pages"]').exists()).toBe(false)
  })
})

// The Sections button opens the same tree in a side sheet from 600 to 1239 pixels, beside the back arrow. Only the page
// with a back arrow draws it; the index on /settings already is the tree.
describe('AdministrationFrame Sections sheet', () => {
  const sectionsButton = (wrapper: VueWrapper) => wrapper.findAll('button').find(button => button.text() === 'Sections') as DOMWrapper<HTMLButtonElement>
  const sheet = (wrapper: VueWrapper) => wrapper.find('[role="dialog"]')

  it('draws the Sections button beside the back arrow, shown from 600 pixels only', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', narrow: true })

    const button = sectionsButton(wrapper)
    expect(button, 'the Sections button is drawn').toBeDefined()
    expect(button.classes(), 'hidden below 600 pixels').toContain('hidden')
    expect(button.classes(), 'shown from the 600 pixel breakpoint').toContain('medium:inline-flex')
    expect(button.classes(), 'a 48 pixel target').toContain('min-h-12')
  })

  it('draws no Sections button on wide widths, where the tree is beside the page', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', narrow: false })

    expect(sectionsButton(wrapper), 'no button from 1240 pixels').toBeUndefined()
  })

  it('draws no Sections button on the index at /settings, which is the tree on the page', async () => {
    const wrapper = await mountFrame({ groups: groups(), current: undefined, narrow: true }, '/settings')

    expect(sectionsButton(wrapper), 'no button without a back arrow').toBeUndefined()
  })

  it('opens the tree in a modal sheet named Administration pages, with the current entry marked', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', narrow: true })

    await sectionsButton(wrapper).trigger('click')

    expect(sheet(wrapper).exists(), 'the sheet is open').toBe(true)
    expect(sheet(wrapper).attributes('aria-modal')).toBe('true')
    const names = sheet(wrapper).findAll('a').map(link => link.text())
    expect(names, 'the sheet holds the whole tree').toEqual(['Users', 'Audit log', 'Audit settings'])
    const marked = sheet(wrapper).findAll('a').filter(link => link.attributes('aria-current') === 'page')
    expect(marked.map(link => link.text()), 'the current entry is marked').toEqual(['Audit log'])
  })

  it('opens the chosen entry and closes the sheet', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', narrow: true })
    await sectionsButton(wrapper).trigger('click')

    await (sheet(wrapper).findAll('a').find(link => link.text() === 'Users') as DOMWrapper<HTMLAnchorElement>).trigger('click')
    await flushPromises()

    expect((wrapper.vm as unknown as { $router: Router }).$router.currentRoute.value.path, 'the chosen page is open').toBe('/access/users')
    expect(sheet(wrapper).exists(), 'the sheet is closed').toBe(false)
  })
})

describe('AdministrationFrame breadcrumb markup', () => {
  const crumbItems = (wrapper: VueWrapper) => wrapper.findAll('[aria-label="Breadcrumb"] li')
  const separators = (wrapper: VueWrapper) => wrapper.findAll('[aria-label="Breadcrumb"] [aria-hidden="true"]')

  it('draws each separator as an element hidden from assistive technology, so it is not in any name', async () => {
    const wrapper = await mountFrame({ current: 'page:users', title: 'alice', detail: true }, '/access/users/alice')

    const slashes = separators(wrapper)
    expect(slashes.map(slash => slash.text()), 'one slash between each pair of crumbs').toEqual(['/', '/'])
    expect(slashes.every(slash => slash.attributes('aria-hidden') === 'true')).toBe(true)
  })

  it('draws no separator after the last crumb', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    const last = crumbItems(wrapper).at(-1) as DOMWrapper<Element>
    expect(last.find('[aria-hidden="true"]').exists(), 'the last crumb has no slash after it').toBe(false)
  })

  it('draws the crumb and its separator in one inline row, so they share a baseline', async () => {
    const wrapper = await mountFrame({ current: 'page:users', title: 'alice', detail: true }, '/access/users/alice')

    for (const crumb of crumbItems(wrapper)) {
      expect(crumb.classes(), 'each crumb is a flex row, so its separator is beside it').toEqual(expect.arrayContaining(['flex', 'items-center']))
    }
  })

})

describe('AdministrationFrame on the settings pages', () => {
  // The tree of /settings: Overview is the first entry, in a group without a heading, and the settings groups follow.
  const overviewGroups = () => [
    { id: 'overview', label: 'Overview', links: [{ key: 'page:overview', label: 'Overview', description: 'Shared configuration for all instances.', to: { name: 'settings' } }] },
    ...groups()
  ]

  it('names the landing page Overview in its one heading, with the description of the tree entry', async () => {
    const wrapper = await mountFrame({ groups: overviewGroups(), current: 'page:overview', overview: true, description: undefined }, '/settings')

    expect(wrapper.findAll('h1').map(heading => heading.text()), 'the heading is the label of the Overview entry').toEqual(['Overview'])
    expect(wrapper.text()).toContain('Shared configuration for all instances.')
  })

  it('draws the single crumb Administration on Overview, as plain text and not a link', async () => {
    const wrapper = await mountFrame({ groups: overviewGroups(), current: 'page:overview', overview: true, description: undefined }, '/settings')

    const crumbs = wrapper.findAll('[aria-label="Breadcrumb"] li')
    expect(crumbs.map(crumbLabel), 'only the section').toEqual(['Administration'])
    expect(crumbs[0].find('a').exists(), 'the crumb is not a link to the page itself').toBe(false)
  })

  it('draws the crumb Administration as a link to /settings, then Monitoring and support as text, on the audit settings page', async () => {
    const wrapper = await mountFrame({ groups: overviewGroups(), current: 'settings:audit' }, '/settings/audit')

    const crumbs = wrapper.findAll('[aria-label="Breadcrumb"] li')
    expect(crumbs.map(crumbLabel)).toEqual(['Administration', 'Monitoring and support'])
    expect(crumbs[0].find('a').attributes('href'), 'Administration links to /settings').toBe('/settings')
    expect(crumbs[1].find('a').exists(), 'the group is plain text').toBe(false)
    expect(wrapper.get('h1').text(), 'the page is named by its heading').toBe('Audit settings')
  })

  it('draws the section crumb while the tree does not hold the entry on screen yet, so the header keeps its height', async () => {
    const wrapper = await mountFrame({ groups: overviewGroups().slice(0, 1), current: 'settings:audit' }, '/settings/audit')

    const crumbs = wrapper.findAll('[aria-label="Breadcrumb"] li')
    expect(crumbs.map(crumbLabel), 'the trail row is drawn before the settings domains arrive').toEqual(['Administration'])
  })
})

// The placement of design D2: from 1240 pixels the header sits in the page column, beside the side navigation, and
// below 1240 pixels the three stack in DOM order (header, side navigation, page).
describe('AdministrationFrame header beside the side navigation', () => {
  it('lets the layout join the grid of the frame from 1240 pixels, so the side navigation can start with the header', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    expect(measureOf(wrapper).classList, 'the wrapper of the layout has no box of its own from 1240 pixels').toContain('large:contents')
  })

  it('keeps the header above the side navigation and the page below 1240 pixels, in DOM order, with no grid', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    expect((wrapper.element as Element).classList.contains('grid'), 'no grid below 1240 pixels').toBe(false)
    const header = wrapper.get('header').element
    const layout = measureOf(wrapper)
    expect(header.compareDocumentPosition(layout) & Node.DOCUMENT_POSITION_FOLLOWING, 'the header comes before the layout in the document').toBeTruthy()
  })

  it('caps a reading page at the 960 pixel measure including the side navigation, from 1240 pixels', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', reading: true })

    const page = wrapper.get('[data-test="page-column"]').element.classList
    expect(page, 'the page column keeps the measure beside the 208 pixel side navigation and its 40 pixel gap').toContain('large:max-w-md-reading-page')
  })

  it('does not cap a wide page from 1240 pixels', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', reading: false })

    const page = wrapper.get('[data-test="page-column"]').element.classList
    expect(page, 'a wide page takes the page column').not.toContain('large:max-w-md-reading-page')
  })
})

// The page shell and the one trail of fix-surface-hierarchy-and-page-header (tasks 2.4 and 2.12, design D2 to D4 and D9).
describe('AdministrationFrame page shell and trail', () => {
  const pageFixtures = (): Array<{ name: string; props: FrameProps; url: string }> => [
    { name: 'a group page', props: { current: 'page:audit' }, url: '/audit' },
    { name: 'a detail page', props: { current: 'page:users', title: 'alice', detail: true }, url: '/access/users/alice' },
    { name: 'the audit settings page', props: { current: 'settings:audit' }, url: '/settings/audit' },
    { name: 'the settings page without an entry', props: { groups: [], current: undefined }, url: '/settings' },
    {
      name: 'Overview',
      props: {
        groups: [{ id: 'overview', label: 'Overview', links: [{ key: 'page:overview', label: 'Overview', to: { name: 'settings' } }] }, ...groups()],
        current: 'page:overview',
        overview: true
      },
      url: '/settings'
    }
  ]

  for (const page of pageFixtures()) {
    it(`draws exactly one h1 on ${page.name}`, async () => {
      const wrapper = await mountFrame(page.props, page.url)

      expect(wrapper.findAll('h1'), `the one heading of ${page.name}`).toHaveLength(1)
    })
  }

  it('draws no separator with an after or before class in the header', async () => {
    const wrapper = await mountFrame({ current: 'page:users', title: 'alice', detail: true }, '/access/users/alice')

    const classes = wrapper.get('header').findAll('*').flatMap(element => element.classes())
    expect(classes.filter(name => /^(after|before):/.test(name)), 'separators are elements, not pseudo-elements').toEqual([])
  })

  it('marks only the last crumb of a detail page trail as the current page, as text', async () => {
    const wrapper = await mountFrame({ current: 'page:users', title: 'alice', detail: true }, '/access/users/alice')

    const current = wrapper.get('header').findAll('[aria-current="page"]')
    expect(current.map(element => element.text()), 'exactly one current crumb, the list entry').toEqual(['Users'])
  })

  it('draws only the last crumb below 1240 pixels, with the full trail as the title of that crumb', async () => {
    const wrapper = await mountFrame({ current: 'settings:audit', narrow: true }, '/settings/audit')

    expect(wrapper.get('header').findAll('li').map(crumbLabel), 'only the last crumb is drawn').toEqual(['Monitoring and support'])
    expect(wrapper.get('header').get('[aria-current="page"]').attributes('title'), 'the full trail').toBe('Administration / Monitoring and support')
  })

  it('draws the back control outside the header text column', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', narrow: true })

    const back = wrapper.get('[aria-label="Back to Administration"]').element
    expect(wrapper.get('header').element.contains(back), 'the back control is not inside the text column').toBe(false)
  })

  it('pulls the back control 12 pixels into the gutter with -ms-3', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', narrow: true })

    expect(wrapper.get('[aria-label="Back to Administration"]').classes(), 'the glyph starts on the title edge').toContain('-ms-3')
  })

  it('renders the page as the MdPage gutter, with no centring and no maximum width class', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', reading: true })

    const page = (wrapper.get('section').element.firstElementChild as Element).classList
    expect(page, 'the page is the container of MdPage').toContain('container')
    expect(page, 'the page is not centred').not.toContain('mx-auto')
    expect([...page].filter(name => name.startsWith('max-w-')), 'the page takes the full width').toEqual([])
  })
})

// The page shell, the header and the trail after fix-surface-hierarchy-and-page-header (design D2 to D5).
describe('AdministrationFrame page shell after the fix', () => {
  it('gives a wide page the full width after the side navigation, with no maximum width class', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    expect([...pageOf(wrapper).classList].filter(name => name.startsWith('max-w-')), 'no width cap on the page').toEqual([])
  })

  it('keeps the page at full width on a reading page too, so its header spans like a wide page', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', reading: true })

    const classes = [...pageOf(wrapper).classList]
    expect(classes.filter(name => name.startsWith('max-w-')), 'no 960 pixel cap on the page').toEqual([])
    expect(measureOf(wrapper).classList, 'the reading measure is on the layout').toContain('max-w-md-reading')
  })

  it('names the group of the current entry in the trail, and the page in the heading', async () => {
    const wrapper = await mountFrame({ current: 'settings:audit' }, '/settings/audit')

    expect(wrapper.findAll('[aria-label="Breadcrumb"] li').map(crumbLabel), 'the section, then the group of the page').toEqual(['Administration', 'Monitoring and support'])
    expect(wrapper.get('h1').text(), 'the page itself is named by the heading').toBe('Audit settings')
  })

  it('draws only the last crumb below 1240 pixels, and the full trail as the title of that crumb', async () => {
    const wrapper = await mountFrame({ current: 'settings:audit', narrow: true }, '/settings/audit')

    expect(wrapper.findAll('[aria-label="Breadcrumb"] li').map(crumbLabel), 'only the last crumb is drawn').toEqual(['Monitoring and support'])
    expect(wrapper.get('[aria-current="page"]').attributes('title'), 'the full trail').toBe('Administration / Monitoring and support')
  })

  it('draws the description line of the header when the page has no description, so the heading does not move', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', description: '' })

    const line = wrapper.findAll('header p')
    expect(line.length, 'the description line is drawn').toBe(1)
    expect(line[0].text(), 'it is empty').toBe('')
  })

  it('wraps a long heading inside the text column instead of cutting it', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', title: 'Audit log of every administrator action on this instance' })

    const heading = wrapper.get('h1')
    expect(heading.classes(), 'the heading may break onto more lines').toContain('break-words')
    expect(heading.classes(), 'it is not cut to one line').not.toContain('truncate')
  })

  it('adds the group of a detail page as a link to the list, and names the page by its item', async () => {
    const wrapper = await mountFrame({ current: 'page:users', title: 'alice', detail: true }, '/access/users/alice')

    const crumbs = wrapper.findAll('[aria-label="Breadcrumb"] li')
    expect(crumbs.map(crumbLabel), 'Administration, the group, then the list').toEqual(['Administration', 'Access', 'Users'])
    expect(crumbs[1].find('a').attributes('href'), 'the group leads to the list').toBe('/access/users')
    expect(crumbs[2].find('a').exists(), 'the list entry is text').toBe(false)
    expect(wrapper.get('h1').text(), 'the heading is the item name').toBe('alice')
  })

  it('draws the crumbs as a list whose text is a span that truncates inside its row', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    const plain = wrapper.get('[aria-current="page"] span')
    expect(plain.text()).toBe('Monitoring and support')
    expect(plain.classes(), 'the text may shrink and ends with an ellipsis').toEqual(expect.arrayContaining(['min-w-0', 'truncate']))
  })

  it('places the page on the frame grid from 1240 pixels, with the side navigation and the page column', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    const classes = [...pageOf(wrapper).classList]
    expect(classes, 'the frame is a grid from the 1240 pixel breakpoint').toContain('large:grid')
    expect(classes, 'the columns are the 208 pixel side navigation and the page').toContain('large:grid-cols-(--grid-md-side-nav)')
    expect(classes, 'the header row is auto and the page row takes the rest').toContain('large:grid-rows-(--grid-md-administration-rows)')
  })

  it('puts the header column in the page column on the first row from 1240 pixels', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    const column = headerColumn(wrapper).classList
    expect(column, 'the header column starts in the page column').toContain('large:col-start-2')
    expect(column, 'and sits on the first row, above the page').toContain('large:row-start-1')
  })

  it('draws the shortcut links of the page in the actions of the header', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', shortcuts: [{ label: 'Audit settings', to: { name: 'settings', params: { domain: 'audit' } } }] })

    expect(wrapper.findAll('header [aria-label="Related pages"] a').map(link => link.text())).toEqual(['Audit settings'])
  })

  it('draws no actions in the header of a page without actions, shortcuts or Sections', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    expect(wrapper.get('header').findAll('button').length, 'no button').toBe(0)
    expect(wrapper.find('[aria-label="Related pages"]').exists(), 'no related pages').toBe(false)
  })
})
