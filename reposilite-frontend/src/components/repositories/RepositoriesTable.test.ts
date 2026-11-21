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

import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import RepositoriesTable from './RepositoriesTable.vue'
import type { RepositoryRow, SortDirection, SortKey } from './repositoryRows'

// Every test mounts its own table with its own router and rows, so no test depends on another.
let wrapper: VueWrapper

afterEach(() => {
  wrapper?.unmount()
})

const rowOf = (name: string, index: number): RepositoryRow => ({
  index,
  id: name,
  name,
  unnamed: false,
  route: name,
  description: '',
  tags: [],
  type: { key: 'HOSTED', label: 'Hosted', derived: false },
  visibility: 'Public',
  storage: 'File system',
  writePolicy: null,
  versionPolicy: 'RELEASE',
  address: `https://repo.example.net/${name}`,
  unsaved: false,
})

const ROWS = [rowOf('releases', 0), rowOf('snapshots', 1)]

interface TableProps {
  rows?: RepositoryRow[]
  sortKey?: SortKey
  sortDirection?: SortDirection
  versionPolicy?: boolean
  emptyText?: string
}

const mountTable = async (props: TableProps = {}) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { name: 'settings-repositories', path: '/settings/repositories', component: { render: () => null } },
      { name: 'settings-repository', path: '/settings/repositories/:id', component: { render: () => null } },
    ],
  })
  await router.push('/settings/repositories')
  const mounted: unknown = mount(RepositoriesTable, {
    props: { rows: ROWS, caption: 'Repositories', ...props },
    global: { plugins: [router] },
  })
  wrapper = mounted as VueWrapper
  return wrapper
}

// The data-ingot-part values inside one element, read from the attributes and not by a selector.
const partsIn = (element: ReturnType<VueWrapper['find']>) =>
  element.findAll('*').map((inner) => inner.attributes('data-ingot-part')).filter(Boolean)

const headerNamed = (label: string) => wrapper.findAll('thead th').find((header) => header.text().startsWith(label))!

describe('the repositories table on the shared data table', () => {
  it.each([
    ['Name', 'name'],
    ['Type', 'type'],
    ['Visibility', 'visibility'],
    ['Storage', 'storage'],
  ])('asks the page to sort by %s when its header button is chosen', async (label, key) => {
    await mountTable()

    await headerNamed(label).find('button').trigger('click')

    expect(wrapper.emitted('sort'), `the ${label} header asks for its sort`).toEqual([[key]])
  })

  it('marks the sorted column with aria-sort and shows the rows in the order the page gives', async () => {
    await mountTable({ rows: [ROWS[1], ROWS[0]], sortKey: 'name', sortDirection: 'descending' })

    expect(headerNamed('Name').attributes('aria-sort'), 'the name column is sorted descending').toBe('descending')
    expect(wrapper.findAll('tbody th[scope="row"]').map((cell) => cell.text()), 'the rows keep the order of the page')
      .toEqual(['snapshots', 'releases'])
  })

  it('draws the table with the shared data table inside the repositories-table component', async () => {
    await mountTable()

    expect(wrapper.attributes('data-ingot-component'), 'the root keeps its anchor').toBe('repositories-table')
    const region = wrapper.find('[role="region"]')
    expect(region.attributes('data-ingot-component'), 'the table is the shared one').toBe('md-data-table')
    expect(region.attributes('aria-label'), 'the scroll region keeps its name').toBe('Repositories table')
  })

  it('names each row with its repository and keeps the name cell anchor', async () => {
    await mountTable()

    const rows = wrapper.findAll('tbody tr')
    expect(rows.map((row) => row.attributes('data-ingot-repository'))).toEqual(['releases', 'snapshots'])
    expect(rows.map((row) => partsIn(row.find('th')))).toEqual([['cell-name'], ['cell-name']])
  })

  it('gives the actions cell the shared cell padding', async () => {
    await mountTable()

    const actions = wrapper.findAll('tbody tr')[0].findAll('td').at(-1)!

    expect(actions.classes(), 'the actions cell has the padding of every other cell').toEqual(expect.arrayContaining(['px-4', 'py-3']))
  })

  it('hides the visibility, version policy and storage columns below the xlarge window, and the address below large', async () => {
    await mountTable({ versionPolicy: true })

    const shownFrom = (label: string) => headerNamed(label).classes().filter((name) => name.endsWith(':table-cell'))

    expect(shownFrom('Visibility')).toEqual(['xlarge:table-cell'])
    expect(shownFrom('Version policy')).toEqual(['xlarge:table-cell'])
    expect(shownFrom('Storage')).toEqual(['xlarge:table-cell'])
    expect(shownFrom('Address')).toEqual(['large:table-cell'])
  })

  it('shows the empty text with its anchor when there are no rows', async () => {
    await mountTable({ rows: [], emptyText: 'No repositories match.' })

    const cell = wrapper.find('tbody td')
    expect(partsIn(cell), 'the empty text keeps its part').toEqual(['empty'])
    expect(cell.text()).toBe('No repositories match.')
    expect(cell.findAll('*').map((element) => element.attributes('data-ingot-state')).filter(Boolean)).toEqual(['empty'])
  })
})
