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

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { reactive } from 'vue'
import axios from 'axios'
import PluginBlock from './PluginBlock.vue'
import { PLUGIN_FILTER_SCOPE, type PluginBlockDefinition, type PluginFilterScope } from './pluginBlocks'

vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    request: vi.fn(),
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
}))

// The debounce of the text field is replaced by a synchronous call, so the tests need no timer.
vi.mock('@vueuse/core', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@vueuse/core')>()),
  useDebounceFn: (fn: unknown) => fn,
}))

const PLUGIN = 'example'
const LIST_SOURCE = '/api/plugins/example/items'
const KIND_SELECT: PluginBlockDefinition = {
  type: 'filter',
  controls: [{ type: 'select', key: 'kind', label: 'Kind', options: [{ value: 'paper', label: 'Paper' }, { value: 'book', label: 'Book' }] }],
}
const LIST: PluginBlockDefinition = { type: 'list', source: LIST_SOURCE }

beforeEach(() => {
  vi.clearAllMocks()
  document.cookie = 'ingot_csrf=token-1; path=/'
})

// The filter and the list of one page share one scope, as the page view provides it.
const mountPage = (scope: PluginFilterScope, blocks: PluginBlockDefinition[]) =>
  blocks.map(block => mount(PluginBlock, {
    props: { plugin: PLUGIN, block },
    global: { provide: { [PLUGIN_FILTER_SCOPE as symbol]: scope } },
  }))

const newScope = (): PluginFilterScope => reactive({ values: {}, facets: {} })

const lastQuery = () => {
  const calls = vi.mocked(axios).get.mock.calls
  return calls[calls.length - 1][1]?.params
}

describe('filter block sends its values to the list', () => {
  it('sends the value of a select as a query parameter of the list', async () => {
    vi.mocked(axios).get.mockResolvedValue({ data: { items: [], next: null } })
    const scope = newScope()
    const [filter] = mountPage(scope, [KIND_SELECT, LIST])
    await flushPromises()

    await filter.find('select').setValue('paper')
    await flushPromises()

    expect(lastQuery(), 'the query of the list after the change').toEqual({ kind: 'paper' })
  })

  it('does not send an empty value of a select', async () => {
    vi.mocked(axios).get.mockResolvedValue({ data: { items: [], next: null } })
    const scope = newScope()
    const [filter] = mountPage(scope, [KIND_SELECT, LIST])
    await flushPromises()
    await filter.find('select').setValue('paper')
    await flushPromises()

    await filter.find('select').setValue('')
    await flushPromises()

    expect(scope.values, 'the values of the page after the empty choice').not.toHaveProperty('kind')
    expect(lastQuery(), 'the query of the list after the empty choice').toBeUndefined()
  })

  it('sends the text of a text field as a query parameter once the debounce has passed', async () => {
    vi.mocked(axios).get.mockResolvedValue({ data: { items: [], next: null } })
    const scope = newScope()
    const [filter] = mountPage(scope, [
      { type: 'filter', controls: [{ type: 'text', key: 'query', label: 'Search' }] },
      LIST,
    ])
    await flushPromises()

    await filter.find('input').setValue('paper')
    await flushPromises()

    expect(lastQuery(), 'the query of the list after the typing').toEqual({ query: 'paper' })
  })

  it('starts the list again from its first page when a value changes, so the cursor is dropped', async () => {
    vi.mocked(axios).get
      .mockResolvedValueOnce({ data: { items: [{ title: 'first' }], next: 'page-2' } })
      .mockResolvedValue({ data: { items: [{ title: 'filtered' }], next: null } })
    const scope = newScope()
    const [filter, list] = mountPage(scope, [KIND_SELECT, LIST])
    await flushPromises()
    await list.findAll('button').find(button => button.text() === 'Load more')!.trigger('click')
    await flushPromises()

    await filter.find('select').setValue('book')
    await flushPromises()

    expect(lastQuery(), 'the query after the change has no cursor').toEqual({ kind: 'book' })
    expect(list.text(), 'the rows after the change').not.toContain('first')
  })
})

describe('filter block counts', () => {
  it('shows the count of an option only when the list response has a facets entry for it', async () => {
    vi.mocked(axios).get.mockResolvedValue({
      data: { items: [], next: null, facets: { kind: [{ value: 'paper', count: 12 }] } },
    })
    const scope = newScope()
    const [filter] = mountPage(scope, [KIND_SELECT, LIST])
    await flushPromises()

    const options = filter.findAll('option').map(option => option.text())

    expect(options, 'the option labels').toContain('Paper (12)')
    expect(options, 'an option the response does not count').toContain('Book')
  })

  it('shows no count when the list response has no facets', async () => {
    vi.mocked(axios).get.mockResolvedValue({ data: { items: [], next: null } })
    const scope = newScope()
    const [filter] = mountPage(scope, [KIND_SELECT, LIST])
    await flushPromises()

    const options = filter.findAll('option').map(option => option.text())

    expect(options.some(text => text.includes('(')), 'the option labels without counts').toBe(false)
  })
})

describe('filter block draws only the controls the server accepts', () => {
  it('does not draw a control with the reserved key cursor', () => {
    const [filter] = mountPage(newScope(), [
      { type: 'filter', controls: [{ type: 'select', key: 'cursor', label: 'Page', options: [{ value: '1', label: 'One' }] }] },
    ])

    expect(filter.find('select').exists(), 'a select for the reserved key').toBe(false)
  })
})
