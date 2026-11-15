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

const routesOf = () => [
  { name: 'settings-repositories', path: '/settings/repositories', component: RepositoriesView },
  { name: 'settings-repository', path: '/settings/repositories/:id', component: RepositoryDetail, props: true }
]

// The list is mounted under a router that owns the query and the address, as the app does.
const mountList = async ({ address = '/settings/repositories', store } = {}) => {
  const router = createRouter({ history: createMemoryHistory(), routes: routesOf() })
  await router.push(address)
  const configuration = store ?? await loadedStore({ schema: repositorySchema({ versionPolicy: true, readOnly: true }) })
  wrapper = mount({
    render: () => h(RouterView)
  }, {
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: configuration }
    },
    attachTo: document.body
  })
  await flushPromises()
  return { wrapper, router, store: configuration }
}

const buttonLabelled = (label) => wrapper.find(`button[aria-label="${label}"]`)
// The Actions menu of a row is teleported to the body: its items are read from the document.
const openActionsOf = async (name) => {
  buttonLabelled(`Actions for ${name}`).trigger('click')
  await flushPromises()
}
const menuItemOf = (label) => document.querySelector(`[role="menu"] [aria-label="${label}"]`)
const wizardDialogs = () => document.querySelectorAll('#repository-wizard')
const deleteDialog = () => document.querySelector('#delete-entry-form')
const headingText = () => wrapper.find('h2').text()

describe('creating a repository from the page', () => {
  it('opens the existing wizard once from New repository', async () => {
    await mountList()

    const button = wrapper.findAll('button').find(control => control.text() === 'New repository')
    expect(button, 'the page offers New repository').toBeDefined()
    await button.trigger('click')
    await flushPromises()

    expect(wizardDialogs().length, 'one wizard is open').toBe(1)
  })

  it('opens the wizard once for ?new=repository and removes the query from the address', async () => {
    const { router } = await mountList({ address: '/settings/repositories?new=repository' })

    expect(wizardDialogs().length, 'the wizard opens for the link').toBe(1)
    expect(router.currentRoute.value.query.new, 'the query is consumed once the wizard opens').toBeUndefined()
  })

  it('moves the focus to the first heading of the new repository once it is open', async () => {
    const { router, store } = await mountList()
    store.addEntry('maven', 'repositories', { id: 'fresh', visibility: 'PUBLIC' })

    await router.push('/settings/repositories/fresh')
    await flushPromises()

    expect(document.activeElement?.tagName, 'the focus is on a heading').toBe('H2')
    expect(document.activeElement?.textContent.trim(), 'the focused heading is the first section of the detail').toBe('Repository type')
  })
})

describe('deleting a repository from the page', () => {
  it('asks for the typed name, lists the groups that name the repository, and starts on Cancel', async () => {
    await mountList()

    await openActionsOf('releases')
    menuItemOf('Delete releases').click()
    await flushPromises()

    expect(deleteDialog(), 'the delete dialog opens').not.toBeNull()
    const groups = Array.from(deleteDialog().querySelectorAll('li')).map(item => item.textContent.trim())
    expect(groups, 'the group that lists the repository is named before the confirm button').toEqual(['all'])
    expect(document.querySelector('#delete-entry-confirm').disabled, 'the confirm button waits for the typed name').toBe(true)
    expect(document.activeElement?.id, 'the initial focus is on Cancel').toBe('delete-entry-cancel')
  })

  it('removes the row after the typed name, moves focus to the list heading and says it is not saved yet', async () => {
    const { store } = await mountList()
    await openActionsOf('releases')
    menuItemOf('Delete releases').click()
    await flushPromises()

    const input = document.querySelector('#delete-entry-confirm-input')
    input.value = 'releases'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    deleteDialog().dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(wrapper.findAll('tbody th[scope="row"]').map(cell => cell.text()), 'the row is removed').not.toContain('releases')
    expect(store.hasUnsavedChanges.value, 'the removal is a draft change').toBe(true)
    expect(wrapper.find('[data-test="status-message"]').text(), 'the status says it is not saved yet').toContain('releases deleted, not saved yet')
    expect(document.activeElement?.tagName, 'focus is on a heading').toBe('H2')
    expect(headingText(), 'the focus goes to the list heading').toBe('Repositories')
  })

  it('closes on Escape and returns the focus to the Actions button that opened the delete', async () => {
    await mountList()
    const opener = buttonLabelled('Actions for snapshots')
    await openActionsOf('snapshots')
    menuItemOf('Delete snapshots').click()
    await flushPromises()
    expect(deleteDialog(), 'the dialog is open').not.toBeNull()

    document.activeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(deleteDialog(), 'Escape closes the dialog').toBeNull()
    expect(document.activeElement, 'the focus returns to the opener').toBe(opener.element)
  })
})
