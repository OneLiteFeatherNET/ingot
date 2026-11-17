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

import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import MdDataTable from './MdDataTable.vue'

// Every test mounts its own table with its own rows, so no test depends on another.
const COLUMNS = [
  { key: 'name', label: 'Name', sortable: true },
  { key: 'type', label: 'Type' },
  { key: 'size', label: 'Size', sortable: true, priority: 'low' as const },
]

const ROWS = [
  { name: 'releases', type: 'maven', size: 30 },
  { name: 'snapshots', type: 'maven', size: 5 },
  { name: 'docs', type: 'raw', size: 12 },
]

type CellScope = { row: unknown, column: unknown, value: unknown }

const mountTable = (props: Record<string, unknown> = {}, slots: Record<string, (scope: CellScope) => unknown> = {}) =>
  mount(MdDataTable, {
    props: { caption: 'Repositories', columns: COLUMNS, rows: ROWS, ...props },
    slots,
  })

const headers = (wrapper: ReturnType<typeof mountTable>) => wrapper.findAll('thead th')
const bodyNames = (wrapper: ReturnType<typeof mountTable>) =>
  wrapper.findAll('tbody tr').map((row) => row.find('th').text())

describe('MdDataTable structure', () => {
  it('names the table with its caption', () => {
    const wrapper = mountTable()

    expect(wrapper.find('caption').text()).toBe('Repositories')
  })

  it('gives each column header the scope col', () => {
    const wrapper = mountTable()

    expect(headers(wrapper).map((header) => header.attributes('scope'))).toEqual(['col', 'col', 'col'])
  })

  it('makes the first cell of each body row the row header with the scope row', () => {
    const wrapper = mountTable()

    const rowHeaders = wrapper.findAll('tbody th')

    expect(rowHeaders.map((cell) => cell.text())).toEqual(['releases', 'snapshots', 'docs'])
    expect(rowHeaders.map((cell) => cell.attributes('scope'))).toEqual(['row', 'row', 'row'])
  })

  it('renders one body cell per remaining column for each row', () => {
    const wrapper = mountTable()

    const firstRow = wrapper.findAll('tbody tr')[0].findAll('td').map((cell) => cell.text())

    expect(firstRow).toEqual(['maven', '30'])
  })

  it('renders the cell slot in place of the plain value', () => {
    const wrapper = mountTable({}, {
      cell: ({ value }: { value: unknown }) => h('b', `${String(value)} items`),
    })

    expect(wrapper.find('tbody td b').text()).toBe('maven items')
  })

  it('puts the scroll region on the root, named after the caption, and focusable for keyboard scrolling', () => {
    const wrapper = mountTable()

    expect(wrapper.attributes('role')).toBe('region')
    expect(wrapper.attributes('aria-label'), 'the region has the caption as its name').toBe('Repositories')
    expect(wrapper.attributes('tabindex')).toBe('0')
    expect(wrapper.classes(), 'the region scrolls on narrow widths').toContain('overflow-x-auto')
  })

  it('carries the md-data-table anchor on the root and marks the header and the body as parts', () => {
    const wrapper = mountTable()

    expect(wrapper.attributes('data-ingot-component')).toBe('md-data-table')
    expect(wrapper.find('thead').attributes('data-ingot-part')).toBe('header')
    expect(wrapper.find('tbody tr').attributes('data-ingot-part')).toBe('row')
  })
})

describe('MdDataTable sorting', () => {
  it('marks a sortable column with aria-sort none while it is not the sort key', () => {
    const wrapper = mountTable()

    const sortable = headers(wrapper).map((header) => header.attributes('aria-sort'))

    expect(sortable, 'only sortable columns carry aria-sort').toEqual(['none', undefined, 'none'])
  })

  it('keeps the rows in the given order until a column is chosen', () => {
    const wrapper = mountTable()

    expect(bodyNames(wrapper)).toEqual(['releases', 'snapshots', 'docs'])
  })

  it('sorts ascending on the first click and marks the header aria-sort ascending', async () => {
    const wrapper = mountTable()

    await headers(wrapper)[0].find('button').trigger('click')

    expect(bodyNames(wrapper)).toEqual(['docs', 'releases', 'snapshots'])
    expect(headers(wrapper)[0].attributes('aria-sort')).toBe('ascending')
  })

  it('sorts descending on the second click on the same column', async () => {
    const wrapper = mountTable()
    const button = headers(wrapper)[0].find('button')

    await button.trigger('click')
    await button.trigger('click')

    expect(bodyNames(wrapper)).toEqual(['snapshots', 'releases', 'docs'])
    expect(headers(wrapper)[0].attributes('aria-sort')).toBe('descending')
  })

  it('sorts numbers by value, not by text', async () => {
    const wrapper = mountTable()

    await headers(wrapper)[2].find('button').trigger('click')

    expect(bodyNames(wrapper)).toEqual(['snapshots', 'docs', 'releases'])
  })

  it('takes the sort from the model value when one is given, and emits the next sort when a header is chosen', async () => {
    const wrapper = mountTable({ modelValue: { key: 'name', direction: 'descending' } })

    expect(headers(wrapper)[0].attributes('aria-sort')).toBe('descending')

    await headers(wrapper)[2].find('button').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[{ key: 'size', direction: 'ascending' }]])
  })
})

describe('MdDataTable priority', () => {
  it('hides a low-priority column below the breakpoint, and keeps the others', () => {
    const wrapper = mountTable()

    const classes = headers(wrapper).map((header) => header.classes())

    expect(classes[0], 'a high-priority column is always shown').not.toContain('hidden')
    expect(classes[2], 'a low-priority column is hidden on narrow widths').toContain('hidden')
  })
})
