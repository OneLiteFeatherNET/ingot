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
import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import InstanceStatusPanel from './InstanceStatusPanel.vue'

// The panel reads the session for its client and raises a toast on failure. Both are
// replaced, so the test never touches the network, the real token or the toast container.
const session = vi.hoisted(() => ({ client: null }))
vi.mock('../../store/session', () => ({ useSession: () => session }))
vi.mock('../../helpers/toast', () => ({ createErrorToast: vi.fn() }))

const INSTANCE = {
  usedMemory: 100,
  maxMemory: 512,
  usedThreads: 3,
  maxThreads: 20,
  uptime: 90000,
  version: '1.0.0',
  latestVersion: '1.0.0',
  failuresCount: 0
}

// The health row and the failures sheet are part of the panel, so their requests are answered
// here too. Nothing in these tests depends on what they say.
const clientWith = (instance) => ({
  status: {
    instance,
    snapshots: vi.fn(),
    health: vi.fn(() => Promise.resolve({ status: 200, data: { status: 'UP' } })),
    probe: vi.fn(() => Promise.resolve({ status: 404, contentType: '', body: '' })),
    url: (endpoint) => endpoint,
    failures: vi.fn(() => Promise.resolve({ data: { failures: [] } })),
    clearFailures: vi.fn(() => Promise.resolve({}))
  }
})

describe('InstanceStatusPanel', () => {
  beforeEach(() => {
    // Only the poll interval is faked. It is the one timer the panel starts, and it must
    // not fire during a test; flushPromises still works on real microtasks.
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('stops showing placeholders and shows an error card when the status request fails', async () => {
    session.client = ref(clientWith(() => Promise.reject(new Error('unreachable'))))
    const wrapper = mount(InstanceStatusPanel)

    await flushPromises()

    expect(wrapper.find('.skeleton-bars').exists(), 'placeholders must end once the request has failed').toBe(false)
    expect(wrapper.text(), 'the error card should name what failed').toContain('Cannot load instance status')
    wrapper.unmount()
  })

  it('requests the status again when the error card is retried', async () => {
    const instance = vi.fn()
      .mockRejectedValueOnce(new Error('unreachable'))
      .mockResolvedValueOnce({ data: INSTANCE })
    session.client = ref(clientWith(instance))
    const wrapper = mount(InstanceStatusPanel)
    await flushPromises()

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(instance, 'retry should send a second status request').toHaveBeenCalledTimes(2)
    expect(wrapper.text(), 'a successful retry should replace the error with the tiles').toContain('Used memory')
    wrapper.unmount()
  })

  it('opens the failures sheet from the failures tile when failures are recorded', async () => {
    const failure = {
      id: 1,
      timestamp: '2026-10-15T11:58:00Z',
      source: '/api/maven/details',
      type: 'IllegalStateException',
      message: 'repository is broken',
      stacktrace: '  by IllegalStateException: repository is broken',
      count: 1
    }
    const client = clientWith(() => Promise.resolve({ data: { ...INSTANCE, failuresCount: 1 } }))
    client.status.failures = vi.fn(() => Promise.resolve({ data: { failures: [failure] } }))
    session.client = ref(client)
    const wrapper = mount(InstanceStatusPanel, { attachTo: document.body })
    await flushPromises()

    await wrapper.find('[aria-haspopup="dialog"]').trigger('click')
    await flushPromises()

    expect(document.querySelector('[role="dialog"]')?.textContent, 'the tile opens the list of failures').toContain('Recorded failures')
    wrapper.unmount()
  })

  it('leaves the failures tile without any action when nothing has failed', async () => {
    session.client = ref(clientWith(() => Promise.resolve({ data: { ...INSTANCE, failuresCount: 0 } })))
    const wrapper = mount(InstanceStatusPanel)
    await flushPromises()

    expect(wrapper.find('[aria-haspopup="dialog"]').exists(), 'an empty tile has nothing to open').toBe(false)
    wrapper.unmount()
  })
})
