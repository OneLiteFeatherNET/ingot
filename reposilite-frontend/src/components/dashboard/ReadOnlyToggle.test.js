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
import ReadOnlyToggle from './ReadOnlyToggle.vue'

// The toggle reads the session for its client. The client is replaced, so the test never
// reaches the network or the real token.
const session = vi.hoisted(() => ({ client: null }))
vi.mock('../../store/session', () => ({ useSession: () => session }))

const forbidden = () => Promise.reject(Object.assign(new Error('Request failed'), {
  response: { status: 403, data: {} }
}))

const clientWith = (overrides = {}) => ({
  readOnly: {
    get: vi.fn(forbidden),
    enable: vi.fn(() => Promise.resolve({ status: 200, data: { enabled: true, source: 'stored' } })),
    disable: vi.fn(() => Promise.resolve({ status: 200, data: { enabled: false, source: 'stored' } })),
    ...overrides
  }
})

let wrapper

const mountToggle = () => {
  wrapper = mount(ReadOnlyToggle, { attachTo: document.body })
}

describe('ReadOnlyToggle', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.restoreAllMocks()
  })

  it('hides the read-only toggle when the probe answers 403', async () => {
    session.client = ref(clientWith({ get: vi.fn(forbidden) }))
    mountToggle()
    await flushPromises()

    expect(wrapper.find('[role="switch"]').exists(), 'a principal without the maintenance area must not see the toggle').toBe(false)
    expect(wrapper.text(), 'no label of the toggle is shown either').not.toContain('Read-only mode')
  })

  it('shows the read-only toggle with the current state when the probe answers 200', async () => {
    session.client = ref(clientWith({
      get: vi.fn(() => Promise.resolve({ status: 200, data: { enabled: true, source: 'stored' } }))
    }))
    mountToggle()
    await flushPromises()

    const toggle = wrapper.find('[role="switch"]')
    expect(toggle.exists(), 'a principal with the maintenance area sees the toggle').toBe(true)
    expect(toggle.attributes('aria-checked'), 'the switch shows the state the server reported').toBe('true')
    expect(wrapper.text()).toContain('Read-only mode')
  })

  it('does not show the toggle while the probe is still pending', async () => {
    session.client = ref(clientWith({ get: vi.fn(() => new Promise(() => {})) }))
    mountToggle()
    await flushPromises()

    expect(wrapper.find('[role="switch"]').exists(), 'nothing is shown before the probe has answered').toBe(false)
  })
})
