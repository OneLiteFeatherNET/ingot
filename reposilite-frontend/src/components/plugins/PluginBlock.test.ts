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
import MdCard from '../md/MdCard.vue'
import type { PluginBlockDefinition } from './pluginBlocks'

// The mocks are plain properties of one object, so tests reach them without going through axios methods.
const http = vi.hoisted(() => ({
  get: vi.fn<typeof axios.get>(),
  request: vi.fn<typeof axios.request>(),
  post: vi.fn<typeof axios.post>(),
}))

vi.mock('axios', () => ({
  default: {
    ...http,
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
}))

const PLUGIN = 'example'

const rows = (count: number) => Array.from({ length: count }, (_, index) => ({ name: `row ${index}` }))
const items = (count: number) => Array.from({ length: count }, (_, index) => ({ title: `item ${index}` }))

beforeEach(() => {
  vi.clearAllMocks()
  document.cookie = 'ingot_csrf=token-1; path=/'
})

afterEach(() => {
  vi.useRealTimers()
  document.cookie = 'ingot_csrf=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

const mountBlock = (block: PluginBlockDefinition) => mount(PluginBlock, { props: { plugin: PLUGIN, block } })

describe('plugin blocks draw text, never markup', () => {
  it('shows markup in a text block as plain characters', () => {
    const wrapper = mountBlock({ type: 'text', text: '<img src=x onerror=alert(1)>' })

    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists(), 'an img element drawn from the text').toBe(false)
  })

  it('shows markup in a table cell as plain characters', async () => {
    http.get.mockResolvedValue({ data: [{ name: '<b>bold</b>' }] })

    const wrapper = mountBlock({ type: 'table', source: '/api/plugins/example/items', columns: [{ key: 'name', label: 'Name' }] })
    await flushPromises()

    expect(wrapper.text()).toContain('<b>bold</b>')
    expect(wrapper.find('b').exists(), 'a bold element drawn from the cell').toBe(false)
  })
})

describe('table block', () => {
  it('shows at most 500 rows and says how many it cut', async () => {
    http.get.mockResolvedValue({ data: rows(600) })

    const wrapper = mountBlock({ type: 'table', source: '/api/plugins/example/items', columns: [{ key: 'name', label: 'Name' }] })
    await flushPromises()

    expect(wrapper.findAll('tbody tr')).toHaveLength(500)
    expect(wrapper.text()).toContain('500 of 600')
  })
})

describe('table block on the shared data table', () => {
  const mountTable = async (label?: string) => {
    http.get.mockResolvedValue({ data: [{ name: 'alpha', size: '3' }] })
    const wrapper = mountBlock({
      type: 'table',
      label,
      source: '/api/plugins/example/items',
      columns: [{ key: 'name', label: 'Name' }, { key: 'size', label: 'Size' }],
    })
    await flushPromises()
    return wrapper
  }

  it('names the table by its block title in a caption that only screen readers see', async () => {
    const wrapper = await mountTable('Installed items')

    const caption = wrapper.find('caption')
    expect(caption.text()).toBe('Installed items')
    expect(caption.classes(), 'the caption is visually hidden').toContain('sr-only')
  })

  it('names a table without a title after its plugin', async () => {
    const wrapper = await mountTable()

    expect(wrapper.find('caption').text()).toBe('example table')
  })

  it('makes the first column the row header of each row', async () => {
    const wrapper = await mountTable('Installed items')

    expect(wrapper.find('tbody th[scope="row"]').text()).toBe('alpha')
    expect(wrapper.findAll('tbody td').map(cell => cell.text())).toEqual(['3'])
  })
})

describe('action block', () => {
  it('asks for the confirm text before it sends anything', async () => {
    http.request.mockResolvedValue({ data: {} })

    const wrapper = mountBlock({
      type: 'action',
      label: 'Start',
      method: 'POST',
      path: '/api/plugins/example/start',
      confirm: 'Start the job?',
    })
    await wrapper.find('button').trigger('click')

    expect(wrapper.text()).toContain('Start the job?')
    expect(http.request).not.toHaveBeenCalled()
  })

  it('asks for the confirm text on the shared card, one surface step above the block', async () => {
    const wrapper = mountBlock({
      type: 'action',
      label: 'Start',
      method: 'POST',
      path: '/api/plugins/example/start',
      confirm: 'Start the job?',
    })
    await wrapper.find('button').trigger('click')

    const confirm = wrapper.get('[data-test="confirm"]').element
    const card = wrapper.findAllComponents(MdCard).find(candidate => (candidate.element as Element).contains(confirm))
    expect(card, 'the confirmation sits on the card primitive, not on a hand-built container').toBeDefined()
    expect(card!.text(), 'the card holds the confirm text').toContain('Start the job?')
    expect(card!.props('tone'), 'the card keeps the container surface of the confirmation').toBe('container')
    expect(card!.props('edge'), 'the card draws no edge, like the tinted panel it replaces').toBe('none')
  })

  it('sends the write with the CSRF header once the confirm text is accepted', async () => {
    http.request.mockResolvedValue({ data: {} })

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

    expect(http.request).toHaveBeenCalledTimes(1)
    const config = http.request.mock.calls[0][0]
    expect(config.method).toBe('POST')
    expect(config.url).toContain('/api/plugins/example/start')
    expect(config.headers?.['X-CSRF-Token']).toBe('token-1')
  })
})

describe('prefix of the data paths', () => {
  it('makes no request for a key value source outside the prefix of the plugin', async () => {
    const wrapper = mountBlock({ type: 'keyValue', source: '/api/tokens' })
    await flushPromises()

    expect(http.get).not.toHaveBeenCalled()
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

    expect(http.request).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('/api/tokens')
  })
})

describe('status block', () => {
  it('polls at the refresh interval and stops while the page is hidden', async () => {
    vi.useFakeTimers()
    http.get.mockResolvedValue({ data: { state: 'running', message: 'half way', progress: 0.5 } })

    mountBlock({ type: 'status', source: '/api/plugins/example/status', refreshSeconds: 5 })
    await flushPromises()
    expect(http.get).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(5000)
    await flushPromises()
    expect(http.get).toHaveBeenCalledTimes(2)

    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    vi.advanceTimersByTime(20000)
    await flushPromises()
    expect(http.get, 'requests while the page is hidden').toHaveBeenCalledTimes(2)

    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
  })
})

describe('unknown block', () => {
  it('shows an unsupported note in its place and draws nothing else', () => {
    const wrapper = mountBlock({ type: 'chart', source: '/api/plugins/example/chart' })

    expect(wrapper.text()).toContain('Unsupported block')
  })
})

describe('block in a reader page', () => {
  const mountReaderBlock = (block: PluginBlockDefinition) => mount(PluginBlock, { props: { plugin: PLUGIN, block, reader: true } })
  const WRITING_BLOCKS: PluginBlockDefinition[] = [
    { type: 'form', source: '/api/plugins/example/settings', target: '/api/plugins/example/settings', method: 'PUT', schema: {}, uiSchema: { type: 'VerticalLayout' } },
    { type: 'action', label: 'Rebuild', path: '/api/plugins/example/rebuild', method: 'POST' },
    { type: 'status', source: '/api/plugins/example/status', refreshSeconds: 5 },
  ]

  for (const block of WRITING_BLOCKS) {
    it(`shows the unsupported note for a ${block.type} block and sends no request`, async () => {
      http.get.mockResolvedValue({ data: {} })

      const wrapper = mountReaderBlock(block)
      await flushPromises()

      expect(wrapper.text(), `the ${block.type} block is not drawn for readers`).toContain(`Unsupported block: ${block.type}`)
      expect(wrapper.find('button').exists(), `no control of the ${block.type} block`).toBe(false)
      expect(http.get.mock.calls, `no read for the ${block.type} block`).toEqual([])
      expect(http.request.mock.calls, `no write for the ${block.type} block`).toEqual([])
    })
  }

  it('still draws a reading block for readers', async () => {
    http.get.mockResolvedValue({ data: { version: '1.0.0' } })

    const wrapper = mountReaderBlock({ type: 'keyValue', source: '/api/plugins/example/info' })
    await flushPromises()

    expect(wrapper.text(), 'the key value block reads its source for a reader').toContain('1.0.0')
  })
})

describe('code block', () => {
  const clipboardOf = (writeText: ReturnType<typeof vi.fn>) =>
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

  afterEach(() => {
    Reflect.deleteProperty(navigator, 'clipboard')
  })

  it('shows markup in the code text as plain characters', () => {
    const wrapper = mountBlock({ type: 'code', text: '<img src=x onerror=alert(1)>' })

    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists(), 'an img element drawn from the code').toBe(false)
  })

  it('copies the code text and nothing else, then announces the copy', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    clipboardOf(writeText)

    const wrapper = mountBlock({ type: 'code', label: 'Snippet', text: 'implementation("a:b:1")', language: 'kotlin' })
    await wrapper.findAll('button').find(button => button.text() === 'Copy')!.trigger('click')
    await flushPromises()

    expect(writeText, 'the text written to the clipboard').toHaveBeenCalledWith('implementation("a:b:1")')
    expect(wrapper.find('[role="status"]').text(), 'the status region').toBe('Copied')
  })

  it('selects the code text when the clipboard refuses the write', async () => {
    clipboardOf(vi.fn().mockRejectedValue(new Error('denied')))

    // The element is attached to the document, because a selection can only hold text that is in the page.
    const wrapper = mount(PluginBlock, { props: { plugin: PLUGIN, block: { type: 'code', text: 'select me' } }, attachTo: document.body })
    await wrapper.findAll('button').find(button => button.text() === 'Copy')!.trigger('click')
    await flushPromises()

    expect(window.getSelection()?.toString(), 'the selected text').toContain('select me')
    expect(wrapper.find('[role="status"]').text(), 'the status region').not.toBe('Copied')
    wrapper.unmount()
  })
})

