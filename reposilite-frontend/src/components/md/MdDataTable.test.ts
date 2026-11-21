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
import { sortRows } from './MdDataTable.sort'

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

  it.each([
    ['medium', 'medium:table-cell'],
    ['expanded', 'expanded:table-cell'],
    ['large', 'large:table-cell'],
    ['xlarge', 'xlarge:table-cell'],
  ] as const)('hides a column with the priority %s below that window class, in its header and its body cells', (priority, shown) => {
    const wrapper = mountTable({ columns: [COLUMNS[0], COLUMNS[1], { ...COLUMNS[2], priority }] })

    const cells = [headers(wrapper)[2], ...wrapper.findAll('tbody tr').map((row) => row.findAll('td')[1])]

    expect(cells.map((cell) => cell.classes().includes('hidden') && cell.classes().includes(shown))).toEqual([true, true, true, true])
  })

  it('shows a column with the priority base at every width', () => {
    const wrapper = mountTable({ columns: [COLUMNS[0], { ...COLUMNS[1], priority: 'base' as const }, COLUMNS[2]] })

    expect(headers(wrapper)[1].classes()).not.toContain('hidden')
  })
})

describe('MdDataTable sortable header', () => {
  it('holds a button in every sortable header and none in the others', () => {
    const wrapper = mountTable()

    expect(headers(wrapper).map((header) => header.find('button').exists())).toEqual([true, false, true])
  })

  it('reverses the order on a second activation, as sortRows does', async () => {
    const wrapper = mountTable()
    const button = headers(wrapper)[2].find('button')

    await button.trigger('click')
    const ascending = bodyNames(wrapper)
    await button.trigger('click')

    expect(bodyNames(wrapper)).toEqual(sortRows(ROWS, 'size', 'descending').map((row) => row.name))
    expect(bodyNames(wrapper), 'the second order is the first one reversed').toEqual([...ascending].reverse())
  })
})

describe('MdDataTable caption', () => {
  it('shows the caption by default', () => {
    const wrapper = mountTable()

    expect(wrapper.find('caption').classes(), 'a visible caption is not hidden for screen readers').not.toContain('sr-only')
  })

  it('keeps the caption in the DOM but visually hidden when captionHidden is set', () => {
    const wrapper = mountTable({ captionHidden: true })

    expect(wrapper.find('caption').text(), 'the caption stays for screen readers').toBe('Repositories')
    expect(wrapper.find('caption').classes()).toContain('sr-only')
  })
})

describe('MdDataTable sticky header', () => {
  it('does not pin the header cells by default', () => {
    const wrapper = mountTable()

    expect(headers(wrapper).map((header) => header.classes().includes('sticky'))).toEqual([false, false, false])
  })

  it('pins every header cell to the top of the scroll region when stickyHeader is set', () => {
    const wrapper = mountTable({ stickyHeader: true })

    const classes = headers(wrapper).map((header) => header.classes())

    expect(classes.map((names) => names.includes('sticky') && names.includes('top-0'))).toEqual([true, true, true])
  })

  it('gives the pinned header an opaque background, so scrolled rows do not show through it', () => {
    const wrapper = mountTable({ stickyHeader: true })

    expect(headers(wrapper)[0].classes()).toContain('bg-surface')
  })

  it('leaves the body cells in place when stickyHeader is set', () => {
    const wrapper = mountTable({ stickyHeader: true })

    const pinnedBodyCells = wrapper.findAll('tbody th, tbody td').filter((cell) => cell.classes().includes('sticky'))

    expect(pinnedBodyCells).toEqual([])
  })
})

