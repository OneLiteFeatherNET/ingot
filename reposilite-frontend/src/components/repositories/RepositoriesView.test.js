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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { configurationStoreKey } from '../../store/configuration'
import RepositoriesView from './RepositoriesView.vue'
import { loadedStore, repositorySchema, schemaFieldsOf } from './fixtures/repositories'

// The session and the toasts are replaced, so the page touches neither the network nor the DOM outside the table.
vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null }, isManager: { value: false } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))
vi.mock('../../store/placeholders', () => ({
  default: () => ({ basePath: '/', productionUrl: 'https://repo.example.net/' }),
  frontendSecretsHidden: () => false
}))

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

// A fresh router and store per test. The page is given the schema fields the served schema declares.
const mountPage = async ({ schema, fields } = {}) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { name: 'settings-repositories', path: '/settings/repositories', component: { render: () => null } },
      { name: 'settings-repository', path: '/settings/repositories/:id', component: { render: () => null } }
    ]
  })
  await router.push('/settings/repositories')
  const store = await loadedStore({ schema })
  wrapper = mount(RepositoriesView, {
    props: { schemaFields: fields ?? schemaFieldsOf(schema ?? repositorySchema({ versionPolicy: true, readOnly: true })) },
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: store }
    },
    attachTo: document.body
  })
  await flushPromises()
  return { wrapper, router, store }
}

// A row is found by the text of its name link, since an unsaved row carries the Unsaved marker in the same cell.
const rowOf = (id) => wrapper.findAll('tbody tr').find(row => row.find('th[scope="row"] a').text() === id)
const headerOf = (label) => wrapper.findAll('th').find(header => header.text().startsWith(label))
const sortButton = (label) => headerOf(label).find('button')
// The Actions menu is teleported to the body, so its items are read from the document.
const menuItems = () => [...document.querySelectorAll('[role="menu"] [role="menuitem"]')]
const openActions = async (name) => {
  wrapper.find(`button[aria-label="Actions for ${name}"]`).trigger('click')
  await flushPromises()
}
const statusText = () => wrapper.findAll('[role="status"]').map(region => region.text()).join(' | ')
const filterInput = () => wrapper.find('input[type="search"], input[type="text"]')

