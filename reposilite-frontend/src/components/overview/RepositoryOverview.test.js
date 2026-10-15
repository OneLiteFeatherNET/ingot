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
import RepositoryOverview from './RepositoryOverview.vue'

// The session is replaced per file; each test sets the client and the readiness it needs.
vi.mock('../../store/session', async () => {
  const { ref } = await import('vue')
  const state = {
    client: ref(null),
    sessionReady: ref(true),
    details: ref({ routes: [] })
  }
  return {
    sessionState: state,
    useSession: () => ({
      client: state.client,
      sessionReady: state.sessionReady,
      details: state.details,
      hasPermissionTo: () => false
    })
  }
})

vi.mock('../card/SnippetsCard.vue', () => ({
  default: { name: 'SnippetsCard', render: () => null }
}))

// The folder listing is what an older backend still shows; its own tests cover it.
vi.mock('../browser/FileBrowserView.vue', async () => {
  const { h } = await import('vue')
  return {
    default: { name: 'FileBrowserView', render: () => h('p', { 'data-test': 'folder-listing' }, 'folders') }
  }
})

const repositoryList = [
  { id: 'releases', visibility: 'PUBLIC', description: 'Released artifacts for the public.', tags: ['team-core', 'public'] },
  { id: 'snapshots', visibility: 'PUBLIC', description: 'Nightly builds, pruned after 30 days.', tags: ['team-core', 'ci'] },
  { id: 'private', visibility: 'PRIVATE', description: 'Internal libraries.', tags: ['internal'] },
  { id: 'archive', visibility: 'HIDDEN', description: '', tags: [] }
]

const routes = [
  { name: 'overview', path: '/' },
  { name: 'browse', path: '/browse/:qualifier(.*)*' }
]

const clientAnswering = (answer) => ({
  maven: { repositories: vi.fn(answer) }
})

const answerWith = (repositories = repositoryList) => () => Promise.resolve({ data: { repositories } })

let wrapper
let router

// Each test mounts its own memory router at the address it needs, so no address leaks between tests.
const mountAt = async (url, { answer = answerWith() } = {}) => {
  sessionState.client.value = clientAnswering(answer)
  sessionState.sessionReady.value = true
  router = createRouter({ history: createMemoryHistory(), routes })
  router.push(url)
  await router.isReady()
  wrapper = mount(RepositoryOverview, {
    props: { qualifier: { path: '', watchable: 0 } },
    global: { plugins: [router] },
    attachTo: document.body
  })
  await flushPromises()
  return wrapper
}

const search = () => wrapper.find('input')
const rows = () => wrapper.findAll('[data-test="repository-row"]')
const rowIds = () => rows().map(row => row.find('[data-test="repository-id"]').text())
const rowFor = (id) => rows().find(row => row.find('[data-test="repository-id"]').text() === id)
const filterChips = () => wrapper.findAll('[data-variant="filter"]')
const chipLabels = () => filterChips().map(chip => chip.text())
const chipNamed = (tag) => filterChips().find(chip => chip.text() === tag)
const clearButton = () => wrapper.findAll('button').find(button => button.text() === 'Clear filters')
const addressQuery = () => router.currentRoute.value.query
const loadCalls = () => sessionState.client.value.maven.repositories.mock.calls

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('RepositoryOverview list', () => {
  it('lists every repository the backend returns, sorted by id', async () => {
    await mountAt('/')

    expect(rowIds()).toEqual(['archive', 'private', 'releases', 'snapshots'])
  })

  it('shows the description and the tags under the id of each repository', async () => {
    await mountAt('/')

    const releases = rowFor('releases')
    expect(releases.text()).toContain('Released artifacts for the public.')
    expect(releases.findAll('[data-test="tag"]').map(tag => tag.text())).toEqual(['team-core', 'public'])
  })

  it('marks a private or hidden repository with its visibility and leaves a public one unmarked', async () => {
    await mountAt('/')

    expect(rowFor('private').find('[data-test="visibility"]').text()).toBe('Private')
    expect(rowFor('archive').find('[data-test="visibility"]').text()).toBe('Hidden')
    expect(rowFor('releases').find('[data-test="visibility"]').exists()).toBe(false)
  })

  it('opens a repository in the browser, at its own path', async () => {
    await mountAt('/')

    expect(rowFor('releases').attributes('href')).toBe('/browse/releases')
    await rowFor('releases').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/browse/releases')
  })

  it('asks the backend once and filters the list it has on the page', async () => {
    await mountAt('/')

    await search().setValue('nightly')
    await flushPromises()
    await search().setValue('')
    await flushPromises()

    expect(loadCalls(), 'typing does not request the list again').toHaveLength(1)
  })

  it('waits for the session before it asks for the list, and asks again when it is ready', async () => {
    sessionState.client.value = clientAnswering(answerWith())
    sessionState.sessionReady.value = false
    router = createRouter({ history: createMemoryHistory(), routes })
    router.push('/')
    await router.isReady()
    wrapper = mount(RepositoryOverview, {
      props: { qualifier: { path: '', watchable: 0 } },
      global: { plugins: [router] }
    })
    await flushPromises()

    expect(loadCalls(), 'nothing is asked before the session is known').toHaveLength(0)

    sessionState.sessionReady.value = true
    await flushPromises()

    expect(loadCalls()).toHaveLength(1)
  })
})