describe('MdDataTable alignment', () => {
  // The alignment of a cell as its text-start, text-end or text-center class, or undefined when it has none.
  const alignmentOf = (cell: { classes(): string[] }) =>
    (['start', 'end', 'center'] as const).find((align) => cell.classes().includes(`text-${align}`))

  it('aligns every header and body cell to the start edge when no column sets an align', () => {
    const wrapper = mountTable()

    const cells = [...wrapper.findAll('thead th'), ...wrapper.findAll('tbody th'), ...wrapper.findAll('tbody td')]

    expect(cells.map(alignmentOf)).toEqual(Array(cells.length).fill('start'))
  })

  it('aligns a column to the end edge in its header and in every body cell', () => {
    const wrapper = mountTable({
      columns: [COLUMNS[0], COLUMNS[1], { ...COLUMNS[2], align: 'end' as const }],
    })

    expect(wrapper.findAll('thead th').map(alignmentOf)).toEqual(['start', 'start', 'end'])
    expect(wrapper.findAll('tbody tr').map((row) => alignmentOf(row.findAll('td')[1]))).toEqual(['end', 'end', 'end'])
  })

  it('aligns a column to the center edge in its header and in every body cell', () => {
    const wrapper = mountTable({
      columns: [COLUMNS[0], { ...COLUMNS[1], align: 'center' as const }, COLUMNS[2]],
    })

    expect(wrapper.findAll('thead th').map(alignmentOf)).toEqual(['start', 'center', 'start'])
    expect(wrapper.findAll('tbody tr').map((row) => alignmentOf(row.findAll('td')[0]))).toEqual(['center', 'center', 'center'])
  })

  it('aligns the row header by the align of the first column', () => {
    const wrapper = mountTable({
      columns: [{ ...COLUMNS[0], align: 'end' as const }, COLUMNS[1], COLUMNS[2]],
    })

    expect(wrapper.findAll('tbody th').map(alignmentOf)).toEqual(['end', 'end', 'end'])
  })
})

describe('MdDataTable for a page that owns its rows', () => {
  it('names the scroll region with the label when one is given, and keeps the caption for the table', () => {
    const wrapper = mountTable({ label: 'Repositories table' })

    expect(wrapper.attributes('aria-label'), 'the region takes the label').toBe('Repositories table')
    expect(wrapper.find('caption').text(), 'the caption still names the table').toBe('Repositories')
  })

  it('keeps the rows in the given order when they come sorted, while the header still shows the sort', () => {
    const wrapper = mountTable({ presorted: true, modelValue: { key: 'name', direction: 'ascending' } })

    expect(bodyNames(wrapper), 'the table does not sort the rows again').toEqual(['releases', 'snapshots', 'docs'])
    expect(headers(wrapper)[0].attributes('aria-sort')).toBe('ascending')
  })

  it('keeps the element of a row with its key when the rows change order', async () => {
    const wrapper = mountTable({ rowKey: 'name' })
    const docs = wrapper.findAll('tbody tr')[2].element

    await wrapper.setProps({ rows: [ROWS[2], ROWS[0], ROWS[1]] })

    expect(wrapper.findAll('tbody tr')[0].element, 'the row moved with its key').toBe(docs)
  })

  it('puts the attributes of rowAttrs on the element of each body row, each with the value of its column', () => {
    const wrapper = mountTable({ rowAttrs: { 'data-test-name': 'name' } })

    expect(wrapper.findAll('tbody tr').map((row) => row.attributes('data-test-name'))).toEqual(['releases', 'snapshots', 'docs'])
  })

  it('lets a fill column take the remaining width, so its cells can truncate', () => {
    const wrapper = mountTable({ columns: [COLUMNS[0], { ...COLUMNS[1], fill: true }, COLUMNS[2]] })

    const cells = [headers(wrapper)[1], ...wrapper.findAll('tbody tr').map((row) => row.findAll('td')[0])]

    expect(cells.map((cell) => cell.classes().includes('w-full') && cell.classes().includes('max-w-0'))).toEqual([true, true, true, true])
  })

  it('renders the empty slot in one cell across every column when there are no rows', () => {
    const wrapper = mountTable({ rows: [] }, { empty: () => h('p', 'No repositories yet.') })

    const cells = wrapper.findAll('tbody td')

    expect(cells.map((cell) => cell.text())).toEqual(['No repositories yet.'])
    expect(cells[0].attributes('colspan')).toBe('3')
  })

  it('leaves the empty slot out while there are rows', () => {
    const wrapper = mountTable({}, { empty: () => h('p', 'No repositories yet.') })

    expect(wrapper.text()).not.toContain('No repositories yet.')
  })

  it('centres every cell vertically and keeps each heading on one line', () => {
    const wrapper = mountTable()

    const cells = [...wrapper.findAll('thead th'), ...wrapper.findAll('tbody th'), ...wrapper.findAll('tbody td')]

    expect(cells.filter((cell) => !cell.classes().includes('align-middle')).length, 'every cell is centred').toBe(0)
    expect(headers(wrapper).map((header) => header.classes().includes('whitespace-nowrap'))).toEqual([true, true, true])
  })

  it('gives a sort button a target of at least 24 by 24 pixels', () => {
    const wrapper = mountTable()

    expect(headers(wrapper)[0].find('button').classes()).toEqual(expect.arrayContaining(['min-h-6', 'min-w-6']))
  })
})
