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
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdministrationFrame from './AdministrationFrame.vue'

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
const measureOf = (wrapper) => wrapper.get('header').element.nextElementSibling

// The label of a crumb: its text without the separator after it, which is decorative and aria-hidden.
const crumbLabel = (crumb) => {
  const copy = crumb.element.cloneNode(true)
  copy.querySelectorAll('[aria-hidden="true"]').forEach(node => node.remove())
  return copy.textContent.trim()
}

const mountFrame = async (props, url = '/audit') => {
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
  })
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

  it('gives a wide page the full width after the side navigation, with no maximum width', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    const classes = wrapper.element.classList
    expect(classes, 'a wide page (a list or a table) takes the width it is given').toContain('max-w-none')
    expect(classes, 'no 1280 pixel cap').not.toContain('max-w-[1280px]')
    expect(classes, 'no 1600 pixel cap').not.toContain('min-[1920px]:max-w-[1600px]')
  })

  it('keeps the section at full width on a reading page too, so its header spans like a wide page', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', reading: true })

    const classes = wrapper.element.classList
    expect(classes, 'the header spans the container on both kinds of page').toContain('max-w-none')
    expect(classes, 'no 960 pixel cap on the section, which would float the header shortcuts').not.toContain('max-w-[960px]')
  })

  it('keeps a reading page at a 960 pixel measure around the layout only, at the start of the content column', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', reading: true })

    const measure = measureOf(wrapper)
    expect(measure.classList, 'a reading page (text and forms) is at most 960 pixels wide').toContain('max-w-[960px]')
    expect(measure.classList, 'it stays at the start of the column, not centred').not.toContain('mx-auto')
    expect(measure.querySelector('[aria-label="Administration"]'), 'the layout is measured').not.toBeNull()
    expect(measure.querySelector('header'), 'the header is not measured').toBeNull()
  })

  it('draws no reading measure on a wide page', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', reading: false })

    expect(measureOf(wrapper).classList).not.toContain('max-w-[960px]')
  })

  it('draws the page heading and its actions in one row from 600 pixels', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    const header = wrapper.find('header').classes()
    expect(header, 'the row starts at the 600 pixel breakpoint').toContain('medium:flex-row')
    expect(header, 'no row at the old 768 pixel breakpoint').not.toContain('md:flex-row')
  })

  it('lets the actions wrap below the text when the row is short of room, instead of squeezing the text', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', shortcuts: [{ label: 'Audit settings', to: { name: 'settings', params: { domain: 'audit' } } }] })

    expect(wrapper.find('header').classes(), 'the header row may wrap').toContain('medium:flex-wrap')
    const textColumn = wrapper.get('header > div').classes()
    expect(textColumn, 'the text keeps a basis of 20 rem before the actions take room').toEqual(expect.arrayContaining(['medium:basis-80', 'min-w-0']))
    const actions = wrapper.get('header > div:last-child').classes()
    expect(actions, 'the actions group may wrap onto its own line').toContain('flex-wrap')
    expect(actions, 'the actions group does not refuse to shrink').not.toContain('shrink-0')
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

  it('names the group of the current entry in the breadcrumb, and the page in the heading', async () => {
    const wrapper = await mountFrame({ current: 'settings:audit' }, '/settings/audit')

    const crumbs = wrapper.findAll('[aria-label="Breadcrumb"] li').map(crumbLabel)
    expect(crumbs, 'the section, then the group of the page').toEqual(['Administration', 'Monitoring and support'])
    expect(wrapper.get('h1').text(), 'the page itself is named by the heading').toBe('Audit settings')
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

  it('draws no empty description element when the description is empty', async () => {
    const wrapper = await mountFrame({ current: 'page:audit', description: '' })

    expect(wrapper.findAll('header p').filter(paragraph => paragraph.text() === ''), 'no empty paragraph in the header').toEqual([])
  })

  it('adds the list page as a link to the trail of a detail page, and names the page by its item', async () => {
    const wrapper = await mountFrame({ current: 'page:users', title: 'alice', detail: true }, '/access/users/alice')

    const crumbs = wrapper.findAll('[aria-label="Breadcrumb"] li')
    expect(crumbs.map(crumbLabel), 'Administration, Access, then the list').toEqual(['Administration', 'Access', 'Users'])
    expect(crumbs[1].find('a').exists(), 'Access is not a link').toBe(false)
    expect(crumbs[2].find('a').attributes('href'), 'Users links to the list').toBe('/access/users')
    expect(wrapper.get('h1').text(), 'the heading is the item name').toBe('alice')
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

  it('shows only the last crumb on narrow widths, beside the back arrow, with the full trail as its name', async () => {
    const wrapper = await mountFrame({ current: 'settings:audit', narrow: true }, '/settings/audit')

    const crumbs = wrapper.findAll('nav[aria-label="Administration / Monitoring and support"] li').map(crumbLabel)
    expect(crumbs, 'only the last crumb is drawn').toEqual(['Monitoring and support'])
    const trail = wrapper.get('nav[aria-label="Administration / Monitoring and support"]')
    expect(trail.attributes('title'), 'the full trail is the title').toBe('Administration / Monitoring and support')
    expect([...trail.element.parentElement.classList], 'the back arrow and the trail share one row').toEqual(expect.arrayContaining(['flex', 'items-center']))
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
  const sectionsButton = (wrapper) => wrapper.findAll('button').find(button => button.text() === 'Sections')
  const sheet = (wrapper) => wrapper.find('[role="dialog"]')

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

    await sheet(wrapper).findAll('a').find(link => link.text() === 'Users').trigger('click')
    await flushPromises()

    expect(wrapper.vm.$router.currentRoute.value.path, 'the chosen page is open').toBe('/access/users')
    expect(sheet(wrapper).exists(), 'the sheet is closed').toBe(false)
  })
})

describe('AdministrationFrame breadcrumb markup', () => {
  const crumbItems = (wrapper) => wrapper.findAll('[aria-label="Breadcrumb"] li')
  const separators = (wrapper) => wrapper.findAll('[aria-label="Breadcrumb"] [aria-hidden="true"]')

  it('draws each separator as an element hidden from assistive technology, so it is not in any name', async () => {
    const wrapper = await mountFrame({ current: 'page:users', title: 'alice', detail: true }, '/access/users/alice')

    const slashes = separators(wrapper)
    expect(slashes.map(slash => slash.text()), 'one slash between each pair of crumbs').toEqual(['/', '/'])
    expect(slashes.every(slash => slash.attributes('aria-hidden') === 'true')).toBe(true)
  })

  it('draws no separator after the last crumb', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    const last = crumbItems(wrapper).at(-1)
    expect(last.find('[aria-hidden="true"]').exists(), 'the last crumb has no slash after it').toBe(false)
  })

  it('draws the crumb and its separator in one inline row, so they share a baseline', async () => {
    const wrapper = await mountFrame({ current: 'page:users', title: 'alice', detail: true }, '/access/users/alice')

    for (const crumb of crumbItems(wrapper)) {
      expect(crumb.classes(), 'each crumb is a flex row, so its separator is beside it').toEqual(expect.arrayContaining(['flex', 'items-center']))
    }
  })

  it('lets a plain crumb truncate inside its row, instead of breaking the row', async () => {
    const wrapper = await mountFrame({ current: 'page:audit' })

    const plain = crumbItems(wrapper)[1].get('span:not([aria-hidden])')
    expect(plain.text()).toBe('Monitoring and support')
    expect(plain.classes(), 'the text may shrink and ends with an ellipsis').toEqual(expect.arrayContaining(['min-w-0', 'truncate']))
  })

  it('lets a link crumb truncate inside its row as well', async () => {
    const wrapper = await mountFrame({ current: 'page:users', title: 'alice', detail: true }, '/access/users/alice')

    const link = crumbItems(wrapper)[2].get('a')
    expect(link.classes(), 'the link may shrink and ends with an ellipsis').toEqual(expect.arrayContaining(['min-w-0', 'truncate']))
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
})
