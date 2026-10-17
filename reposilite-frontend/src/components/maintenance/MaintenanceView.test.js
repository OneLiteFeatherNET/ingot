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
import MaintenanceView from './MaintenanceView.vue'

const session = vi.hoisted(() => ({ client: null }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createWarningToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const POLICY = {
  id: 'snapshots-weekly',
  enabled: true,
  repositories: ['snapshots'],
  scope: 'SNAPSHOTS',
  keepLatest: 3,
  olderThanDays: null,
  notDownloadedForDays: null,
  groupFilter: null,
  schedule: 'manual'
}

const fakeClient = () => ({
  maven: {
    storage: vi.fn(() => Promise.resolve({
      data: { computedAt: '2026-10-15T09:30:00Z', computing: false, totalBytes: 0, repositories: [] }
    })),
    searchIndex: vi.fn(() => Promise.resolve({
      data: { state: 'READY', lagSeconds: 0, rows: 0, outbox: { pending: 0, deadLetters: 0, droppedEnqueues: 0 }, repositories: [], runs: [] }
    }))
  },
  settings: {
    fetch: vi.fn((name) => Promise.resolve({
      data: name === 'cleanup' ? { policies: [POLICY] } : { repositories: [{ id: 'snapshots' }] }
    })),
    update: vi.fn(() => Promise.resolve({ data: '' }))
  },
  cleanup: {
    runs: vi.fn(() => Promise.resolve({ data: [] })),
    dryRun: vi.fn(),
    run: vi.fn(() => Promise.resolve({
      data: { id: 1, policyId: 'snapshots-weekly', trigger: 'MANUAL', startedAt: '2026-10-15T10:00:00Z', status: 'SUCCEEDED', deletedCount: 0, deletedBytes: 0, failures: [] }
    }))
  }
})

let wrapper

beforeEach(() => {
  session.client = fakeClient()
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})

describe('MaintenanceView', () => {
  it('shows the storage usage and the cleanup policies on one page', async () => {
    wrapper = mount(MaintenanceView, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.text()).toContain('Storage usage')
    expect(wrapper.text()).toContain('Cleanup policies')
  })

  it('shows the search index beside the storage usage and the cleanup policies', async () => {
    wrapper = mount(MaintenanceView, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.text()).toContain('Search index')
    expect(session.client.maven.searchIndex).toHaveBeenCalledTimes(1)
  })

  it('reloads the storage figures once a run has finished', async () => {
    wrapper = mount(MaintenanceView, { attachTo: document.body })
    await flushPromises()
    wrapper.findAll('button').find(button => button.attributes('aria-label') === 'Run snapshots-weekly now').trigger('click')
    await flushPromises()
    document.getElementById('run-now-input').value = 'snapshots-weekly'
    document.getElementById('run-now-input').dispatchEvent(new Event('input'))
    await flushPromises()

    document.getElementById('run-now-confirm').click()
    await flushPromises()

    expect(session.client.maven.storage).toHaveBeenCalledTimes(2)
  })
})
