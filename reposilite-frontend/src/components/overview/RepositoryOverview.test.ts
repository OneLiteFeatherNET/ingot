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

import { describe, it, expect, vi, beforeEach, afterEach, type Mock } from 'vitest'
import { mount, flushPromises, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { createRouter, createMemoryHistory, type Router, type RouteRecordRaw } from 'vue-router'
import type { Ref } from 'vue'
import * as sessionModule from '../../store/session'
import RepositoryOverview from './RepositoryOverview.vue'
import { LIST_DETAILS_KEY } from './listDetails'

// The client the page asks for the list, with the mock of its one call.
interface TestClient {
  maven: { repositories: Mock<() => Promise<unknown>> }
}

// The session state the mock below provides. The real module does not export it.
interface SessionState {
  client: Ref<TestClient | null>
  sessionReady: Ref<boolean>
  details: Ref<unknown>
}

const { sessionState } = sessionModule as unknown as { sessionState: SessionState }

// A repository as a test lists it. A case leaves out the fields it does not use.
interface RepositoryFixture {
  id: string
  visibility: string
  description?: string
  tags?: string[]
  type?: string
  typeExplicit?: boolean
  writePolicy?: string | null
}

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

const repositoryList: RepositoryFixture[] = [
  { id: 'releases', visibility: 'PUBLIC', description: 'Released artifacts for the public.', tags: ['team-core', 'public'] },
  { id: 'snapshots', visibility: 'PUBLIC', description: 'Nightly builds, pruned after 30 days.', tags: ['team-core', 'ci'] },
  { id: 'private', visibility: 'PRIVATE', description: 'Internal libraries.', tags: ['internal'] },
  { id: 'archive', visibility: 'HIDDEN', description: '', tags: [] }
]

// Name and path only: the routes are never rendered, so they carry no component.
const routes = [
  { name: 'overview', path: '/' },
  { name: 'browse', path: '/browse/:qualifier(.*)*' },
  { name: 'search', path: '/search' }
] as unknown as RouteRecordRaw[]

const clientAnswering = (answer: () => Promise<unknown>): TestClient => ({
  maven: { repositories: vi.fn(answer) }
})

const answerWith = (repositories: RepositoryFixture[] = repositoryList) => () => Promise.resolve({ data: { repositories } })

let wrapper: VueWrapper
let router: Router

// Each test mounts its own memory router at the address it needs, so no address leaks between tests.
const mountAt = async (url: string, { answer = answerWith() }: { answer?: () => Promise<unknown> } = {}) => {
  sessionState.client.value = clientAnswering(answer)
  sessionState.sessionReady.value = true
  router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(url)
  await router.isReady()
  wrapper = mount(RepositoryOverview, {
    props: { qualifier: { path: '', watchable: 0 } },
    global: { plugins: [router] },
    attachTo: document.body
  }) as VueWrapper
  await flushPromises()
  return wrapper
}

const search = () => wrapper.find<HTMLInputElement>('input')
const rows = () => wrapper.findAll('[data-test="repository-row"]')
const rowIds = () => rows().map(row => row.find('[data-test="repository-id"]').text())
// The row of a repository the case has already seen in the list, so it is always there.
const rowFor = (id: string): DOMWrapper<Element> => rows().find(row => row.find('[data-test="repository-id"]').text() === id)!
const filterChips = () => wrapper.findAll('[data-test="filter-tag"]')
const chipLabels = () => filterChips().map(chip => chip.text())
// The chip of a tag the case has already seen in the chips, so it is always there.
const chipNamed = (tag: string): DOMWrapper<Element> => filterChips().find(chip => chip.text() === tag)!
const clearButton = () => wrapper.findAll('button').find(button => button.text() === 'Clear filters')
const addressQuery = () => router.currentRoute.value.query
const loadCalls = () => sessionState.client.value!.maven.repositories.mock.calls

// The store the Details choice is kept in. Each test gets a fresh one, so no choice leaks from one test to the next.
const memoryStorage = () => {
  const items = new Map<string, string>()
  return {
    getItem: (key: string) => items.get(key) ?? null,
    setItem: (key: string, value: string) => {
      items.set(key, value)
    },
    items
  }
}
let storage: ReturnType<typeof memoryStorage>

// The Details switch of the list, and the switch's own label, which is the text of the label tied to it.
const detailsSwitch = () => wrapper.get('[role="switch"]')
const detailsLabel = () => wrapper.get(`label[for="${detailsSwitch().attributes('id')}"]`).text()
const showDetails = async () => {
  await detailsSwitch().trigger('click')
}

beforeEach(() => {
  storage = memoryStorage()
  vi.stubGlobal('localStorage', storage)
})

afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
})

