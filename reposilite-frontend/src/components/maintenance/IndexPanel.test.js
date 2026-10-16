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
import { mount, flushPromises } from '@vue/test-utils'
import IndexPanel from './IndexPanel.vue'

// The client is swapped per test through this holder. The panel reads it through the session.
const session = vi.hoisted(() => ({ client: null }))
const toast = vi.hoisted(() => ({ createErrorToast: vi.fn(), createSuccessToast: vi.fn() }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: toast.createSuccessToast,
  createErrorToast: toast.createErrorToast,
  createWarningToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message || error?.message || `${error}`
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

const httpError = (status, message = `Request failed with status code ${status}`) =>
  Object.assign(new Error(message), { response: { status, data: { status, message } } })

const fakeClient = (overrides = {}) => ({
  maven: {
    searchIndex: vi.fn(() => Promise.resolve({ data: indexBody() })),
    startIndexRebuild: vi.fn(() => Promise.resolve({ status: 202, data: { runId: 8 } })),
    startIndexVerify: vi.fn(() => Promise.resolve({ status: 202, data: { runId: 9 } })),
    cancelIndexRun: vi.fn(() => Promise.resolve({ status: 204, data: '' })),
    ...overrides
  }
})

let wrapper

const mountPanel = async () => {
  wrapper = mount(IndexPanel, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

const buttonNamed = (text) => wrapper.findAll('button').find(button => button.text() === text)
const dialogButton = (id) => document.getElementById(id)

beforeEach(() => {
  session.client = fakeClient()
  toast.createErrorToast.mockClear()
  toast.createSuccessToast.mockClear()
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
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

    expect(wrapper.find('[role="alert"]').text()).toContain('The index is unreachable')
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
    wrapper = mount(IndexPanel)
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
    document.getElementById('search-index-scope').value = 'snapshots'
    document.getElementById('search-index-scope').dispatchEvent(new Event('change'))
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

describe('IndexPanel run history', () => {
  it('lists the runs newest first with the status and the last error of a failed run', async () => {
    session.client = fakeClient({
      searchIndex: vi.fn(() => Promise.resolve({
        data: indexBody({
          runs: [
            run({ id: 8, status: 'FAILED', finishedAt: SECONDS + 5, lastError: 'could not list releases' }),
            run({ id: 7, status: 'COMPLETED', finishedAt: SECONDS - 60 })
          ]
        })
      }))
    })
    await mountPanel()

    const ids = wrapper.findAll('[data-run-id]').map(item => item.attributes('data-run-id'))
    expect(ids).toEqual(['8', '7'])
    expect(wrapper.find('[data-run-id="8"]').text()).toContain('could not list releases')
    expect(wrapper.find('[data-run-id="7"]').text()).toContain('Completed')
  })
})
