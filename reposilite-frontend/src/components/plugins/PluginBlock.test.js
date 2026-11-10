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
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import PluginBlock from './PluginBlock.vue'

vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    request: vi.fn(),
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
}))

const PLUGIN = 'example'

const rows = (count) => Array.from({ length: count }, (_, index) => ({ name: `row ${index}` }))

beforeEach(() => {
  vi.clearAllMocks()
  document.cookie = 'ingot_csrf=token-1; path=/'
})

afterEach(() => {
  vi.useRealTimers()
  document.cookie = 'ingot_csrf=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

const mountBlock = (block) => mount(PluginBlock, { props: { plugin: PLUGIN, block } })

describe('plugin blocks draw text, never markup', () => {
  it('shows markup in a text block as plain characters', () => {
    const wrapper = mountBlock({ type: 'text', text: '<img src=x onerror=alert(1)>' })

    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists(), 'an img element drawn from the text').toBe(false)
  })

  it('shows markup in a table cell as plain characters', async () => {
    axios.get.mockResolvedValue({ data: [{ name: '<b>bold</b>' }] })

    const wrapper = mountBlock({ type: 'table', source: '/api/plugins/example/items', columns: [{ key: 'name', label: 'Name' }] })
    await flushPromises()

    expect(wrapper.text()).toContain('<b>bold</b>')
    expect(wrapper.find('b').exists(), 'a bold element drawn from the cell').toBe(false)
  })
})

describe('table block', () => {
  it('shows at most 500 rows and says how many it cut', async () => {
    axios.get.mockResolvedValue({ data: rows(600) })

    const wrapper = mountBlock({ type: 'table', source: '/api/plugins/example/items', columns: [{ key: 'name', label: 'Name' }] })
    await flushPromises()

    expect(wrapper.findAll('tbody tr')).toHaveLength(500)
    expect(wrapper.text()).toContain('500 of 600')
  })
})

describe('action block', () => {
  it('asks for the confirm text before it sends anything', async () => {
    axios.request.mockResolvedValue({ data: {} })

    const wrapper = mountBlock({
      type: 'action',
      label: 'Start',
      method: 'POST',
      path: '/api/plugins/example/start',
      confirm: 'Start the job?',
    })
    await wrapper.find('button').trigger('click')

    expect(wrapper.text()).toContain('Start the job?')
    expect(axios.request).not.toHaveBeenCalled()
  })

  it('sends the write with the CSRF header once the confirm text is accepted', async () => {
    axios.request.mockResolvedValue({ data: {} })

    const wrapper = mountBlock({
      type: 'action',
      label: 'Start',
      method: 'POST',
      path: '/api/plugins/example/start',
      confirm: 'Start the job?',
    })
    await wrapper.find('button').trigger('click')
    await wrapper.find('[data-test="confirm"]').trigger('click')
    await flushPromises()

    expect(axios.request).toHaveBeenCalledTimes(1)
    const config = axios.request.mock.calls[0][0]
    expect(config.method).toBe('POST')
    expect(config.url).toContain('/api/plugins/example/start')
    expect(config.headers['X-CSRF-Token']).toBe('token-1')
  })
})

describe('prefix of the data paths', () => {
  it('makes no request for a key value source outside the prefix of the plugin', async () => {
    const wrapper = mountBlock({ type: 'keyValue', source: '/api/tokens' })
    await flushPromises()

    expect(axios.get).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('/api/tokens')
  })

  it('makes no request for a form target outside the prefix of the plugin', async () => {
    const wrapper = mountBlock({
      type: 'form',
      source: '/api/plugins/example/config',
      target: '/api/tokens',
      method: 'PUT',
      schema: { type: 'object', properties: {} },
    })
    await flushPromises()

    expect(axios.request).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('/api/tokens')
  })
})

describe('status block', () => {
  it('polls at the refresh interval and stops while the page is hidden', async () => {
    vi.useFakeTimers()
    axios.get.mockResolvedValue({ data: { state: 'running', message: 'half way', progress: 0.5 } })

    mountBlock({ type: 'status', source: '/api/plugins/example/status', refreshSeconds: 5 })
    await flushPromises()
    expect(axios.get).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(5000)
    await flushPromises()
    expect(axios.get).toHaveBeenCalledTimes(2)

    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    vi.advanceTimersByTime(20000)
    await flushPromises()
    expect(axios.get, 'requests while the page is hidden').toHaveBeenCalledTimes(2)

    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
  })
})

describe('unknown block', () => {
  it('shows an unsupported note in its place and draws nothing else', () => {
    const wrapper = mountBlock({ type: 'chart', source: '/api/plugins/example/chart' })

    expect(wrapper.text()).toContain('Unsupported block')
  })
})
