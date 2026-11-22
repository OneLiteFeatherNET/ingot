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

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import IndexPanel from './IndexPanel.vue'

// The error shape the mocked errorMessage reads: a plain Error, or an HTTP error with a response body.
type ErrorLike = (Error & { response?: { data?: { message?: string } } }) | undefined

// The client is swapped per test through this holder. The panel reads it through the session.
const session = vi.hoisted(() => ({ client: null as unknown as ReturnType<typeof fakeClient> }))
const toast = vi.hoisted(() => ({ createErrorToast: vi.fn(), createSuccessToast: vi.fn() }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: toast.createSuccessToast,
  createErrorToast: toast.createErrorToast,
  createWarningToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error: ErrorLike) => error?.response?.data?.message || error?.message || `${error}`
}))

const SECONDS = 1760931012.5
const POLL_MS = 2000

const counters = (overrides = {}) => ({
  processed: 0, upserted: 0, deleted: 0, unparsed: 0, skippedTooLong: 0, errors: 0, ...overrides
})

const run = (overrides = {}) => ({
  id: 7,
  kind: 'REBUILD',
  trigger: 'MANUAL',
  status: 'RUNNING',
  startedAt: SECONDS,
  finishedAt: null,
  leaseOwner: 'ingot-1#3',
  leaseUntil: SECONDS + 120,
  repositories: null,
  cursorRepository: null,
  cursorPath: null,
  counters: counters(),
  lastError: null,
  ...overrides
})

const indexBody = (overrides = {}) => ({
  state: 'READY',
  lagSeconds: 0,
  lastFullRebuild: SECONDS,
  rows: 1834,
  outbox: { pending: 0, deadLetters: 0, droppedEnqueues: 0 },
  repositories: ['releases', 'snapshots'],
  engine: 'database',
  activeRun: null,
  runs: [],
  lastVerify: null,
  ...overrides
})

const httpError = (status: number, message = `Request failed with status code ${status}`) =>
  Object.assign(new Error(message), { response: { status, data: { status, message } } })

// The Jobs endpoint answers the latest run of a kind; the panel asks it for one run of each index kind.
const jobPage = (items: unknown[] = []) => ({ items, nextCursor: null, sourceErrors: [], kinds: ['index.rebuild', 'index.verify'] })

const fakeClient = (overrides = {}, jobs: Record<string, unknown> = {}) => ({
  maven: {
    searchIndex: vi.fn(() => Promise.resolve({ data: indexBody() })),
    startIndexRebuild: vi.fn(() => Promise.resolve({ status: 202, data: { runId: 8 } })),
    startIndexVerify: vi.fn(() => Promise.resolve({ status: 202, data: { runId: 9 } })),
    cancelIndexRun: vi.fn(() => Promise.resolve({ status: 204, data: '' })),
    ...overrides
  },
  jobs: {
    list: vi.fn(() => Promise.resolve({ data: jobPage() })),
    ...jobs
  }
})

let wrapper: VueWrapper

const mountPanel = async () => {
  wrapper = mount(IndexPanel, {
    attachTo: document.body,
    global: { stubs: { RouterLink: RouterLinkStub } }
  }) as VueWrapper
  await flushPromises()
  return wrapper
}

// The shared cards of the panel that carry the given data attribute, and the props of a shared component as a record.
const cardsWith = (attribute: string) => (wrapper.findAllComponents({ name: 'MdCard' }) as VueWrapper[])
  .filter(card => card.attributes(attribute) !== undefined)
const propsOf = (component: VueWrapper) => component.props() as Record<string, unknown>

const buttonNamed = (text: string) => wrapper.findAll('button').find(button => button.text() === text)!
const dialogButton = (id: string) => document.getElementById(id) as HTMLElement

beforeEach(() => {
  session.client = fakeClient()
  toast.createErrorToast.mockClear()
  toast.createSuccessToast.mockClear()
})