describe('the repositories table', () => {
  it('captions the table with the shown and the total count', async () => {
    await mountPage()

    expect(wrapper.find('table caption').text(), 'the caption counts the rows').toBe('Repositories, 5 of 5 shown')
  })

  it('names the page with an h2 and marks the columns and the name cells as headers', async () => {
    await mountPage()

    expect(wrapper.find('h2').text(), 'the page heading').toBe('Repositories')
    const columnHeaders = wrapper.findAll('thead th[scope="col"]').map(header => header.text())
    expect(columnHeaders.join(','), 'every column header is a column header').toContain('Name')
    expect(columnHeaders.join(','), 'the type column is named').toContain('Type')
    expect(rowOf('releases').find('th').attributes('scope'), 'the name cell is a row header').toBe('row')
  })

  it('sets aria-sort on the sorted column only, and flips it on the second activation', async () => {
    await mountPage()

    await sortButton('Name').trigger('click')
    expect(headerOf('Name').attributes('aria-sort'), 'the sorted column says ascending').toBe('ascending')
    expect(headerOf('Type').attributes('aria-sort'), 'an unsorted column carries no aria-sort').toBeUndefined()

    await sortButton('Name').trigger('click')
    expect(headerOf('Name').attributes('aria-sort'), 'the second activation sorts descending').toBe('descending')
    expect(wrapper.findAll('tbody th[scope="row"]').map(cell => cell.text())[0], 'the first row is the last name')
      .toBe('snapshots')
  })

  it('announces the sort in a status region', async () => {
    await mountPage()

    await sortButton('Type').trigger('click')

    expect(statusText(), 'the sort is announced').toContain('Sorted by Type, ascending')
  })

  it('reports the count of the filtered rows in a labelled status region', async () => {
    await mountPage()

    const input = filterInput()
    expect(wrapper.find(`label[for="${input.attributes('id')}"]`).text(), 'the filter has a label').toBe('Filter repositories')
    input.element.value = 'central'
    input.trigger('input')
    await flushPromises()

    expect(statusText(), 'the count of matches is announced').toContain('1 of 5 repositories shown')
    expect(wrapper.find('table caption').text(), 'the caption follows the filter').toBe('Repositories, 1 of 5 shown')
  })

  it('filters by a tag token, as the repository search does', async () => {
    await mountPage()

    const input = filterInput()
    input.element.value = 'tag:team-a'
    input.trigger('input')
    await flushPromises()

    expect(wrapper.findAll('tbody tr').map(row => row.find('th').text()), 'only the tagged repository is listed')
      .toEqual(['releases'])
  })

  it('names each type in words, and says derived for an untyped repository', async () => {
    await mountPage()

    expect(rowOf('releases').find('[data-test="type-badge"]').text(), 'a declared type is named').toContain('Hosted')
    expect(rowOf('releases').text(), 'a declared type is not derived').not.toContain('derived')
    expect(rowOf('snapshots').text(), 'an untyped hosted repository is derived').toContain('derived')
    expect(rowOf('central').text(), 'an untyped proxy names its type').toContain('Proxy')
  })

  it('shows the warning and no declare action for a Mixed repository', async () => {
    await mountPage()

    const row = rowOf('mixed')
    expect(row.text(), 'the Mixed type is named').toContain('Mixed')
    expect(row.text(), 'the warning is text').toContain('References local and remote repositories. Split it into a group and a proxy.')
    expect(row.findAll('button').map(button => button.text()), 'no declare action is offered').not.toContain('Declare type')
  })

  it('gives each row one Actions button named after the repository, and no inline action', async () => {
    await mountPage()

    const row = rowOf('releases')
    const names = row.findAll('a, button').map(control => control.attributes('aria-label'))
    expect(names, 'the row has the name link and one Actions button').toEqual([undefined, 'Actions for releases'])
  })

  it('names the items of the Actions menu after the repository', async () => {
    await mountPage()

    await openActions('releases')

    expect(menuItems().map(item => item.getAttribute('aria-label')), 'the items keep the names of the inline actions')
      .toEqual(['Edit releases', 'Copy address of releases', 'Delete releases'])
  })

  it('makes the Edit item a link to the repository detail, so it can open in a new tab', async () => {
    await mountPage()

    await openActions('releases')

    const edit = menuItems().find(item => item.getAttribute('aria-label') === 'Edit releases')
    expect(edit.tagName, 'the Edit item is a link').toBe('A')
    expect(edit.getAttribute('href'), 'the link is the address of the repository detail').toBe('/settings/repositories/releases')
  })

  it('keeps the name link before the Actions button in one row', async () => {
    await mountPage()

    const controls = rowOf('releases').findAll('a, button')
    expect(controls[0].text(), 'the first control of the row is the name link').toBe('releases')
    expect(controls[1].attributes('aria-label'), 'the next control is the Actions button').toBe('Actions for releases')
  })

  it('copies the address, reports Copied and returns the focus to the Actions button', async () => {
    const writeText = vi.fn(async () => {})
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    try {
      await mountPage()
      const trigger = wrapper.find('button[aria-label="Actions for releases"]')
      await openActions('releases')

      menuItems().find(item => item.getAttribute('aria-label') === 'Copy address of releases').click()
      await flushPromises()

      expect(writeText, 'the address is written to the clipboard').toHaveBeenCalledWith('https://repo.example.net/releases')
      expect(statusText(), 'the copy is reported').toContain('Copied')
      expect(document.activeElement, 'the focus returns to the Actions button').toBe(trigger.element)
    } finally {
      delete navigator.clipboard
    }
  })

  it('truncates the name and the address on one line, with the full value as title', async () => {
    await mountPage()

    const row = rowOf('releases')
    const name = row.find('th[scope="row"] a')
    const address = row.find('code')
    expect(name.classes(), 'the name is truncated with an ellipsis').toContain('truncate')
    expect(name.classes(), 'the name is never broken inside a word').not.toContain('break-all')
    expect(name.attributes('title'), 'the title is the full name').toBe('releases')
    expect(address.classes(), 'the address is truncated with an ellipsis').toContain('truncate')
    expect(address.classes(), 'the address is never broken inside a word').not.toContain('break-all')
    expect(address.attributes('title'), 'the title is the full address').toBe('https://repo.example.net/releases')
  })

  it('keeps the Write policy heading on one line', async () => {
    await mountPage()

    expect(headerOf('Write policy').classes(), 'the heading does not wrap').toContain('whitespace-nowrap')
  })

  it('hides the low-priority columns below their breakpoint, Name, Type and Actions never', async () => {
    await mountPage()

    const hidden = (label) => headerOf(label).classes()
    expect(hidden('Storage'), 'Storage is the first to go').toEqual(expect.arrayContaining(['hidden', 'min-[1920px]:table-cell']))
    expect(hidden('Version policy'), 'Version policy is shown from 1600 pixels').toEqual(expect.arrayContaining(['hidden', 'min-[1600px]:table-cell']))
    expect(hidden('Visibility'), 'Visibility is shown from 1600 pixels').toEqual(expect.arrayContaining(['hidden', 'min-[1600px]:table-cell']))
    expect(hidden('Write policy'), 'Write policy is shown from 1240 pixels').toEqual(expect.arrayContaining(['hidden', 'min-[1240px]:table-cell']))
    expect(hidden('Address'), 'Address is shown from 1240 pixels').toEqual(expect.arrayContaining(['hidden', 'min-[1240px]:table-cell']))
    expect(hidden('Name'), 'Name is always shown').not.toContain('hidden')
    expect(hidden('Type'), 'Type is always shown').not.toContain('hidden')
    expect(hidden('Actions'), 'Actions is always shown').not.toContain('hidden')
  })

  it('shows the Version policy column only when the schema declares it', async () => {
    await mountPage({ schema: repositorySchema({ readOnly: true }) })
    expect(wrapper.findAll('th').map(header => header.text()), 'no version policy column without the field')
      .not.toContain('Version policy')

    wrapper.unmount()
    await mountPage({ schema: repositorySchema({ versionPolicy: true, readOnly: true }) })
    expect(headerOf('Version policy'), 'the column is there with the field').toBeDefined()
  })

  it('shows the Write policy column with Allow or Allow once when the schema has no readOnly', async () => {
    await mountPage({ schema: repositorySchema({ redeployment: true }) })

    expect(headerOf('Write policy'), 'the column is shown from redeployment alone').toBeDefined()
    expect(rowOf('snapshots').text(), 'a redeploying repository is Allow').toContain('Allow')
    expect(rowOf('releases').text(), 'a repository that refuses redeploys is Allow once').toContain('Allow once')
  })

  it('marks a repository that is not saved yet as Unsaved', async () => {
    const { store } = await mountPage()
    store.addEntry('maven', 'repositories', { id: 'fresh', visibility: 'PUBLIC' })
    await flushPromises()

    expect(rowOf('fresh').text(), 'the new row says it is not saved').toContain('Unsaved')
    expect(rowOf('releases').text(), 'a saved row is not marked').not.toContain('Unsaved')
  })

  it('shows a new row at once, counts the change for the save bar, and drops it on discard', async () => {
    const { store } = await mountPage()

    store.addEntry('maven', 'repositories', { id: 'fresh' })
    await flushPromises()
    expect(rowOf('fresh'), 'the new repository is listed at once').toBeDefined()
    expect(store.hasUnsavedChanges.value, 'the save bar has a change to show').toBe(true)

    store.discardChanges()
    await flushPromises()
    expect(rowOf('fresh'), 'the discarded repository is gone').toBeUndefined()
  })
})

// The served maven schema with the domain-level field, which the Maven options section of the page shows.
const schemaWithOption = () => {
  const schema = repositorySchema({ versionPolicy: true, readOnly: true })
  schema.properties.resolutionCacheClusterMaxAgeSeconds = { type: 'integer', title: 'Resolution cache maximum age in cluster mode' }
  return schema
}

describe('the Maven options section of the page', () => {
  it('comes after the table, below the repositories', async () => {
    await mountPage({ schema: schemaWithOption() })

    const section = wrapper.get('[data-test="maven-options"]')
    const table = wrapper.get('table')
    expect(table.element.compareDocumentPosition(section.element) & Node.DOCUMENT_POSITION_FOLLOWING, 'the section follows the table').toBeTruthy()
  })

  it('is not shown when the served schema has no domain-level field', async () => {
    await mountPage()

    expect(wrapper.find('[data-test="maven-options"]').exists()).toBe(false)
  })
})
