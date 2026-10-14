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

const clientWith = (instance) => ({
  status: { instance, snapshots: vi.fn() }
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
})