afterEach(() => {
  wrapper?.unmount()
  vi.useRealTimers()
  document.body.innerHTML = ''
})

describe('IndexPanel status', () => {
  it('shows the state, the lag, the last full rebuild and the number of rows', async () => {
    await mountPanel()

    expect(wrapper.text()).toContain('Ready')
    expect(wrapper.text()).toContain(new Date(Math.round(SECONDS * 1000)).toLocaleString())
    expect(wrapper.text()).toContain('1834 rows')
  })

  it('shows the lag in minutes once it reaches a minute', async () => {
    session.client = fakeClient({ searchIndex: vi.fn(() => Promise.resolve({ data: indexBody({ state: 'DEGRADED', lagSeconds: 420 }) })) })
    await mountPanel()

    expect(wrapper.find('[data-lag]').text()).toContain('7 minutes')
  })

  it('says a rebuild has never completed when the status has no last full rebuild', async () => {
    session.client = fakeClient({ searchIndex: vi.fn(() => Promise.resolve({ data: indexBody({ state: 'MISSING', lastFullRebuild: null, rows: 0 }) })) })
    await mountPanel()

    expect(wrapper.find('[data-last-rebuild]').text()).toBe('Never')
  })

  it('shows the dead letters of the outbox apart from the pending entries', async () => {
    session.client = fakeClient({
      searchIndex: vi.fn(() => Promise.resolve({ data: indexBody({ outbox: { pending: 5, deadLetters: 2, droppedEnqueues: 0 } }) }))
    })
    await mountPanel()

    expect(wrapper.find('[data-outbox="pending"]').text()).toContain('5')
    expect(wrapper.find('[data-outbox="dead-letters"]').text()).toContain('2')
  })

  it('shows the drift report of the last verify with its percentage and the rows it found', async () => {
    session.client = fakeClient({
      searchIndex: vi.fn(() => Promise.resolve({
        data: indexBody({ lastVerify: { runId: 9, at: SECONDS, sampled: 2000, missing: 90, changed: 50, reorigined: 0, removedRepositoryRows: 0, drift: 0.07 } })
      }))
    })
    await mountPanel()

    expect(wrapper.find('[data-verify]').text()).toContain('7%')
    expect(wrapper.find('[data-verify]').text()).toContain('2000')
    expect(wrapper.find('[data-verify]').text()).toContain('90')
  })

  it('recommends a rebuild when the drift of the last verify is above five percent', async () => {
    session.client = fakeClient({
      searchIndex: vi.fn(() => Promise.resolve({
        data: indexBody({ lastVerify: { runId: 9, at: SECONDS, sampled: 2000, missing: 200, changed: 0, reorigined: 0, removedRepositoryRows: 0, drift: 0.1 } })
      }))
    })
    await mountPanel()

    expect(wrapper.find('[data-verify]').text()).toContain('Start a rebuild')
  })

  it('says search is turned off on this instance when the status answers 404', async () => {
    session.client = fakeClient({ searchIndex: vi.fn(() => Promise.reject(httpError(404))) })
    await mountPanel()

    expect(wrapper.text()).toContain('Search is turned off on this instance')
    expect(buttonNamed('Rebuild index')).toBeUndefined()
  })

  it('says only managers can see the index when the status answers 403', async () => {
    session.client = fakeClient({ searchIndex: vi.fn(() => Promise.reject(httpError(403))) })
    await mountPanel()

    expect(wrapper.text()).toContain('Only managers can see the search index')
  })

  it('shows a readable message when the status cannot be loaded', async () => {
    session.client = fakeClient({ searchIndex: vi.fn(() => Promise.reject(httpError(500, 'The index is unreachable'))) })
    await mountPanel()

    const panel = wrapper.get('[data-test="index-error"]')
    expect(panel.attributes('role'), 'the failure is announced').toBe('alert')
    expect(panel.attributes('data-ingot-state'), 'the failure is an error state panel').toBe('error')
    expect(panel.text()).toContain('The index is unreachable')
  })

  it('reads the status again when Retry is pressed after a failed load', async () => {
    const searchIndex = vi.fn()
      .mockRejectedValueOnce(httpError(500, 'The index is unreachable'))
      .mockResolvedValue({ data: indexBody() })
    session.client = fakeClient({ searchIndex })
    await mountPanel()

    await wrapper.get('[data-test="index-error"] button').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="index-error"]').exists(), 'the retry cleared the error').toBe(false)
    expect(searchIndex).toHaveBeenCalledTimes(2)
  })

  it('draws the loading line as a busy state panel', async () => {
    session.client = fakeClient({ searchIndex: vi.fn(() => new Promise(() => undefined)) })
    await mountPanel()

    const panel = wrapper.get('[data-test="index-loading"]')
    expect(panel.attributes('aria-busy')).toBe('true')
    expect(wrapper.text()).not.toContain('Loading the search index')
  })
})

