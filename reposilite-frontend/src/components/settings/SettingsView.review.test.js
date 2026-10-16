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
import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createConfigurationStore, configurationStoreKey } from '../../store/configuration'
import SettingsView from './SettingsView.vue'

vi.mock('downloadjs', () => ({ default: vi.fn() }))
vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const SCHEMA = {
  type: 'object',
  title: 'Maven',
  properties: {
    repositories: {
      title: 'Repositories',
      type: 'array',
      items: {
        type: 'object',
        title: 'Repository',
        properties: {
          id: { type: 'string', title: 'Id' },
          visibility: { type: 'string', title: 'Visibility' },
          redeployment: { type: 'boolean', title: 'Redeployment' }
        },
        additionalProperties: false
      }
    }
  }
}

const initialServer = () => ({
  maven: {
    repositories: [
      { id: 'releases', visibility: 'PUBLIC', redeployment: false },
      { id: 'snapshots', visibility: 'PRIVATE', redeployment: true }
    ]
  }
})

let server
let writes

const fakeClient = () => ({
  settings: {
    domains: async () => ({ data: Object.keys(server) }),
    schema: async () => ({ data: SCHEMA }),
    fetch: async (domain) => ({ data: JSON.parse(JSON.stringify(server[domain])) }),
    update: async (domain, content) => {
      writes.push({ domain, content: JSON.parse(JSON.stringify(content)) })
      server[domain] = JSON.parse(JSON.stringify(content))
      return { data: {} }
    }
  }
})

const namedRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: [
    { name: 'settings', path: '/settings/:domain?', component: { render: () => null } },
    { name: 'settings-entry', path: '/settings/:domain/:collection/:entry', component: { render: () => null } }
  ]
})

let wrapper

const mountView = async () => {
  server = initialServer()
  writes = []
  const store = createConfigurationStore(ref(fakeClient()), { secretsHidden: () => false })
  expect(await store.fetchConfiguration(), 'the fixture configuration should load').toBe(true)
  wrapper = mount(SettingsView, {
    props: { selectedTab: 'Settings' },
    global: {
      plugins: [namedRouter()],
      provide: { [configurationStoreKey]: store }
    },
    attachTo: document.body
  })
  await flushPromises()
  return store
}

const countButton = () => wrapper.findAll('button').find(button => button.text().includes('unsaved change'))
const sheet = () => document.querySelector('[role="dialog"][aria-labelledby]')
const sheetButton = (name) => [...sheet().querySelectorAll('button')].find(button => button.textContent.trim() === name)

const editBothRepositories = (store) => {
  store.setEntry('maven', 'repositories', 0, { id: 'releases', visibility: 'PRIVATE', redeployment: false })
  store.setEntry('maven', 'repositories', 1, { id: 'snapshots', visibility: 'PUBLIC', redeployment: true })
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})

describe('SettingsView review', () => {
  it('opens the review of every change when the unsaved count is pressed', async () => {
    const store = await mountView()
    editBothRepositories(store)
    await flushPromises()

    await countButton().trigger('click')
    await flushPromises()

    expect(sheet(), 'the review sheet is open').not.toBeNull()
    expect(sheet().textContent).toContain('Repositories: releases')
    expect(sheet().textContent).toContain('Repositories: snapshots')
  })

  it('reverts one change in the draft and keeps the other edits', async () => {
    const store = await mountView()
    editBothRepositories(store)
    await flushPromises()
    await countButton().trigger('click')
    await flushPromises()

    sheet().querySelector('[aria-label="Revert Visibility"]').click()
    await flushPromises()

    const repositories = store.configurations.value.maven.repositories
    expect(repositories.find(entry => entry.id === 'releases').visibility, 'the reverted field is back to its saved value').toBe('PUBLIC')
    expect(repositories.find(entry => entry.id === 'snapshots').visibility, 'the other edits remain in the draft').toBe('PUBLIC')
    expect(writes, 'the revert writes nothing to the server').toEqual([])
  })

  it('closes the review once the last change is reverted', async () => {
    const store = await mountView()
    store.setEntry('maven', 'repositories', 0, { id: 'releases', visibility: 'PRIVATE', redeployment: false })
    await flushPromises()
    await countButton().trigger('click')
    await flushPromises()

    sheet().querySelector('[aria-label="Revert Visibility"]').click()
    await flushPromises()

    expect(sheet(), 'nothing is left to review, so the sheet is closed').toBeNull()
  })

  it('discards all changes from the review and closes it', async () => {
    const store = await mountView()
    editBothRepositories(store)
    await flushPromises()
    await countButton().trigger('click')
    await flushPromises()

    sheetButton('Discard all').click()
    await flushPromises()

    expect(store.hasUnsavedChanges.value).toBe(false)
    expect(sheet()).toBeNull()
  })

  it('saves from the review and closes it once the save went through', async () => {
    const store = await mountView()
    editBothRepositories(store)
    await flushPromises()
    await countButton().trigger('click')
    await flushPromises()

    sheetButton('Save').click()
    await flushPromises()

    expect(writes).toHaveLength(1)
    expect(writes[0].content.repositories[0].visibility).toBe('PRIVATE')
    expect(sheet()).toBeNull()
  })
})
