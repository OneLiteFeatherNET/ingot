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
import prettyBytes from 'pretty-bytes'
import StorageUsagePanel from './StorageUsagePanel.vue'

// The client is swapped per test through this holder. The panel reads it through the session.
const session = vi.hoisted(() => ({ client: null }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createWarningToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.response?.data?.message || error?.message || `${error}`
}))

const COMPUTED_AT = '2026-10-15T09:30:00Z'

const storageBody = (overrides = {}) => ({
  computedAt: COMPUTED_AT,
  computing: false,
  totalBytes: 13000,
  repositories: [
    { id: 'releases', usedBytes: 2500, quota: '10kB', quotaBytes: 10000, fileCount: 42, storageType: 'fs', group: false },
    { id: 'mirror', usedBytes: 10500, quota: null, quotaBytes: null, fileCount: 7, storageType: 's3', group: false }
  ],
  ...overrides
})

const fakeClient = (overrides = {}) => ({
  maven: {
    storage: vi.fn(() => Promise.resolve({ data: storageBody() })),
    refreshStorage: vi.fn(() => Promise.resolve({ status: 202, data: storageBody({ computing: true }) })),
    ...overrides
  }
})

let wrapper

const mountPanel = async (props = {}) => {
  wrapper = mount(StorageUsagePanel, { props: { revision: 0, ...props } })
  await flushPromises()
  return wrapper
}

const rowOf = (id) => wrapper.findAll('li').find(item => item.find('.font-mono').exists() && item.find('.font-mono').text() === id)

const buttonNamed = (text) => wrapper.findAll('button').find(button => button.text() === text)

beforeEach(() => {
  session.client = fakeClient()
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('StorageUsagePanel', () => {
  it('loads the storage figures once when it opens', async () => {
    await mountPanel()

    expect(session.client.maven.storage).toHaveBeenCalledTimes(1)
  })

  it('shows one row per repository with its used bytes and file count', async () => {
    await mountPanel()

    expect(rowOf('releases').text()).toContain(prettyBytes(2500))
    expect(rowOf('releases').text()).toContain('42 files')
  })

  it('shows the total across all repositories', async () => {
    await mountPanel()

    expect(wrapper.text()).toContain(`Total ${prettyBytes(13000)}`)
  })

  it('shows the time the figures were computed', async () => {
    await mountPanel()

    expect(wrapper.text()).toContain(new Date(COMPUTED_AT).toLocaleString())
  })

  it('draws the used share of the quota on the bar', async () => {
    await mountPanel()

    expect(rowOf('releases').find('[role="progressbar"]').attributes('aria-valuenow')).toBe('25')
  })

  it('caps the bar at a full bar when usage is above the quota', async () => {
    session.client = fakeClient({
      storage: vi.fn(() => Promise.resolve({
        data: storageBody({ repositories: [{ id: 'releases', usedBytes: 12000, quota: '10kB', quotaBytes: 10000, fileCount: 1, storageType: 'fs', group: false }] })
      }))
    })
    await mountPanel()

    expect(rowOf('releases').find('[role="progressbar"]').attributes('aria-valuenow')).toBe('100')
  })

  it('draws no bar, and says so, when the quota is not known in bytes', async () => {
    session.client = fakeClient({
      storage: vi.fn(() => Promise.resolve({
        data: storageBody({ repositories: [{ id: 'releases', usedBytes: 2500, quota: '10GB', quotaBytes: null, fileCount: 1, storageType: 'fs', group: false }] })
      }))
    })
    await mountPanel()

    expect(rowOf('releases').find('[role="progressbar"]').exists()).toBe(false)
    expect(rowOf('releases').text()).toContain('The quota size is not known, so there is no bar.')
  })

  it('shows only the used size for object storage, which has no quota', async () => {
    await mountPanel()

    expect(rowOf('mirror').text()).toContain(`${prettyBytes(10500)} used`)
    expect(rowOf('mirror').text()).not.toContain('quota')
    expect(rowOf('mirror').text()).not.toContain('no limit')
    expect(rowOf('mirror').find('[role="progressbar"]').exists()).toBe(false)
  })

  it('says a file system repository without a limit has no limit', async () => {
    session.client = fakeClient({
      storage: vi.fn(() => Promise.resolve({
        data: storageBody({ repositories: [{ id: 'releases', usedBytes: 2500, quota: null, quotaBytes: null, fileCount: 1, storageType: 'fs', group: false }] })
      }))
    })
    await mountPanel()

    expect(rowOf('releases').text()).toContain('no limit')
  })

  it('says a loopback group has no own storage and shows no quota for it', async () => {
    session.client = fakeClient({
      storage: vi.fn(() => Promise.resolve({
        data: storageBody({ repositories: [{ id: 'onelitefeather', usedBytes: 0, quota: null, quotaBytes: null, fileCount: 0, storageType: 'fs', group: true }] })
      }))
    })
    await mountPanel()

    expect(rowOf('onelitefeather').text()).toContain('group, no own storage')
    expect(rowOf('onelitefeather').text()).not.toContain('quota')
    expect(rowOf('onelitefeather').text()).not.toContain('100%')
    expect(rowOf('onelitefeather').find('[role="progressbar"]').exists()).toBe(false)
  })

  it('says the figures are not computed yet and offers a refresh when none exist', async () => {
    session.client = fakeClient({
      storage: vi.fn(() => Promise.resolve({ data: storageBody({ computedAt: null, totalBytes: 0, repositories: [] }) }))
    })
    await mountPanel()

    expect(wrapper.text()).toContain('The figures have not been computed yet.')
    expect(buttonNamed('Refresh').exists()).toBe(true)
  })

  it('calls the refresh endpoint when the user asks for a refresh', async () => {
    await mountPanel()

    await buttonNamed('Refresh').trigger('click')
    await flushPromises()

    expect(session.client.maven.refreshStorage).toHaveBeenCalledTimes(1)
  })

  it('disables refresh and shows progress while the figures are being computed', async () => {
    await mountPanel()

    await buttonNamed('Refresh').trigger('click')
    await flushPromises()

    expect(buttonNamed('Refresh').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[role="progressbar"]').attributes('aria-valuenow')).toBeUndefined()
  })

  it('reloads the figures when the parent reports a change', async () => {
    await mountPanel()

    await wrapper.setProps({ revision: 1 })
    await flushPromises()

    expect(session.client.maven.storage).toHaveBeenCalledTimes(2)
  })

  it('shows the load failure in place of the figures', async () => {
    session.client = fakeClient({
      storage: vi.fn(() => Promise.reject(Object.assign(new Error('Request failed'), { response: { data: { message: 'Storage is unavailable' } } })))
    })
    await mountPanel()

    expect(wrapper.text()).toContain('Storage is unavailable')
  })
})