describe('IndexPanel section', () => {
  it('titles the card with a level 2 MdSection that keeps the heading id of the panel', async () => {
    await mountPanel()

    const section = wrapper.findComponent({ name: 'MdSection' }) as VueWrapper
    const heading = wrapper.get('#search-index-title')

    expect(section.exists(), 'the card holds the shared section').toBe(true)
    expect(propsOf(section).level,'the panel is the first level under the h1 of the frame, so its section is level 2').toBe(2)
    expect(heading.element.tagName, 'the title is a level 2 heading').toBe('H2')
    expect(heading.classes(), 'a level 2 title takes the title-medium role').toContain('md-title-medium')
  })

  it('sets every level 3 heading in the title-small role', async () => {
    await mountPanel()

    const headings = wrapper.findAll('h3')

    expect(headings.length, 'the panel has level 3 headings').toBeGreaterThan(0)
    expect(headings.map(heading => heading.classes().includes('md-title-small')), 'each level 3 heading is title-small').toEqual(headings.map(() => true))
    expect(wrapper.html(), 'no level 3 heading is title-medium').not.toMatch(/<h3[^>]*md-title-medium/)
  })

  it('puts the rebuild and verify actions in the section header', async () => {
    await mountPanel()

    const header = wrapper.get('[data-test="section-header"]')

    expect(header.text(), 'the rebuild action sits beside the title').toContain('Rebuild index')
    expect(header.text(), 'the verify action sits beside the title').toContain('Verify sample')
  })
})

