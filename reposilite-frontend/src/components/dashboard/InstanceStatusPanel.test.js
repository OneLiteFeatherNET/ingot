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
const session = vi.hoisted(() => ({ client: null, isManager: null }))
vi.mock('../../store/session', () => ({ useSession: () => session }))
// The health row under the tiles reads the manager flag, so the panel's session carries one.
session.isManager = ref(true)
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
  },
  // The read-only toggle is part of the panel. These tests are not about maintainers, so the
  // probe answers as a principal without the maintenance area, and the toggle stays hidden.
  readOnly: {
    get: vi.fn(() => Promise.reject(Object.assign(new Error('Forbidden'), { response: { status: 403, data: {} } })))
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

describe('InstanceStatusPanel cluster view', () => {
  const NOW = Date.UTC(2026, 10, 8, 12, 0, 0)
  const MINUTE = 60 * 1000
  const HOUR = 60 * MINUTE

  // The clock is the system time, faked so the uptime of each instance is the same on every run.
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
    vi.setSystemTime(NOW)
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  const member = (overrides = {}) => ({
    id: 'ingot-0',
    version: '1.3.0',
    uptimeStart: NOW - 2 * HOUR,
    usedMemory: 100,
    maxMemory: 512,
    usedThreads: 3,
    maxThreads: 20,
    failuresCount: 0,
    health: 'up',
    stale: false,
    interval: 60000,
    samples: [],
    ...overrides
  })

  const mountWithStatus = async (status) => {
    session.client = ref(clientWith(() => Promise.resolve({ data: status })))
    const wrapper = mount(InstanceStatusPanel)
    await flushPromises()
    return wrapper
  }

  it('shows the cluster summary above the per-instance list', async () => {
    const wrapper = await mountWithStatus({
      ...INSTANCE,
      cluster: { instances: [member(), member({ id: 'ingot-1' })] }
    })

    const summary = wrapper.find('[data-testid="cluster-summary"]')
    const list = wrapper.find('[data-testid="cluster-instances"]')
    expect(summary.exists(), 'the cluster summary is shown when a bus is configured').toBe(true)
    expect(list.exists(), 'the per-instance list is shown when a bus is configured').toBe(true)
    expect(summary.element.compareDocumentPosition(list.element) & Node.DOCUMENT_POSITION_FOLLOWING, 'the summary comes before the list').toBeTruthy()
    wrapper.unmount()
  })

  it('lists every instance with its own version', async () => {
    const wrapper = await mountWithStatus({
      ...INSTANCE,
      cluster: { instances: [member({ version: '1.3.0' }), member({ id: 'ingot-1', version: '1.3.0' })] }
    })

    const items = wrapper.findAll('[data-testid="cluster-instance"]')
    expect(items, 'one row per instance').toHaveLength(2)
    expect(items[0].text()).toContain('ingot-0')
    expect(items[1].text()).toContain('ingot-1')
    wrapper.unmount()
  })

  it('sums the memory, threads and failures of the instances in the summary', async () => {
    const wrapper = await mountWithStatus({
      ...INSTANCE,
      cluster: {
        instances: [
          member({ usedMemory: 100, usedThreads: 3, maxThreads: 20, failuresCount: 1 }),
          member({ id: 'ingot-1', usedMemory: 151.5, usedThreads: 5, maxThreads: 20, failuresCount: 2 })
        ]
      }
    })

    const summary = wrapper.find('[data-testid="cluster-summary"]').text()
    expect(summary, 'used memory is the sum, not the first instance').toContain('251.5 MB')
    expect(summary, 'maximum memory is the sum of both limits').toContain('of 1024 MB available')
    expect(summary, 'maximum threads is the sum of both pools').toContain('of 40 in the pool')
    expect(summary, 'failures are summed as a count').toContain('3 failures recorded')
    wrapper.unmount()
  })

  it('marks a stale instance as stale and leaves the others unmarked', async () => {
    const wrapper = await mountWithStatus({
      ...INSTANCE,
      cluster: {
        instances: [member(), member({ id: 'ingot-1', stale: true })]
      }
    })

    const items = wrapper.findAll('[data-testid="cluster-instance"]')
    expect(items[0].text(), 'a reporting instance is not marked').not.toContain('Stale')
    expect(items[1].text(), 'a replica that stopped reporting is marked').toContain('Stale')
    wrapper.unmount()
  })

  it('shows mixed when the instances run different versions, and lists both versions', async () => {
    const wrapper = await mountWithStatus({
      ...INSTANCE,
      cluster: {
        instances: [member({ version: '1.2.0' }), member({ id: 'ingot-1', version: '1.3.0' })]
      }
    })

    const summary = wrapper.find('[data-testid="cluster-summary"]').text()
    expect(summary, 'the version of a mixed cluster is named mixed').toContain('mixed')
    expect(summary, 'every version in the cluster is listed').toContain('1.2.0')
    expect(summary, 'every version in the cluster is listed').toContain('1.3.0')
    wrapper.unmount()
  })

  it('shows the shortest uptime and the worst health of the cluster', async () => {
    const wrapper = await mountWithStatus({
      ...INSTANCE,
      cluster: {
        instances: [
          member({ uptimeStart: NOW - 2 * HOUR, health: 'up' }),
          member({ id: 'ingot-1', uptimeStart: NOW - 30 * MINUTE, health: 'down' })
        ]
      }
    })

    const summary = wrapper.find('[data-testid="cluster-summary"]').text()
    expect(summary, 'the restart 30 minutes ago sets the shortest uptime').toContain('30m 0s')
    expect(summary, 'one unhealthy instance makes the cluster unhealthy').toContain('Unhealthy')
    wrapper.unmount()
  })

  it('renders as before, with no cluster section, when the status has no cluster field', async () => {
    const wrapper = await mountWithStatus({ ...INSTANCE })

    expect(wrapper.find('[data-testid="cluster-summary"]').exists(), 'no summary without a bus').toBe(false)
    expect(wrapper.find('[data-testid="cluster-instances"]').exists(), 'no instance list without a bus').toBe(false)
    expect(wrapper.text(), 'the existing tiles are still shown').toContain('Used memory')
    expect(wrapper.text()).toContain('Uptime')
    expect(wrapper.text()).toContain('Version')
    wrapper.unmount()
  })

  it('hands the cluster to its parent on every poll so the trends can use it', async () => {
    const instances = [member()]
    const wrapper = await mountWithStatus({ ...INSTANCE, cluster: { instances } })

    expect(wrapper.emitted('cluster').at(-1), 'the parent receives the instances of the status').toEqual([{ instances }])
    wrapper.unmount()
  })

  it('hands null to its parent when there is no cluster, so the trends stay on the local history', async () => {
    const wrapper = await mountWithStatus({ ...INSTANCE })

    expect(wrapper.emitted('cluster').at(-1), 'no bus means no cluster for the trends').toEqual([null])
    wrapper.unmount()
  })
})

describe('InstanceStatusPanel read-only banner', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  const mountWithStatus = async (status) => {
    session.client = ref(clientWith(() => Promise.resolve({ data: status })))
    const wrapper = mount(InstanceStatusPanel)
    await flushPromises()
    return wrapper
  }

  it('shows the read-only banner while the instance is read-only', async () => {
    const wrapper = await mountWithStatus({ ...INSTANCE, readOnly: true, readOnlySource: 'environment' })

    const banner = wrapper.find('[data-testid="read-only-banner"]')
    expect(banner.exists(), 'a read-only instance is announced on the status page').toBe(true)
    expect(banner.text(), 'the banner says writes are refused until read-only mode is turned off').toContain('Writes are refused until read-only mode is turned off')
    expect(banner.text(), 'an environment setting is named as the source').toContain('INGOT_READ_ONLY')
    wrapper.unmount()
  })

  it('names the dashboard or API as the source when read-only was turned on at runtime', async () => {
    const wrapper = await mountWithStatus({ ...INSTANCE, readOnly: true, readOnlySource: 'stored' })

    const banner = wrapper.find('[data-testid="read-only-banner"]')
    expect(banner.text(), 'a stored state is named as set from the dashboard or the API').toContain('dashboard or the API')
    expect(banner.text(), 'the environment variable is not named for a stored state').not.toContain('INGOT_READ_ONLY')
    wrapper.unmount()
  })

  it('does not show the read-only banner while the instance accepts writes', async () => {
    const wrapper = await mountWithStatus({ ...INSTANCE, readOnly: false, readOnlySource: 'stored' })

    expect(wrapper.find('[data-testid="read-only-banner"]').exists(), 'a writable instance shows no banner').toBe(false)
    expect(wrapper.text(), 'the rest of the status is still shown').toContain('Used memory')
    wrapper.unmount()
  })
})
