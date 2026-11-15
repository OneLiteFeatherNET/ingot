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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { sessionState } from '../../store/session'
import SearchView from './SearchView.vue'

// The session is replaced per file; each test sets the client and who is signed in.
vi.mock('../../store/session', async () => {
  const { ref } = await import('vue')
  const state = {
    client: ref(null),
    sessionReady: ref(true),
    details: ref(undefined),
    isLogged: ref(false),
    allowed: ref([])
  }
  return {
    sessionState: state,
    useSession: () => ({
      client: state.client,
      sessionReady: state.sessionReady,
      details: state.details,
      isLogged: state.isLogged,
      can: (node) => state.allowed.value.includes(node)
    })
  }
})

// The page under test is mounted directly, so every route renders nothing.
const page = { render: () => null }
const routes = [
  { name: 'overview', path: '/', component: page },
  { name: 'browse', path: '/browse/:qualifier(.*)*', component: page },
  { name: 'search', path: '/search', component: page },
  { name: 'maintenance', path: '/maintenance', component: page }
]

const gav = (overrides = {}) => ({
  groupId: 'org.apache.commons',
  artifactId: 'commons-lang3',
  version: '3.14.0',
  latestVersion: '3.17.0',
  snapshot: false,
  repositories: [{ name: 'releases', origin: 'HOSTED' }],
  fileCount: 8,
  lastModified: '2026-09-30T08:14:02Z',
  browseLink: '/browse/releases/org/apache/commons/commons-lang3/3.14.0',
  ...overrides
})

const answerPage = (overrides = {}) => ({
  items: [
    gav(),
    gav({
      artifactId: 'commons-io',
      version: '2.16.1',
      latestVersion: '2.16.1',
      repositories: [{ name: 'central-proxy', origin: 'MIRROR' }],
      fileCount: 3,
      browseLink: '/browse/central-proxy/commons-io/commons-io/2.16.1'
    }),
    gav({ artifactId: 'commons-text', version: '1.12.0', latestVersion: '1.12.0', browseLink: '/browse/releases/commons-text' })
  ],
  page: 0,
  size: 20,
  total: 143,
  totalCapped: false,
  facets: {
    repository: [{ value: 'releases', count: 612 }, { value: 'central-proxy', count: 90 }],
    extension: [{ value: 'jar', count: 301 }, { value: 'pom', count: 150 }],
    classifier: [{ value: '-', count: 150 }, { value: 'sources', count: 75 }],
    origin: [{ value: 'HOSTED', count: 700 }, { value: 'MIRROR', count: 90 }],
    snapshot: [{ value: 'false', count: 760 }, { value: 'true', count: 30 }]
  },
  indexStatus: { state: 'READY', lagSeconds: 0, lastFullRebuild: '2026-10-20T03:30:12Z' },
  ...overrides
})

const answerFiles = (overrides = {}) => ({
  items: [
    {
      repository: 'central-proxy',
      path: 'org/apache/commons/commons-io/2.16.1/commons-io-2.16.1-sources.jar',
      name: 'commons-io-2.16.1-sources.jar',
      classifier: 'sources',
      extension: 'jar',
      size: 650400,
      lastModified: '2026-02-01T10:00:00Z',
      origin: 'MIRROR',
      sourceHost: 'repo.maven.apache.org',
      sha1: '8d2f',
      browseLink: '/browse/central-proxy/org/apache/commons/commons-io/2.16.1/commons-io-2.16.1-sources.jar'
    }
  ],
  page: 0,
  size: 50,
  total: 1,
  ...overrides
})

const failing = (status, message) => Object.assign(new Error(`status ${status}`), { response: { status, data: { message } } })

// A client whose answers the test controls. Each test builds its own, so no call leaks between tests.
const clientAnswering = ({ search = () => Promise.resolve({ data: answerPage() }), searchFiles = () => Promise.resolve({ data: answerFiles() }) } = {}) => ({
  maven: { search: vi.fn(search), searchFiles: vi.fn(searchFiles) }
})