describe('IndexPanel active run', () => {
  it('shows each repository of a running rebuild as done, current or pending', async () => {
    session.client = fakeClient({
      searchIndex: vi.fn(() => Promise.resolve({
        data: indexBody({ activeRun: run({ cursorRepository: 'releases', cursorPath: 'a/b' }) })
      }))
    })
    await mountPanel()

    expect(wrapper.find('[data-repository="releases"]').attributes('data-progress')).toBe('current')
    expect(wrapper.find('[data-repository="snapshots"]').attributes('data-progress')).toBe('pending')
  })

  it('disables the rebuild and verify actions while a run is live', async () => {
    session.client = fakeClient({ searchIndex: vi.fn(() => Promise.resolve({ data: indexBody({ activeRun: run() }) })) })
    await mountPanel()

    expect(buttonNamed('Rebuild index').attributes('disabled')).toBeDefined()
    expect(buttonNamed('Verify sample').attributes('disabled')).toBeDefined()
  })

  // The panel is a filled card on surface-container-low, so the live run block steps one role up, never the same role.
  it('draws the live run one surface step above its card', async () => {
    session.client = fakeClient({ searchIndex: vi.fn(() => Promise.resolve({ data: indexBody({ activeRun: run() }) })) })
    await mountPanel()

    const live = wrapper.find('[data-live-run]')

    expect(live.classes(), 'a block nested in a card is surface-container').toContain('bg-surface-container')
    expect(live.classes(), 'not the role of its card').not.toContain('bg-surface-container-low')
  })

  it('draws the live run as a shared card on the container tone', async () => {
    session.client = fakeClient({ searchIndex: vi.fn(() => Promise.resolve({ data: indexBody({ activeRun: run() }) })) })
    await mountPanel()

    const live = cardsWith('data-live-run')[0]

    expect(live, 'the live run is an MdCard').toBeDefined()
    expect(live && propsOf(live).tone,'the live run steps one surface role above its card').toBe('container')
  })

  it('cancels the live run by its id', async () => {
    session.client = fakeClient({ searchIndex: vi.fn(() => Promise.resolve({ data: indexBody({ activeRun: run({ id: 7 }) }) })) })
    await mountPanel()

    await buttonNamed('Cancel run').trigger('click')
    await flushPromises()

    expect(session.client.maven.cancelIndexRun).toHaveBeenCalledWith(7)
  })

  it('asks for the status again every two seconds while a run is live, and stops once it has ended', async () => {
    vi.useFakeTimers()
    const searchIndex = vi.fn()
      .mockResolvedValueOnce({ data: indexBody({ activeRun: run() }) })
      .mockResolvedValueOnce({ data: indexBody({ activeRun: run() }) })
      .mockResolvedValue({ data: indexBody({ runs: [run({ status: 'COMPLETED', finishedAt: SECONDS + 60 })] }) })
    session.client = fakeClient({ searchIndex })
    wrapper = mount(IndexPanel) as VueWrapper
    await vi.advanceTimersByTimeAsync(0)
    expect(searchIndex).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(searchIndex).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(searchIndex).toHaveBeenCalledTimes(3)

    await vi.advanceTimersByTimeAsync(POLL_MS * 3)
    expect(searchIndex).toHaveBeenCalledTimes(3)
  })
})

describe('IndexPanel actions', () => {
  it('asks for a confirmation before a rebuild starts', async () => {
    await mountPanel()

    await buttonNamed('Rebuild index').trigger('click')
    await flushPromises()

    expect(document.getElementById('search-index-rebuild-confirm')).not.toBeNull()
    expect(session.client.maven.startIndexRebuild).not.toHaveBeenCalled()
  })

  it('rebuilds every repository when the scope is left at all repositories', async () => {
    await mountPanel()
    await buttonNamed('Rebuild index').trigger('click')
    await flushPromises()

    dialogButton('search-index-rebuild-confirm').click()
    await flushPromises()

    expect(session.client.maven.startIndexRebuild).toHaveBeenCalledWith({})
  })

  it('rebuilds only the repository the scope names', async () => {
    await mountPanel()
    await buttonNamed('Rebuild index').trigger('click')
    await flushPromises()
    const scope = document.getElementById('search-index-scope') as HTMLSelectElement
    scope.value = 'snapshots'
    scope.dispatchEvent(new Event('change'))
    await flushPromises()

    dialogButton('search-index-rebuild-confirm').click()
    await flushPromises()

    expect(session.client.maven.startIndexRebuild).toHaveBeenCalledWith({ repositories: ['snapshots'] })
  })

  it('starts a verify run at once and reloads the status', async () => {
    await mountPanel()

    await buttonNamed('Verify sample').trigger('click')
    await flushPromises()

    expect(session.client.maven.startIndexVerify).toHaveBeenCalledTimes(1)
    expect(session.client.maven.searchIndex).toHaveBeenCalledTimes(2)
  })

  it('says a run was started in a live region', async () => {
    await mountPanel()

    await buttonNamed('Verify sample').trigger('click')
    await flushPromises()

    expect(wrapper.find('[aria-live="polite"]').text()).toContain('Verify run 9 started')
  })

  it('reports a refused start as a conflict when another run is live', async () => {
    session.client = fakeClient({ startIndexVerify: vi.fn(() => Promise.reject(httpError(409))) })
    await mountPanel()

    await buttonNamed('Verify sample').trigger('click')
    await flushPromises()

    expect(toast.createErrorToast).toHaveBeenCalledWith(expect.stringContaining('already running'))
  })

  it('reports a refused rebuild as a notice for managers when the server answers 403', async () => {
    session.client = fakeClient({ startIndexRebuild: vi.fn(() => Promise.reject(httpError(403))) })
    await mountPanel()
    await buttonNamed('Rebuild index').trigger('click')
    await flushPromises()

    dialogButton('search-index-rebuild-confirm').click()
    await flushPromises()

    expect(toast.createErrorToast).toHaveBeenCalledWith(expect.stringContaining('Only managers'))
  })
})

