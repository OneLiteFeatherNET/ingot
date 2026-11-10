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
import { h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { configurationStoreKey } from '../../store/configuration'
import RepositoriesView from './RepositoriesView.vue'
import RepositoryDetail from './RepositoryDetail.vue'
import { loadedStore, repositorySchema } from './fixtures/repositories'

// A repository entry without an id is what a hand-edited configuration can hold. The page must still list it.
// The session is an administrator without a live client, so the wizard and the access card stay off the network.
vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { value: null }, isManager: { value: true } })
}))
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

// jsdom has no <dialog>; the wizard and the one-of renderer use it.
HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

// The first entry has no id; the other two are ordinary repositories.
const UNNAMED_SERVER = {
  maven: {
    repositories: [
      { visibility: 'PUBLIC', storageProvider: { type: 'fs' } },
      { id: 'releases', visibility: 'PUBLIC', storageProvider: { type: 'fs' } },
      { id: 'snapshots', visibility: 'PRIVATE', storageProvider: { type: 's3', bucketName: 'snap' } }
    ]
  }
}

const routesOf = () => [
  { name: 'settings-repositories', path: '/settings/repositories', component: RepositoriesView },
  { name: 'settings-repository', path: '/settings/repositories/:id', component: RepositoryDetail, props: true }
]

const mountList = async ({ address = '/settings/repositories' } = {}) => {
  const router = createRouter({ history: createMemoryHistory(), routes: routesOf() })
  await router.push(address)
  const store = await loadedStore({ server: UNNAMED_SERVER, schema: repositorySchema({ versionPolicy: true, readOnly: true }) })
  wrapper = mount({ render: () => h(RouterView) }, {
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: store }
    },
    attachTo: document.body
  })
  await flushPromises()
  return { wrapper, router, store }
}

const rowHeaders = () => wrapper.findAll('tbody th[scope="row"]').map(cell => cell.text())
const unnamedRow = () => wrapper.findAll('tbody tr').find(row => row.text().includes('Unnamed repository'))
const buttonLabelled = (label) => wrapper.find(`[aria-label="${label}"]`)
const sortButton = (label) => wrapper.findAll('thead th').find(header => header.text().startsWith(label)).find('button')
const deleteDialog = () => document.querySelector('#delete-entry-form')

describe('the repositories page with an entry that has no id', () => {
  it('lists the entry as Unnamed repository, with the hint to enter an id', async () => {
    await mountList()

    expect(rowHeaders().some(text => text.startsWith('Unnamed repository')), 'the unnamed entry is listed').toBe(true)
    expect(unnamedRow()?.text(), 'the row says an id is missing').toContain('Enter an id.')
  })

  it('sorts the rows by name with the unnamed entry first, without losing a row', async () => {
    await mountList()

    await sortButton('Name').trigger('click')
    await flushPromises()

    expect(rowHeaders().map(text => text.slice(0, 'releases'.length)), 'ascending by name, the unnamed row first')
      .toEqual(['Unnamed', 'releases', 'snapshots'])
    expect(rowHeaders(), 'every row is still listed').toHaveLength(3)
  })

  it('filters the other repositories and keeps the unnamed entry out of an id match', async () => {
    await mountList()

    const input = wrapper.find('input[type="search"], input[type="text"]')
    input.element.value = 'snap'
    input.trigger('input')
    await flushPromises()

    expect(rowHeaders(), 'the filter keeps only the matching repository').toEqual(['snapshots'])
  })

  it('names the Edit and Delete actions of the unnamed row with the name Unnamed repository', async () => {
    await mountList()

    expect(buttonLabelled('Edit Unnamed repository').exists(), 'the edit action is named').toBe(true)
    expect(buttonLabelled('Delete Unnamed repository').exists(), 'the delete action is named').toBe(true)
  })

  it('opens the detail of the unnamed entry from its Edit action, at an address that keeps its place', async () => {
    const { router } = await mountList()

    await buttonLabelled('Edit Unnamed repository').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name, 'the Edit action opens the repository detail').toBe('settings-repository')
    expect(String(router.currentRoute.value.params.id).startsWith('~'), 'the unnamed entry is addressed by its key').toBe(true)
    expect(wrapper.find('h1').text(), 'the detail heading names the entry').toBe('Unnamed repository')
  })

  it('removes the unnamed entry from the draft after the typed name unnamed, and keeps the others', async () => {
    const { store } = await mountList()

    await buttonLabelled('Delete Unnamed repository').trigger('click')
    await flushPromises()
    const input = document.querySelector('#delete-entry-confirm-input')
    input.value = 'unnamed'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    deleteDialog().dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(store.configurations.value.maven.repositories.map(entry => entry.id), 'only the named repositories remain')
      .toEqual(['releases', 'snapshots'])
    expect(wrapper.find('[data-test="status-message"]').text(), 'the status says the entry is deleted and not saved yet')
      .toContain('Unnamed repository deleted, not saved yet')
  })
})