describe('RepositoryOverview list', () => {
  it('draws the list with the 1 pixel outline edge of the surface table', async () => {
    await mountAt('/')

    const list = rowFor('releases').element.closest('ul')
    expect(list, 'the rows sit in a list').not.toBeNull()
    expect(list!.classList.contains('border-outline'), 'the list block has the outline edge').toBe(true)
  })

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
    await showDetails()

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
    await router.push('/')
    await router.isReady()
    wrapper = mount(RepositoryOverview, {
      props: { qualifier: { path: '', watchable: 0 } },
      global: { plugins: [router] }
    }) as VueWrapper
    await flushPromises()

    expect(loadCalls(), 'nothing is asked before the session is known').toHaveLength(0)

    sessionState.sessionReady.value = true
    await flushPromises()

    expect(loadCalls()).toHaveLength(1)
  })
})

describe('RepositoryOverview layout', () => {
  it('lists a repository once when the backend answers its id twice', async () => {
    const duplicated = [
      { id: 'snapshots', visibility: 'PUBLIC', description: '', tags: [] },
      { id: 'snapshots', visibility: 'PUBLIC', description: 'Nightly builds.', tags: ['ci'] },
      { id: 'releases', visibility: 'PUBLIC', description: '', tags: [] }
    ]
    await mountAt('/', { answer: answerWith(duplicated) })

    expect(rowIds()).toEqual(['releases', 'snapshots'])
    expect(rowFor('snapshots').find('[data-test="repository-description"]').exists(), 'the first answer for an id is the one shown').toBe(false)
  })

  it('counts each repository once, although the backend answered its id twice', async () => {
    const duplicated = [
      { id: 'snapshots', visibility: 'PUBLIC', description: '', tags: [] },
      { id: 'snapshots', visibility: 'PUBLIC', description: '', tags: [] }
    ]
    await mountAt('/', { answer: answerWith(duplicated) })

    expect(wrapper.find('[role="status"]').text()).toBe('1 repositories')
  })

  it('gives the chip row vertical padding and a negative top margin, so its focus rings and touch targets are not clipped', async () => {
    await mountAt('/')

    const row = wrapper.find('[role="group"][aria-label="Filter by tag"]')
    expect(row.classes()).toEqual(expect.arrayContaining(['overflow-x-auto', 'pt-2', 'pb-2', '-mt-2']))
  })

  it('centres the avatar and the text of a one-line row vertically', async () => {
    await mountAt('/')

    const row = rowFor('archive')
    expect(row.classes()).toContain('items-center')
    expect(row.classes()).not.toContain('items-start')
    expect(row.classes()).toContain('py-3')
  })

  it('top-aligns a multi-line row, with the avatar at the top of the text and 16px of padding', async () => {
    await mountAt('/')

    const row = rowFor('releases')
    expect(row.classes()).toContain('items-start')
    expect(row.classes()).not.toContain('items-center')
    expect(row.classes()).toContain('py-4')
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

describe('RepositoryOverview filter text', () => {
  it('reads a tag:name word as a tag, as the Administration page does', async () => {
    await mountAt('/')

    await search().setValue('tag:ci')
    await flushPromises()

    expect(rowIds(), 'only the repository with the tag ci is shown').toEqual(['snapshots'])
    expect(wrapper.find('[role="status"]').text()).toBe('1 of 4 repositories')
  })

  it('still matches the other words of the text next to a tag word', async () => {
    await mountAt('/')

    await search().setValue('tag:team-core nightly')
    await flushPromises()

    expect(rowIds()).toEqual(['snapshots'])
  })
})

describe('RepositoryOverview no match', () => {
  it('offers a link to the artifact search with the text already entered', async () => {
    await mountAt('/?q=guava')

    const link = wrapper.findAll('a').find(anchor => anchor.text() === 'Search artifacts for guava')
    expect(link, 'the empty state links to the artifact search').toBeDefined()
    expect(link!.attributes('href')).toBe('/search?q=guava')
  })

  it('keeps Clear filters next to the artifact search link', async () => {
    await mountAt('/?q=guava')

    expect(clearButton()).toBeDefined()
  })

  it('offers no artifact search link when only a tag is selected, because the search cannot express it', async () => {
    await mountAt('/?tag=nothing-carries-this')

    expect(wrapper.text()).toContain('No repository matches these filters.')
    expect(wrapper.findAll('a').find(anchor => anchor.text().startsWith('Search artifacts'))).toBeUndefined()
    expect(clearButton()).toBeDefined()
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

    await clearButton()!.trigger('click')
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
    await wrapper.findAll('button').find(button => button.text() === 'Retry')!.trigger('click')
    await flushPromises()

    expect(rowIds()).toEqual(['archive', 'private', 'releases', 'snapshots'])
  })

  it('shows the failure as an error state panel with a tonal retry', async () => {
    await mountAt('/', { answer: () => Promise.reject(Object.assign(new Error('Boom'), { response: { status: 500, data: { message: 'Boom' } } })) })

    const panel = wrapper.find('[role="alert"]')
    expect(panel.text(), 'the panel names what went wrong').toContain('Cannot load repositories')
    expect(panel.findAll('button').find(button => button.text() === 'Retry')?.attributes('data-variant'), 'the retry is a tonal button').toBe('tonal')
  })

  it('draws the loading line with shared skeleton bars, without a pulsing box', async () => {
    await mountAt('/', { answer: () => new Promise(() => {}) })

    const loading = wrapper.find('[aria-busy="true"]')
    expect(loading.findAll('[data-shape="bar"]').length, 'the loading line is drawn with shared bars').toBeGreaterThan(0)
    expect(loading.find('.animate-pulse').exists(), 'the loading line does not pulse on its own').toBe(false)
  })

  it('keeps the folder listing when the backend does not have the endpoint, as an older backend does', async () => {
    await mountAt('/', { answer: () => Promise.reject(Object.assign(new Error('Not Found'), { response: { status: 404, data: {} } })) })

    expect(wrapper.find('[data-test="folder-listing"]').exists()).toBe(true)
    expect(rows()).toHaveLength(0)
  })
})

describe('RepositoryOverview Material 3 structure', () => {
  it('draws the filter as the outlined field of the Administration page, with the filter icon', async () => {
    await mountAt('/')

    expect(wrapper.find('label').text(), 'the field is labelled as a filter').toBe('Filter repositories')
    expect(search().classes(), 'the field has the outlined border').toContain('rounded-corner-extra-small')
    expect(wrapper.find('.md-embedded-input').exists(), 'the pill of the search bar is gone').toBe(false)
  })

  it('makes the filter a search field, so assistive technology announces it as one', async () => {
    await mountAt('/')

    expect(search().attributes('type'), 'the input has the searchbox role').toBe('search')
  })

  it('does not make the filter a search landmark', async () => {
    await mountAt('/')

    expect(wrapper.find('[role="search"]').exists(), 'the rail owns the only search landmark').toBe(false)
  })

  it('explains the filter under the field, with the tag syntax', async () => {
    await mountAt('/')

    expect(wrapper.text()).toContain('Search by id, description or tag. Use tag:name for one tag.')
    expect(search().attributes('aria-describedby'), 'the supporting text is linked to the field').toBeTruthy()
  })

  it('keeps the count in a status region that is already there while the list loads', async () => {
    await mountAt('/', { answer: () => new Promise(() => {}) })

    const status = wrapper.find('[role="status"]')
    expect(status.exists(), 'the region exists while the list is loading').toBe(true)
    expect(status.text()).toBe('')
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
    await showDetails()

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

describe('RepositoryOverview layout order', () => {
  it('lists the repositories before the snippet card in the reading order', async () => {
    await mountAt('/')

    const [first, second] = (wrapper.element as HTMLElement).firstElementChild?.children ?? []
    expect(first.tagName, 'the list is the first block, so a narrow screen shows it first').toBe('SECTION')
    expect(second.className, 'the snippet card follows the list').toContain('large:col-start-2')
  })
})

describe('RepositoryOverview width', () => {
  it('keeps the list and the snippet card side by side from the large window class (1240 pixels)', async () => {
    await mountAt('/')

    expect((wrapper.element as HTMLElement).firstElementChild?.className, 'the card keeps its column on the right from 840 pixels').toContain(
      'large:grid-cols-[minmax(0,1fr)_minmax(320px,400px)]'
    )
  })

  it('keeps the details card at its own width on wide screens, so the list does not stretch with the window', async () => {
    await mountAt('/')

    expect((wrapper.element as HTMLElement).firstElementChild?.className, 'the card column is the same from 1920 pixels').not.toContain('min-[1920px]:grid-cols-[2fr_1fr]')
  })

  it('renders its page container through MdPage at the full width, without a container or width class of its own', async () => {
    await mountAt('/')

    expect(wrapper.attributes('style'), 'the page shell lifts the width steps, so the list uses the full window width').toContain('max-width: none')
    expect(wrapper.classes(), 'the page shell writes the container class').toContain('container')
    expect(wrapper.classes(), 'the page container is not centred').not.toContain('mx-auto')
    expect(wrapper.classes().filter(name => name.startsWith('max-w-')), 'the page container takes no max-w-* class').toEqual([])
    expect(wrapper.classes(), 'the page shell draws the top spacing of the page').toContain('pt-7')
    expect(wrapper.classes(), 'the page shell draws the bottom spacing of the page').toContain('pb-12')
    expect(wrapper.classes(), 'the view writes no bottom margin of its own').not.toContain('mb-12')
    expect((wrapper.element as HTMLElement).firstElementChild?.className, 'the grid inside the page writes no top spacing of its own').not.toMatch(/(?<![\w-])pt-7(?![\w-])/)
    expect((wrapper.element as HTMLElement).firstElementChild?.className, 'only the page shell writes the container class').not.toMatch(/(?<![-\w])container(?![-\w])/)
  })
})

describe('RepositoryOverview row marks', () => {
  it('keeps the type badge and the visibility label in one wrapping element after the text', async () => {
    await mountAt('/')
    await showDetails()

    const row = rowFor('private')
    const marks = row.find('[data-test="marks"]')
    expect(marks.classes(), 'the marks wrap onto a line of their own at compact widths').toContain('flex-wrap')
    expect(marks.find('[data-test="type-badge"]').exists(), 'the type badge is one of the marks').toBe(true)
    expect(marks.find('[data-test="visibility"]').exists(), 'the visibility label is one of the marks').toBe(true)

    const children = [...row.element.children]
    const text = children.find(child => child.classList.contains('flex-1'))
    expect(children.indexOf(marks.element), 'the marks come after the text column').toBeGreaterThan(children.indexOf(text!))
  })
})

describe('RepositoryOverview repository types', () => {
  const typedList = [
    { id: 'releases', visibility: 'PUBLIC', type: 'HOSTED', typeExplicit: true },
    { id: 'central', visibility: 'PUBLIC', type: 'PROXY', typeExplicit: true },
    { id: 'all', visibility: 'PUBLIC', type: 'GROUP', typeExplicit: true },
    { id: 'legacy', visibility: 'PUBLIC', type: 'mixed', typeExplicit: false }
  ]

  it('shows the type of every repository in its row', async () => {
    await mountAt('/', { answer: answerWith(typedList) })

    expect(rowFor('releases').find('[data-test="type-badge"]').text()).toBe('Hosted')
    expect(rowFor('central').find('[data-test="type-badge"]').text()).toBe('Proxy')
    expect(rowFor('all').find('[data-test="type-badge"]').text()).toBe('Group')
  })

  it('marks an untyped repository as legacy, with the hint that says what it does', async () => {
    await mountAt('/', { answer: answerWith(typedList) })

    const badge = rowFor('legacy').find('[data-test="type-badge"]')
    expect(badge.text()).toContain('Mixed')
    expect(badge.attributes('title')).toBe('No type set, behaves as before. Set a type to enforce the rules.')
  })

  it('offers one filter chip per type, after the tag chips', async () => {
    await mountAt('/', { answer: answerWith(typedList) })

    const typeChips = wrapper.findAll('[data-test="filter-type"]').map(chip => chip.text())
    expect(typeChips).toEqual(['Hosted', 'Proxy', 'Group', 'Mixed'])
  })

  it('groups the declared types apart from the derived Mixed', async () => {
    await mountAt('/', { answer: answerWith(typedList) })

    const declared = wrapper.find('[role="group"][aria-label="Repository type"]')
    const derived = wrapper.find('[role="group"][aria-label="Derived type"]')
    expect(declared.findAll('[data-test="filter-type"]').map(chip => chip.text()), 'the declared types').toEqual(['Hosted', 'Proxy', 'Group'])
    expect(derived.findAll('[data-test="filter-type"]').map(chip => chip.text()), 'the derived type').toEqual(['Mixed'])
  })

  it('says in the derived group what Mixed means, and links the sentence to the group', async () => {
    await mountAt('/', { answer: answerWith(typedList) })

    const derived = wrapper.find('[role="group"][aria-label="Derived type"]')
    const described = wrapper.find(`[id="${derived.attributes('aria-describedby')}"]`)
    expect(described.exists(), 'the group points at its explanation').toBe(true)
    expect(described.text()).toBe('Mixed: no type is declared, and the repository proxies both local and remote repositories.')
  })

  it('shows only the repositories of the chosen type and writes the choice to the address', async () => {
    await mountAt('/', { answer: answerWith(typedList) })

    const proxyChip = wrapper.findAll('[data-test="filter-type"]').find(chip => chip.text() === 'Proxy')
    await proxyChip!.trigger('click')
    await flushPromises()

    expect(rowIds()).toEqual(['central'])
    expect([addressQuery().type].flat()).toEqual(['proxy'])
  })

  it('reads the chosen types from the address, so a shared link shows the same list', async () => {
    await mountAt('/?type=group&type=mixed', { answer: answerWith(typedList) })

    expect(rowIds()).toEqual(['all', 'legacy'])
    const selected = wrapper.findAll('[data-test="filter-type"]').filter(chip => chip.attributes('aria-pressed') === 'true' || chip.attributes('aria-checked') === 'true')
    expect(selected.map(chip => chip.text())).toEqual(['Group', 'Mixed'])
  })

  it('counts the repositories of the type filter against the whole list', async () => {
    await mountAt('/?type=hosted', { answer: answerWith(typedList) })

    expect(wrapper.get('[role="status"]').text()).toBe('1 of 4 repositories')
  })

  it('clears the type filter together with the search and the tags', async () => {
    await mountAt('/?type=hosted&q=nothing-matches', { answer: answerWith(typedList) })
    await clearButton()!.trigger('click')
    await flushPromises()

    expect(addressQuery().type).toBeUndefined()
    expect(rowIds()).toEqual(['all', 'central', 'legacy', 'releases'])
  })
})

describe('RepositoryOverview write policy', () => {
  it('shows the write policy of every repository that takes uploads, and none for a proxy or a group', async () => {
    await mountAt('/', {
      answer: answerWith([
        { id: 'releases', visibility: 'PUBLIC', description: '', tags: [], type: 'hosted', typeExplicit: true, writePolicy: 'ALLOW' },
        { id: 'frozen', visibility: 'PUBLIC', description: '', tags: [], type: 'hosted', typeExplicit: true, writePolicy: 'READ_ONLY' },
        { id: 'central', visibility: 'PUBLIC', description: '', tags: [], type: 'proxy', typeExplicit: true, writePolicy: null }
      ])
    })
    await showDetails()

    expect(rowFor('releases').find('[data-test="write-policy"]').text()).toBe('Allow redeploy')
    expect(rowFor('frozen').find('[data-test="write-policy"]').text()).toBe('Read-only')
    expect(rowFor('central').find('[data-test="write-policy"]').exists()).toBe(false)
  })
})

// Repositories that show every kind of detail: a private one that takes uploads, a read-only one, and a proxy.
const policyList: RepositoryFixture[] = [
  { id: 'releases', visibility: 'PRIVATE', description: '', tags: [], type: 'hosted', typeExplicit: true, writePolicy: 'ALLOW' },
  { id: 'frozen', visibility: 'PUBLIC', description: '', tags: [], type: 'hosted', typeExplicit: true, writePolicy: 'READ_ONLY' },
  { id: 'central', visibility: 'PUBLIC', description: '', tags: [], type: 'proxy', typeExplicit: true, writePolicy: null }
]

describe('RepositoryOverview details switch', () => {
  it('hides the visibility and the write policy of every row by default, and keeps the type badge', async () => {
    await mountAt('/', { answer: answerWith(policyList) })

    expect(detailsSwitch().attributes('aria-checked'), 'the switch starts off').toBe('false')
    expect(wrapper.findAll('[data-test="visibility"]')).toHaveLength(0)
    expect(wrapper.findAll('[data-test="write-policy"]')).toHaveLength(0)
    expect(wrapper.findAll('[data-test="type-badge"]')).toHaveLength(policyList.length)
  })

  it('labels the switch Details, and reports its state through aria-checked', async () => {
    await mountAt('/', { answer: answerWith(policyList) })

    expect(detailsLabel()).toBe('Details')
    await showDetails()
    expect(detailsSwitch().attributes('aria-checked')).toBe('true')
    expect(detailsSwitch().attributes('role')).toBe('switch')
  })

  it('shows the visibility and the write policy of every row once the switch is on', async () => {
    await mountAt('/', { answer: answerWith(policyList) })
    await showDetails()

    expect(rowFor('releases').find('[data-test="visibility"]').text()).toBe('Private')
    expect(rowFor('releases').find('[data-test="write-policy"]').text()).toBe('Allow redeploy')
    expect(rowFor('frozen').find('[data-test="write-policy"]').text()).toBe('Read-only')
    expect(rowFor('central').find('[data-test="write-policy"]').exists()).toBe(false)
  })

  it('puts the type badge last among the marks, after the visibility and the write policy, and before the chevron', async () => {
    await mountAt('/', { answer: answerWith(policyList) })
    await showDetails()

    const row = rowFor('releases')
    const marks = row.findAll('[data-test="visibility"], [data-test="write-policy"], [data-test="type-badge"]')
    expect(marks.map(mark => mark.attributes('data-test'))).toEqual(['visibility', 'write-policy', 'type-badge'])

    const marksBox = row.get('[data-test="marks"]')
    expect(marksBox.element.lastElementChild?.getAttribute('data-test'), 'the type badge is the last mark').toBe('type-badge')
    expect(marksBox.element.nextElementSibling?.tagName.toLowerCase(), 'the chevron comes straight after the marks').toBe('svg')
  })

  it('leaves the write policy and the visibility out of the text of a row while the details are hidden', async () => {
    await mountAt('/', { answer: answerWith(policyList) })

    const row = rowFor('releases')
    expect(row.text()).not.toContain('Private')
    expect(row.text()).not.toContain('Allow redeploy')
    expect(row.attributes('aria-describedby'), 'no description points at the hidden details').toBeUndefined()
  })

  it('remembers the choice for this viewer, so a page opened later starts with the details on', async () => {
    await mountAt('/', { answer: answerWith(policyList) })
    await showDetails()
    expect(storage.items.get(LIST_DETAILS_KEY)).toBe('true')

    wrapper.unmount()
    await mountAt('/', { answer: answerWith(policyList) })

    expect(detailsSwitch().attributes('aria-checked')).toBe('true')
    expect(rowFor('releases').find('[data-test="visibility"]').exists()).toBe(true)
  })

  it('forgets the choice when the switch is turned off again', async () => {
    await mountAt('/', { answer: answerWith(policyList) })
    await showDetails()
    await showDetails()

    expect(storage.items.get(LIST_DETAILS_KEY)).toBe('false')
    expect(wrapper.findAll('[data-test="visibility"]')).toHaveLength(0)
  })

  it('still lists the repositories and switches the details when storage is blocked', async () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new DOMException('Access is denied for this document.', 'SecurityError')
      },
      setItem: () => {
        throw new DOMException('Access is denied for this document.', 'SecurityError')
      }
    })
    await mountAt('/', { answer: answerWith(policyList) })
    expect(rowIds()).toEqual(['central', 'frozen', 'releases'])

    await showDetails()

    expect(detailsSwitch().attributes('aria-checked')).toBe('true')
    expect(rowFor('releases').find('[data-test="visibility"]').exists()).toBe(true)
  })
})

describe('RepositoryOverview page heading', () => {
  it('names the page with exactly one level one heading, Repositories', async () => {
    await mountAt('/')

    const headings = wrapper.findAll('h1')
    expect(headings.length, 'the overview is the page at /, so it carries the one h1').toBe(1)
    expect(headings[0].text()).toBe('Repositories')
  })

  it('starts the outline at level one and names the repository list with the page heading', async () => {
    await mountAt('/')

    const levels = wrapper.findAll('h1, h2, h3, h4, h5, h6').map(heading => heading.element.tagName)
    expect(levels[0], 'no lower heading comes before the page heading, so the card title (h2) sits under it').toBe('H1')
    const list = wrapper.get('section[aria-labelledby]')
    expect(wrapper.get(`#${list.attributes('aria-labelledby')}`).element.tagName, 'the list region is named by the page heading').toBe('H1')
  })
})
