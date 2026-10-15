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
import InstanceHealthRow from './InstanceHealthRow.vue'

// The row reads the session for its client. The client is replaced, so the test never reaches
// the network or the real token.
const session = vi.hoisted(() => ({ client: null }))
vi.mock('../../store/session', () => ({ useSession: () => session }))

const SWAGGER_PAGE = '<!doctype html><html><body><div id="swagger-ui"></div></body></html>'

// Answers for the probes: API docs is there, metrics is not, unless a test says otherwise.
const probeAnswers = (answers = {}) => (endpoint) =>
  Promise.resolve(answers[endpoint] ?? { status: 404, contentType: 'application/json', body: '{}' })

const clientWith = ({ health, probe }) => ({
  status: {
    health: health ?? (() => Promise.resolve({ status: 200, data: { status: 'UP' } })),
    probe: probe ?? probeAnswers({
      '/swagger': { status: 200, contentType: 'text/html', body: SWAGGER_PAGE }
    }),
    url: (endpoint) => `http://test${endpoint}`
  }
})

let wrapper

const mountRow = () => {
  wrapper = mount(InstanceHealthRow, { attachTo: document.body })
}

describe('InstanceHealthRow', () => {
  beforeEach(() => {
    // Only the poll interval is faked, as in the status panel. Nothing fires during a test.
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('shows the instance as healthy when the health endpoint answers UP', async () => {
    session.client = ref(clientWith({}))
    mountRow()
    await flushPromises()

    expect(wrapper.text()).toContain('Healthy')
    expect(wrapper.text(), 'a healthy instance shows no failure wording').not.toContain('Unhealthy')
  })

  it('shows an unhealthy instance with the details the server sends', async () => {
    session.client = ref(clientWith({
      health: () => Promise.reject(Object.assign(new Error('Request failed'), {
        response: { status: 503, data: { status: 'DOWN', details: 'database unreachable' } }
      }))
    }))
    mountRow()
    await flushPromises()

    expect(wrapper.text()).toContain('Unhealthy')
    expect(wrapper.text()).toContain('database unreachable')
  })

  it('says the health is unknown when the server cannot be reached', async () => {
    session.client = ref(clientWith({
      health: () => Promise.reject(new Error('Network Error'))
    }))
    mountRow()
    await flushPromises()

    expect(wrapper.text()).toContain('Health unavailable')
  })

  it('links only the integrations that respond, to their absolute address', async () => {
    session.client = ref(clientWith({}))
    mountRow()
    await flushPromises()

    const links = [...wrapper.findAll('nav a')]
    expect(links.map(link => link.text())).toEqual(['API docs'])
    expect(links[0].attributes('href')).toBe('http://test/swagger')
    expect(links[0].attributes('target')).toBe('_blank')
  })

  it('shows no integrations when the probes fail', async () => {
    session.client = ref(clientWith({
      probe: () => Promise.reject(new Error('Network Error'))
    }))
    mountRow()
    await flushPromises()

    expect(wrapper.find('nav').exists(), 'the integrations row is left out entirely').toBe(false)
  })
})