describe('RepositoryOverview search', () => {
  it('matches the id, the description and the tags as the user types', async () => {
    await mountAt('/')

    await search().setValue('nightly')
    await flushPromises()

    expect(rowIds()).toEqual(['snapshots'])
  })

  it('writes the search text into the address, so the address says what is shown', async () => {
    await mountAt('/')

    await search().setValue('internal')
    await flushPromises()

    expect(addressQuery().q).toBe('internal')
    expect(router.currentRoute.value.name, 'the page stays the overview').toBe('overview')
  })

  it('reads the search text from the address when the page opens', async () => {
    await mountAt('/?q=internal')

    expect(search().element.value).toBe('internal')
    expect(rowIds()).toEqual(['private'])
  })

  it('removes the search from the address when the field is emptied', async () => {
    await mountAt('/?q=internal')

    await search().setValue('')
    await flushPromises()

    expect(addressQuery().q).toBeUndefined()
  })
})

describe('RepositoryOverview tag filter', () => {
  it('shows the most used tags as filter chips, most used first and then by name', async () => {
    await mountAt('/')

    expect(chipLabels()).toEqual(['team-core', 'ci', 'internal', 'public'])
  })

  it('narrows the list to the repositories that carry every selected tag', async () => {
    await mountAt('/')

    await chipNamed('team-core').trigger('click')
    await flushPromises()
    await chipNamed('ci').trigger('click')
    await flushPromises()

    expect(rowIds()).toEqual(['snapshots'])
    expect(addressQuery().tag).toEqual(['team-core', 'ci'])
  })

  it('deselects a tag when its chip is pressed again, and takes it out of the address', async () => {
    await mountAt('/')
    await chipNamed('team-core').trigger('click')
    await flushPromises()

    await chipNamed('team-core').trigger('click')
    await flushPromises()

    expect(addressQuery().tag).toBeUndefined()
    expect(rowIds()).toEqual(['archive', 'private', 'releases', 'snapshots'])
  })

  it('reads the selected tags from the address, in lower case, and marks their chips as selected', async () => {
    await mountAt('/?tag=CI&tag=team-core')

    expect(rowIds()).toEqual(['snapshots'])
    expect(chipNamed('ci').attributes('aria-checked')).toBe('true')
    expect(chipNamed('team-core').attributes('aria-checked')).toBe('true')
  })

  it('keeps a selected tag among the chips even when it is not one of the most used', async () => {
    const many = Array.from({ length: 10 }, (_, index) => ({ id: `repo-${index}`, visibility: 'PUBLIC', description: '', tags: [`tag-${index}`] }))
    await mountAt('/?tag=tag-9', { answer: answerWith(many) })

    expect(chipLabels()).toContain('tag-9')
    expect(chipNamed('tag-9').attributes('aria-checked')).toBe('true')
  })
})