let wrapper
let router

const mountAt = async (url, { client = clientAnswering(), logged = false, allowed = [] } = {}) => {
  sessionState.client.value = client
  sessionState.sessionReady.value = true
  sessionState.details.value = undefined
  sessionState.isLogged.value = logged
  sessionState.allowed.value = allowed
  router = createRouter({ history: createMemoryHistory(), routes })
  router.push(url)
  await router.isReady()
  wrapper = mount(SearchView, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return client
}

const box = () => wrapper.find('input[type="search"]')
const rows = () => wrapper.findAll('[data-test="result"]')
const rowToggles = () => wrapper.findAll('[data-row-toggle]')
const chip = (group, value) => wrapper.find(`[data-test="chip-${group}-${value}"]`)
const liveRegion = () => wrapper.find('[data-test="live-region"]')
const addressQuery = () => router.currentRoute.value.query
const searchCalls = (client) => client.maven.search.mock.calls.map(call => call[0])
const press = async (target, key) => {
  await target.trigger('keydown', { key })
  await flushPromises()
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('SearchView request and address', () => {
  it('builds the request from the address', async () => {
    const client = await mountAt('/search?q=commons&origin=mirror&extension=jar&extension=pom&sort=size&order=desc&page=2')

    expect(searchCalls(client)).toEqual(['q=commons&origin=mirror&sort=size&order=desc&extension=jar&extension=pom&page=2'])
  })

  it('asks for the default search when the address has no query', async () => {
    const client = await mountAt('/search')

    expect(searchCalls(client)).toEqual([''])
  })

  it('shows the text of the address in the box', async () => {
    await mountAt('/search?q=commons')

    expect(box().element.value).toBe('commons')
  })

  it('does not search again while the text is typed', async () => {
    const client = await mountAt('/search')

    await box().setValue('commons')
    await flushPromises()

    expect(searchCalls(client), 'only Enter runs the query').toHaveLength(1)
  })

  it('writes the text into the address on Enter and starts again at the first page', async () => {
    const client = await mountAt('/search?page=3&origin=hosted')

    await box().setValue('  commons-lang  ')
    await wrapper.find('form[role="search"]').trigger('submit')
    await flushPromises()

    expect(addressQuery()).toEqual({ q: 'commons-lang', origin: 'hosted' })
    expect(searchCalls(client).at(-1)).toBe('q=commons-lang&origin=hosted')
  })

  it('searches again when Enter is pressed with a query that is already in the address', async () => {
    const client = await mountAt('/search?q=commons')

    await wrapper.find('form[role="search"]').trigger('submit')
    await flushPromises()

    expect(searchCalls(client), 'the same query is asked for again').toHaveLength(2)
  })

  it('puts a chosen chip into the address and searches with it', async () => {
    const client = await mountAt('/search')

    await chip('extension', 'jar').trigger('click')
    await flushPromises()

    expect(addressQuery()).toEqual({ extension: ['jar'] })
    expect(searchCalls(client).at(-1)).toBe('extension=jar')
  })

  it('removes a value from the address when its chip is chosen again', async () => {
    await mountAt('/search?repository=releases&repository=snapshots')

    await chip('repository', 'releases').trigger('click')
    await flushPromises()

    expect(addressQuery()).toEqual({ repository: ['snapshots'] })
  })

  it('goes back to the earlier query with the back button', async () => {
    const client = await mountAt('/search')
    await chip('extension', 'jar').trigger('click')
    await flushPromises()

    router.back()
    await flushPromises()

    expect(addressQuery()).toEqual({})
    expect(searchCalls(client).at(-1), 'the request follows the address').toBe('')
  })

  it('follows the address into the box when the query changes under it', async () => {
    await mountAt('/search?q=commons')

    await router.push('/search?q=guava')
    await flushPromises()

    expect(box().element.value).toBe('guava')
  })

  it('writes the sort and the order into the address', async () => {
    await mountAt('/search')

    await wrapper.find('[data-test="sort"]').setValue('modified')
    await flushPromises()
    await wrapper.find('[data-test="order"]').setValue('desc')
    await flushPromises()

    expect(addressQuery()).toEqual({ sort: 'modified', order: 'desc' })
  })

  it('writes the origin and the release state of a single choice chip', async () => {
    await mountAt('/search')

    await chip('origin', 'mirror').trigger('click')
    await flushPromises()
    await chip('snapshot', 'false').trigger('click')
    await flushPromises()

    expect(addressQuery()).toEqual({ origin: 'mirror', snapshot: 'false' })
  })

  it('writes a coordinate filter into the address when the field is left', async () => {
    await mountAt('/search')

    const field = wrapper.find('[data-test="field-groupId"]')
    await field.setValue('org.apache')
    await field.trigger('change')
    await flushPromises()

    expect(addressQuery()).toEqual({ groupId: 'org.apache' })
  })

  it('writes the day of a date field as the UTC midnight that starts it', async () => {
    await mountAt('/search')

    const field = wrapper.find('[data-test="field-modifiedAfter"]')
    await field.setValue('2026-03-04')
    await field.trigger('change')
    await flushPromises()

    expect(addressQuery()).toEqual({ modifiedAfter: '2026-03-04T00:00:00Z' })
  })

  it('writes a size limit in bytes into the address', async () => {
    await mountAt('/search')

    const field = wrapper.find('[data-test="field-sizeMax"]')
    await field.setValue('1048576')
    await field.trigger('change')
    await flushPromises()

    expect(addressQuery()).toEqual({ sizeMax: '1048576' })
  })

  it('moves to the next page and back through the address', async () => {
    const client = await mountAt('/search')

    await wrapper.find('[data-test="next"]').trigger('click')
    await flushPromises()
    expect(addressQuery()).toEqual({ page: '1' })
    expect(searchCalls(client).at(-1)).toBe('page=1')

    await wrapper.find('[data-test="previous"]').trigger('click')
    await flushPromises()
    expect(addressQuery(), 'the first page leaves the address plain').toEqual({})
  })

  it('does not offer a page beyond the last one', async () => {
    await mountAt('/search?page=7')

    expect(wrapper.find('[data-test="next"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="page-position"]').text()).toBe('Page 8 of 8')
  })

  it('searches again for a sign in, because more repositories can be visible', async () => {
    const client = await mountAt('/search')

    sessionState.details.value = { permissions: [] }
    await flushPromises()

    expect(searchCalls(client), 'a changed session asks again').toHaveLength(2)
  })

  it('drops an answer that arrives after a newer query was asked for', async () => {
    const slow = []
    const client = clientAnswering({
      search: (query) => new Promise((resolve) => { slow.push({ query, resolve }) })
    })
    await mountAt('/search?q=old', { client })
    await router.push('/search?q=new')
    await flushPromises()

    slow[1].resolve({ data: answerPage({ items: [gav({ artifactId: 'new-artifact' })], total: 1 }) })
    await flushPromises()
    slow[0].resolve({ data: answerPage({ items: [gav({ artifactId: 'old-artifact' })], total: 1 }) })
    await flushPromises()

    expect(rows().map(row => row.text()).join(' ')).toContain('new-artifact')
    expect(rows().map(row => row.text()).join(' ')).not.toContain('old-artifact')
  })
})

describe('SearchView results', () => {
  it('lists a row per version with its coordinates', async () => {
    await mountAt('/search')

    expect(rows().map(row => row.find('[data-test="coordinates"]').text())).toEqual([
      'org.apache.commons:commons-lang3:3.14.0',
      'org.apache.commons:commons-io:2.16.1',
      'org.apache.commons:commons-text:1.12.0'
    ])
  })

  it('marks a version that has a newer one and leaves the latest alone', async () => {
    await mountAt('/search')

    expect(rows()[0].find('[data-test="newer"]').text()).toBe('Newer: 3.17.0')
    expect(rows()[1].find('[data-test="newer"]').exists()).toBe(false)
  })

  it('shows the repositories of a version, and a cached copy as cached from its proxy', async () => {
    await mountAt('/search')

    expect(rows()[0].findAll('[data-test="repository"]').map(entry => entry.text())).toEqual(['releases'])
    expect(rows()[1].findAll('[data-test="repository"]').map(entry => entry.text())).toEqual(['cached from central-proxy'])
  })

  it('shows the number of files and the day of the last change', async () => {
    await mountAt('/search')

    expect(rows()[0].find('[data-test="file-count"]').text()).toBe('8 files')
    expect(rows()[0].find('[data-test="modified"]').text()).toBe('Modified 2026-09-30')
  })

  it('links every version to the browser', async () => {
    await mountAt('/search')

    expect(rows()[0].find('[data-test="browse"]').attributes('href'))
      .toBe('/browse/releases/org/apache/commons/commons-lang3/3.14.0')
  })

  it('shows the file counts of the facets on the chips', async () => {
    await mountAt('/search')

    expect(chip('extension', 'jar').text()).toBe('jar 301')
    expect(chip('extension', 'jar').attributes('title')).toBe('301 files')
    expect(chip('classifier', '-').text()).toBe('none 150')
  })

  it('keeps a chosen value as a chip although the facet no longer lists it', async () => {
    await mountAt('/search?extension=war')

    expect(chip('extension', 'war').exists()).toBe(true)
    expect(chip('extension', 'war').attributes('aria-pressed')).toBe('true')
  })

  it('shows the capped total as more than 10 000', async () => {
    await mountAt('/search', {
      client: clientAnswering({ search: () => Promise.resolve({ data: answerPage({ total: 10001, totalCapped: true }) }) })
    })

    expect(wrapper.find('[data-test="count"]').text()).toBe('More than 10 000 results')
  })
})

describe('SearchView files of a version', () => {
  it('loads the files when a row opens, with the coordinates and the filters of the search', async () => {
    const client = await mountAt('/search?origin=mirror&repository=central-proxy')

    await rowToggles()[1].trigger('click')
    await flushPromises()

    expect(client.maven.searchFiles).toHaveBeenCalledWith({
      groupId: 'org.apache.commons', artifactId: 'commons-io', version: '2.16.1',
      origin: 'mirror', repository: ['central-proxy'], page: 0, size: 50
    })
  })

  it('lists the files of an open row with a link to each in the browser', async () => {
    await mountAt('/search')

    await rowToggles()[1].trigger('click')
    await flushPromises()

    const link = rows()[1].find('[data-test="file-link"]')
    expect(link.text()).toBe('commons-io-2.16.1-sources.jar')
    expect(link.attributes('href')).toBe('/browse/central-proxy/org/apache/commons/commons-io/2.16.1/commons-io-2.16.1-sources.jar')
  })

  it('says where a cached file comes from, with the upstream host when the API sends it', async () => {
    await mountAt('/search')

    await rowToggles()[1].trigger('click')
    await flushPromises()

    expect(rows()[1].find('[data-test="file-source"]').text()).toBe('cached from central-proxy (repo.maven.apache.org)')
  })

  it('does not ask for the files of a row twice', async () => {
    const client = await mountAt('/search')

    await rowToggles()[1].trigger('click')
    await flushPromises()
    await rowToggles()[1].trigger('click')
    await rowToggles()[1].trigger('click')
    await flushPromises()

    expect(client.maven.searchFiles).toHaveBeenCalledTimes(1)
  })

  it('shows an error with a retry when the files cannot be loaded', async () => {
    let calls = 0
    const client = clientAnswering({
      searchFiles: () => (++calls === 1 ? Promise.reject(failing(500, 'boom')) : Promise.resolve({ data: answerFiles() }))
    })
    await mountAt('/search', { client })

    await rowToggles()[0].trigger('click')
    await flushPromises()
    expect(rows()[0].find('[data-test="files-error"]').exists()).toBe(true)

    await rows()[0].find('[data-test="files"] button').trigger('click')
    await flushPromises()

    expect(rows()[0].find('[data-test="file"]').exists()).toBe(true)
  })

  it('offers the rest of the files when the answer holds only a page of them', async () => {
    const client = clientAnswering({ searchFiles: () => Promise.resolve({ data: answerFiles({ total: 120 }) }) })
    await mountAt('/search', { client })

    await rowToggles()[0].trigger('click')
    await flushPromises()
    await rows()[0].find('[data-test="more-files"]').trigger('click')
    await flushPromises()

    expect(client.maven.searchFiles.mock.calls[1][0]).toMatchObject({ page: 1 })
  })
})

describe('SearchView states', () => {
  it('shows skeleton rows while the answer is on its way, and the box stays usable', async () => {
    const client = clientAnswering({ search: () => new Promise(() => {}) })
    await mountAt('/search', { client })

    expect(wrapper.find('[data-test="skeleton"]').exists()).toBe(true)
    expect(wrapper.find('section[aria-busy="true"]').exists()).toBe(true)
    expect(box().attributes('disabled')).toBeUndefined()
  })

  it('says why nothing was found and which filters were active', async () => {
    const client = clientAnswering({ search: () => Promise.resolve({ data: answerPage({ items: [], total: 0 }) }) })
    await mountAt('/search?q=zzz&extension=war&snapshot=true', { client })

    expect(wrapper.find('[data-test="empty"]').text()).toContain('Nothing matches "zzz".')
    expect(wrapper.find('[data-test="empty-filters"]').text()).toBe('Active filters: extension war, snapshots only.')
  })

  it('resets the filters from the empty state and keeps the text', async () => {
    const client = clientAnswering({ search: () => Promise.resolve({ data: answerPage({ items: [], total: 0 }) }) })
    await mountAt('/search?q=zzz&extension=war', { client })

    await wrapper.find('[data-test="reset-all"]').trigger('click')
    await flushPromises()

    expect(addressQuery(), 'the empty state drops the filters and the text, the search starts over').toEqual({})
  })

  it('explains that a proxy lists an artifact after it has fetched it once, for a search of cached copies', async () => {
    const client = clientAnswering({ search: () => Promise.resolve({ data: answerPage({ items: [], total: 0 }) }) })
    await mountAt('/search?q=zzz&origin=mirror', { client })

    expect(wrapper.find('[data-test="empty-mirror"]').text()).toContain('after it has fetched it once')
  })

  it('does not give the proxy explanation when cached copies were not asked for', async () => {
    const client = clientAnswering({ search: () => Promise.resolve({ data: answerPage({ items: [], total: 0 }) }) })
    await mountAt('/search?q=zzz', { client })

    expect(wrapper.find('[data-test="empty-mirror"]').exists()).toBe(false)
  })

  it('shows the message of the failure with a retry that asks again', async () => {
    let calls = 0
    const client = clientAnswering({
      search: () => (++calls === 1 ? Promise.reject(failing(500, 'boom')) : Promise.resolve({ data: answerPage() }))
    })
    await mountAt('/search', { client })
    expect(wrapper.find('[data-test="failure"]').text()).toContain('Cannot search')

    await wrapper.find('[data-test="retry"]').trigger('click')
    await flushPromises()

    expect(rows()).toHaveLength(3)
  })

  it('shows the hint of the server for a result window that is too large', async () => {
    const client = clientAnswering({
      search: () => Promise.reject(failing(400, 'Result window is limited to 10000 entries, narrow the filters'))
    })
    await mountAt('/search?page=600&size=20', { client })

    expect(wrapper.find('[data-test="failure"]').text()).toContain('Result window is limited to 10000 entries, narrow the filters')
  })

  it('says that search is turned off for a 404 and offers no search box', async () => {
    const client = clientAnswering({ search: () => Promise.reject(failing(404, 'Not found')) })
    await mountAt('/search', { client })

    expect(wrapper.find('[data-test="disabled"]').text()).toBe('Search is turned off on this instance.')
    expect(wrapper.find('input').exists()).toBe(false)
    expect(wrapper.find('[data-test="failure"]').exists()).toBe(false)
  })

  it('shows the progress of an index that is still being built', async () => {
    const status = { state: 'BUILDING', lagSeconds: 0, repositoriesDone: 2, repositoriesTotal: 5 }
    const client = clientAnswering({ search: () => Promise.resolve({ data: answerPage({ indexStatus: status }) }) })
    await mountAt('/search', { client })

    expect(wrapper.find('[data-test="notice-building"]').text()).toBe('Indexing, 2 of 5 repositories. Results may be partial.')
  })

  const missing = () => clientAnswering({
    search: () => Promise.resolve({ data: answerPage({ items: [], total: 0, indexStatus: { state: 'MISSING', lagSeconds: 0 } }) })
  })

  it('tells a manager that nothing is indexed and links to the maintenance page', async () => {
    await mountAt('/search', { client: missing(), logged: true, allowed: ['ingot.admin.maintenance'] })

    expect(wrapper.find('[data-test="notice-missing"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="maintenance-link"]').attributes('href')).toBe('/maintenance')
  })

  it('tells a visitor that nothing is indexed without a link to a page they cannot open', async () => {
    await mountAt('/search', { client: missing() })

    expect(wrapper.find('[data-test="notice-missing"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="maintenance-link"]').exists()).toBe(false)
  })

  it('shows a quiet notice with the lag of a degraded index', async () => {
    const client = clientAnswering({
      search: () => Promise.resolve({ data: answerPage({ indexStatus: { state: 'DEGRADED', lagSeconds: 600 } }) })
    })
    await mountAt('/search', { client })

    expect(wrapper.find('[data-test="notice-degraded"]').text()).toBe('Results may lag by 10 minutes.')
  })

  it('shows no notice while the index is ready', async () => {
    await mountAt('/search')

    expect(wrapper.find('[role="status"][data-test^="notice"]').exists()).toBe(false)
  })

  it('tells an anonymous visitor that some repositories are hidden, without naming any', async () => {
    await mountAt('/search')

    const note = wrapper.find('[data-test="anonymous"]').text()
    expect(note).toContain('Some repositories are hidden')
    expect(note).not.toContain('releases')
  })

  it('leaves the note out for a signed in visitor', async () => {
    await mountAt('/search', { logged: true })

    expect(wrapper.find('[data-test="anonymous"]').exists()).toBe(false)
  })
})

describe('SearchView accessibility', () => {
  it('announces the number of results in a polite live region once the answer is in', async () => {
    await mountAt('/search')

    expect(liveRegion().attributes('aria-live')).toBe('polite')
    expect(liveRegion().text()).toBe('143 results')
  })

  it('announces that nothing was found', async () => {
    const client = clientAnswering({ search: () => Promise.resolve({ data: answerPage({ items: [], total: 0 }) }) })
    await mountAt('/search', { client })

    expect(liveRegion().text()).toBe('No results')
  })

  it('does not announce an old count while a new answer is awaited', async () => {
    const pending = []
    const client = clientAnswering({ search: () => new Promise((resolve) => { pending.push(resolve) }) })
    await mountAt('/search', { client })
    pending[0]({ data: answerPage() })
    await flushPromises()
    await router.push('/search?q=next')
    await flushPromises()

    expect(wrapper.find('[data-test="count"]').exists(), 'no visible count between two answers').toBe(false)
  })

  it('gives the chips a pressed state', async () => {
    await mountAt('/search?origin=mirror')

    expect(chip('origin', 'mirror').attributes('aria-pressed')).toBe('true')
    expect(chip('origin', 'hosted').attributes('aria-pressed')).toBe('false')
  })

  it('gives a row an expanded state and a labelled region for its files', async () => {
    await mountAt('/search')
    expect(rowToggles()[0].attributes('aria-expanded')).toBe('false')

    await rowToggles()[0].trigger('click')
    await flushPromises()

    const region = rows()[0].find('[role="region"]')
    expect(rowToggles()[0].attributes('aria-expanded')).toBe('true')
    expect(region.attributes('aria-labelledby')).toBe(rowToggles()[0].attributes('id'))
    expect(rowToggles()[0].attributes('aria-controls')).toBe(region.attributes('id'))
  })

  it('makes the search a landmark and groups each filter under a legend', async () => {
    await mountAt('/search')

    expect(wrapper.find('form[role="search"]').exists()).toBe(true)
    expect(wrapper.findAll('fieldset legend').map(legend => legend.text())).toContain('Repository')
  })

  it('keeps exactly one result row in the tab order', async () => {
    await mountAt('/search')

    expect(rowToggles().map(toggle => toggle.attributes('tabindex'))).toEqual(['0', '-1', '-1'])
  })
})

describe('SearchView keyboard', () => {
  it('focuses the box on a slash from the page', async () => {
    await mountAt('/search')

    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true }))
    await flushPromises()

    expect(document.activeElement).toBe(box().element)
  })

  it('types a slash into a field instead of moving the focus', async () => {
    await mountAt('/search')
    const field = wrapper.find('[data-test="field-groupId"]')
    field.element.focus()

    const event = new KeyboardEvent('keydown', { key: '/', bubbles: true, cancelable: true })
    field.element.dispatchEvent(event)

    expect(event.defaultPrevented, 'a slash in a field is text').toBe(false)
    expect(document.activeElement).toBe(field.element)
  })

  it('clears the box and takes the focus out of it on Escape', async () => {
    await mountAt('/search?q=commons')
    box().element.focus()

    await press(box(), 'Escape')

    expect(box().element.value).toBe('')
    expect(document.activeElement).not.toBe(box().element)
  })

  it('moves from the box to the first result on ArrowDown and to the last on ArrowUp', async () => {
    await mountAt('/search')

    await press(box(), 'ArrowDown')
    expect(document.activeElement).toBe(rowToggles()[0].element)

    box().element.focus()
    await press(box(), 'ArrowUp')
    expect(document.activeElement).toBe(rowToggles()[2].element)
  })

  it('moves between the result rows with the up and down arrows', async () => {
    await mountAt('/search')
    rowToggles()[0].element.focus()

    await press(rowToggles()[0], 'ArrowDown')
    expect(document.activeElement).toBe(rowToggles()[1].element)

    await press(rowToggles()[1], 'ArrowUp')
    expect(document.activeElement).toBe(rowToggles()[0].element)
  })

  it('leaves the first row for the box on ArrowUp', async () => {
    await mountAt('/search')
    rowToggles()[0].element.focus()

    await press(rowToggles()[0], 'ArrowUp')

    expect(document.activeElement).toBe(box().element)
  })

  it('makes the row with the focus the one tab stop', async () => {
    await mountAt('/search')
    rowToggles()[0].element.focus()

    await press(rowToggles()[0], 'ArrowDown')

    expect(rowToggles().map(toggle => toggle.attributes('tabindex'))).toEqual(['-1', '0', '-1'])
  })

  it('opens a row with ArrowRight and folds it with ArrowLeft', async () => {
    await mountAt('/search')
    rowToggles()[0].element.focus()

    await press(rowToggles()[0], 'ArrowRight')
    expect(rowToggles()[0].attributes('aria-expanded')).toBe('true')

    await press(rowToggles()[0], 'ArrowLeft')
    expect(rowToggles()[0].attributes('aria-expanded')).toBe('false')
  })

  it('opens the filter sheet and closes it on Escape', async () => {
    await mountAt('/search')

    await wrapper.find('[data-test="open-filters"]').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="open-filters"]').attributes('aria-expanded')).toBe('true')

    await press(wrapper.find('[data-test="filter-panel"]'), 'Escape')

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})