describe('link block', () => {
  it('draws a same-origin path as a link in the same tab', () => {
    const wrapper = mountBlock({ type: 'link', label: 'Download', href: '/browse/repo/a/b.jar' })

    const link = wrapper.find('a')
    expect(link.exists(), 'a link element').toBe(true)
    expect(link.attributes('href')).toBe('/browse/repo/a/b.jar')
    expect(link.attributes('target'), 'a target attribute').toBeUndefined()
  })

  it('draws no link for a javascript address and says so', () => {
    const wrapper = mountBlock({ type: 'link', label: 'Get', href: 'javascript:alert(1)' })

    expect(wrapper.find('a').exists(), 'a link element for a javascript address').toBe(false)
    expect(wrapper.text()).toContain('Link not shown')
  })
})

describe('list block', () => {
  const LIST_SOURCE = '/api/plugins/example/items'

  it('draws a row with its title, subtitle and chips as characters', async () => {
    http.get.mockResolvedValue({
      data: { items: [{ title: '<b>bold</b>', subtitle: 'a line', chips: ['paper', '1.21'] }], next: null },
    })

    const wrapper = mountBlock({ type: 'list', source: LIST_SOURCE })
    await flushPromises()

    expect(wrapper.text()).toContain('<b>bold</b>')
    expect(wrapper.find('b').exists(), 'a bold element drawn from the title').toBe(false)
    expect(wrapper.text()).toContain('a line')
    expect(wrapper.text()).toContain('paper')
  })

  it('shows at most 100 rows and says how many items it cut', async () => {
    http.get.mockResolvedValue({ data: { items: items(120), next: 'page-2' } })

    const wrapper = mountBlock({ type: 'list', source: LIST_SOURCE })
    await flushPromises()

    expect(wrapper.findAll('li')).toHaveLength(100)
    expect(wrapper.text()).toContain('Showing 100 of 120 items')
    expect(wrapper.findAll('button').find(button => button.text() === 'Load more'), 'the load more control').toBeUndefined()
  })

  it('says how many items without a title it did not show', async () => {
    http.get.mockResolvedValue({ data: { items: [{ title: 'kept' }, { subtitle: 'untitled' }], next: null } })

    const wrapper = mountBlock({ type: 'list', source: LIST_SOURCE })
    await flushPromises()

    expect(wrapper.text()).toContain('kept')
    expect(wrapper.text()).toContain('Skipped 1 without a title')
  })

  it('shows the empty state when the source answers no items', async () => {
    http.get.mockResolvedValue({ data: { items: [], next: null } })

    const wrapper = mountBlock({ type: 'list', source: LIST_SOURCE })
    await flushPromises()

    expect(wrapper.text()).toContain('Nothing to show.')
  })

  it('sends the cursor of the next page with the load more control and appends the rows', async () => {
    http.get
      .mockResolvedValueOnce({ data: { items: [{ title: 'first' }], next: 'page-2' } })
      .mockResolvedValueOnce({ data: { items: [{ title: 'second' }], next: null } })

    const wrapper = mountBlock({ type: 'list', source: LIST_SOURCE })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === 'Load more')!.trigger('click')
    await flushPromises()

    expect(http.get.mock.calls[1][1]?.params, 'the query of the second request').toEqual({ cursor: 'page-2' })
    expect(wrapper.text(), 'the rows after the second page').toContain('first')
    expect(wrapper.text()).toContain('second')
  })

  it('draws no load more control when the response has no next cursor', async () => {
    http.get.mockResolvedValue({ data: { items: [{ title: 'only' }], next: null } })

    const wrapper = mountBlock({ type: 'list', source: LIST_SOURCE })
    await flushPromises()

    expect(wrapper.findAll('button').find(button => button.text() === 'Load more')).toBeUndefined()
  })

  it('makes no request for a list source outside the prefix of the plugin', async () => {
    const wrapper = mountBlock({ type: 'list', source: '/api/tokens' })
    await flushPromises()

    expect(http.get.mock.calls, 'requests made').toHaveLength(0)
    expect(wrapper.text()).toContain('/api/tokens')
  })

  it('draws a refused row link as a note and no link', async () => {
    http.get.mockResolvedValue({ data: { items: [{ title: 'bad', href: 'javascript:alert(1)' }], next: null } })

    const wrapper = mountBlock({ type: 'list', source: LIST_SOURCE })
    await flushPromises()

    expect(wrapper.find('a').exists(), 'a link element').toBe(false)
    expect(wrapper.text()).toContain('Link not shown')
  })
})