describe('IndexPanel latest runs', () => {
  const jobRun = (id: string, kind: string, label: string) => ({
    id: `index:${id}`,
    source: 'index',
    kind,
    label,
    trigger: 'MANUAL',
    triggerDetail: null,
    state: 'SUCCEEDED',
    startedAt: '2026-11-10T10:00:00Z',
    finishedAt: '2026-11-10T10:05:00Z',
    durationMs: 300000,
    counts: [],
    failures: [],
    failuresTruncated: false
  })

  it('shows the latest rebuild and the latest verify, asking the Jobs endpoint for one run of each kind', async () => {
    const list = vi.fn((params: Record<string, string>) => Promise.resolve({
      data: jobPage([jobRun(params.kind === 'index.rebuild' ? '7' : '9', params.kind, params.kind === 'index.rebuild' ? 'Rebuild' : 'Verify')])
    }))
    session.client = fakeClient({}, { list })
    await mountPanel()

    expect(list).toHaveBeenCalledWith({ kind: 'index.rebuild', size: '1' })
    expect(list).toHaveBeenCalledWith({ kind: 'index.verify', size: '1' })
    expect(wrapper.findAll('[data-latest-run]').map(item => item.attributes('data-latest-run'))).toEqual(['index.rebuild', 'index.verify'])
  })

  it('draws each latest run one surface step above its card', async () => {
    const list = vi.fn((params: Record<string, string>) => Promise.resolve({
      data: jobPage([jobRun(params.kind === 'index.rebuild' ? '7' : '9', params.kind, params.kind === 'index.rebuild' ? 'Rebuild' : 'Verify')])
    }))
    session.client = fakeClient({}, { list })
    await mountPanel()

    expect(wrapper.findAll('[data-latest-run]'), 'one card per kind').toHaveLength(2)
    for (const card of wrapper.findAll('[data-latest-run]')) {
      expect(card.classes(), `${card.attributes('data-latest-run')} is surface-container`).toContain('bg-surface-container')
      expect(card.classes(), `${card.attributes('data-latest-run')} is not the role of its card`).not.toContain('bg-surface-container-low')
    }
  })

  it('draws each latest run as a shared card list item', async () => {
    await mountPanel()

    const cards = cardsWith('data-latest-run')

    expect(cards.map(card => (card.element as HTMLElement).tagName), 'one list item card per kind').toEqual(['LI', 'LI'])
    expect(cards.map(card => propsOf(card).tone),'each latest run steps one surface role above its card').toEqual(['container', 'container'])
  })

  it('draws no list of index runs', async () => {
    session.client = fakeClient({
      searchIndex: vi.fn(() => Promise.resolve({
        data: indexBody({ runs: [run({ id: 8, status: 'FAILED' }), run({ id: 7 })] })
      }))
    })
    await mountPanel()

    expect(wrapper.find('[data-run-id]').exists()).toBe(false)
  })

  it('links "All runs" to the Jobs page filtered to the index source', async () => {
    await mountPanel()

    const link = wrapper.findAllComponents(RouterLinkStub).find(item => item.text() === 'All runs')

    expect(link?.props('to')).toEqual({ name: 'jobs', query: { kind: 'index' } })
  })
})