describe('RepositoryOverview empty and failing states', () => {
  it('says that nothing matches and offers to clear the filters', async () => {
    await mountAt('/')

    await search().setValue('zzz')
    await flushPromises()

    expect(wrapper.text()).toContain('No repository matches these filters.')
    expect(clearButton()).toBeDefined()
  })

  it('clears the search and the tags from the address with Clear filters', async () => {
    await mountAt('/?q=zzz&tag=ci')

    await clearButton().trigger('click')
    await flushPromises()

    expect(addressQuery()).toEqual({})
    expect(rowIds()).toEqual(['archive', 'private', 'releases', 'snapshots'])
  })

  it('says that there are no repositories yet when the backend has none', async () => {
    await mountAt('/', { answer: answerWith([]) })

    expect(wrapper.text()).toContain('No repositories yet.')
    expect(clearButton()).toBeUndefined()
  })

  it('shows the failure with a retry, and asks again when the retry is pressed', async () => {
    const answer = vi.fn()
      .mockRejectedValueOnce({ response: { status: 500, data: { message: 'Boom' } } })
      .mockImplementationOnce(answerWith())
    await mountAt('/', { answer })

    expect(wrapper.text()).toContain('Cannot load repositories')
    await wrapper.findAll('button').find(button => button.text() === 'Retry').trigger('click')
    await flushPromises()

    expect(rowIds()).toEqual(['archive', 'private', 'releases', 'snapshots'])
  })

  it('keeps the folder listing when the backend does not have the endpoint, as an older backend does', async () => {
    await mountAt('/', { answer: () => Promise.reject({ response: { status: 404, data: {} } }) })

    expect(wrapper.find('[data-test="folder-listing"]').exists()).toBe(true)
    expect(rows()).toHaveLength(0)
  })
})

describe('RepositoryOverview Material 3 structure', () => {
  it('draws the search input with the embedded-input reset, so the pill is the only box', async () => {
    await mountAt('/')

    expect(search().classes()).toContain('md-embedded-input')
  })

  it('makes each repository row a link named after the repository, covering the whole row', async () => {
    await mountAt('/')

    const row = rowFor('releases')
    expect(row.element.tagName).toBe('A')
    expect(row.attributes('aria-label')).toBe('Browse releases')
    expect(row.classes()).toContain('md-state-layer')
  })

  it('draws the rows as one list, with a leading avatar on each row', async () => {
    await mountAt('/')

    expect(wrapper.find('ul').findAll('li')).toHaveLength(4)
    expect(rowFor('releases').find('span.rounded-corner-full').classes()).toContain('bg-secondary-container')
  })

  it('shows the visibility as a label with an icon and no coloured fill', async () => {
    await mountAt('/')

    const label = rowFor('private').find('[data-test="visibility"]')
    expect(label.find('svg').exists()).toBe(true)
    expect(label.classes()).not.toContain('bg-secondary-container')
    expect(label.classes()).not.toContain('bg-tertiary-container')
  })

  it('gives the selected filter chip a check mark and reports it as checked', async () => {
    await mountAt('/?tag=ci')

    const selected = chipNamed('ci')
    expect(selected.attributes('aria-checked')).toBe('true')
    expect(selected.find('svg').exists()).toBe(true)
    expect(chipNamed('team-core').attributes('aria-checked')).toBe('false')
  })

  it('offers a clear button in the search bar only while there is text', async () => {
    await mountAt('/')
    expect(wrapper.find('button[aria-label="Clear search"]').exists()).toBe(false)

    await search().setValue('rel')
    await flushPromises()

    expect(wrapper.find('button[aria-label="Clear search"]').exists()).toBe(true)
  })
})