describe('controls of the blocks come from the md primitives', () => {
  it('draws the text filter as an MdTextField and the select filter as an MdSelect', () => {
    const wrapper = mountBlock({
      type: 'filter',
      controls: [
        { type: 'text', key: 'q', label: 'Search' },
        { type: 'select', key: 'kind', label: 'Kind', options: [{ value: 'a', label: 'Alpha' }] },
      ],
    })

    expect(wrapper.findComponent({ name: 'MdTextField' }).exists(), 'the text filter is an MdTextField').toBe(true)
    expect(wrapper.findComponent({ name: 'MdSelect' }).exists(), 'the select filter is an MdSelect').toBe(true)
  })

  it('writes a picked select option to the filter scope', async () => {
    const wrapper = mountBlock({
      type: 'filter',
      controls: [{ type: 'select', key: 'kind', label: 'Kind', options: [{ value: 'a', label: 'Alpha' }] }],
    })

    await wrapper.findComponent({ name: 'MdSelect' }).setValue('a')
    await flushPromises()

    expect(wrapper.findComponent({ name: 'MdSelect' }).props('modelValue'), 'the select shows the picked option').toBe('a')
  })

  it('draws the buttons of the action block as MdButton', async () => {
    const wrapper = mountBlock({
      type: 'action', label: 'Start', method: 'POST', path: '/api/plugins/example/start', confirm: 'Start the job?',
    })
    await wrapper.findComponent({ name: 'MdButton' }).trigger('click')

    expect(wrapper.findAllComponents({ name: 'MdButton' }), 'the action, confirm and cancel buttons').toHaveLength(3)
    expect(wrapper.findAll('button').length, 'no native button is left').toBe(wrapper.findAllComponents({ name: 'MdButton' }).length)
  })

  it('draws the progress of a status block as MdLinearProgress', async () => {
    http.get.mockResolvedValue({ data: { state: 'running', message: 'half way', progress: 0.5 } })

    const wrapper = mountBlock({ type: 'status', source: '/api/plugins/example/status' })
    await flushPromises()

    expect(wrapper.findComponent({ name: 'MdLinearProgress' }).exists(), 'the shared progress bar').toBe(true)
    expect(wrapper.find('progress').exists(), 'no native progress element').toBe(false)
  })
})
