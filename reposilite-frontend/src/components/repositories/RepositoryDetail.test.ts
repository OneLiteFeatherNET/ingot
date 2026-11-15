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

import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { configurationStoreKey } from '../../store/configuration'
import RepositoryDetail from './RepositoryDetail.vue'
import { loadedStore, repositorySchema } from './fixtures/repositories'

// The session answers as an instance administrator, so the access card asks the identity endpoint.
const holder = vi.hoisted(() => ({
  session: {
    client: { value: null },
    isManager: { value: true }
  }
}))
vi.mock('../../store/session', () => ({ useSession: () => holder.session }))
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

beforeEach(() => {
  holder.session.client = {
    value: {
      identity: {
        repositoryAccess: async () => ({ data: { visibility: 'PUBLIC', entries: [] } })
      }
    }
  }
})

// jsdom has no <dialog>; the one-of renderer of the schema uses it.
HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

const mountDetail = async ({ id = 'releases', schema, store, address } = {}) => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { name: 'settings-repositories', path: '/settings/repositories', component: { render: () => null } },
      { name: 'settings-repository', path: '/settings/repositories/:id', component: { render: () => null } }
    ]
  })
  await router.push(address ?? `/settings/repositories/${encodeURIComponent(id)}`)
  const configuration = store ?? await loadedStore({ schema })
  wrapper = mount(RepositoryDetail, {
    props: { id },
    global: {
      plugins: [router],
      provide: { [configurationStoreKey]: configuration }
    },
    attachTo: document.body
  })
  await flushPromises()
  return { wrapper, router, store: configuration }
}

// The entry of the draft, read the way the page reads it, so each test compares what was stored.
const entryOf = (store, id) => store.configurations.value.maven.repositories.find(entry => entry.id === id)
const radioNamed = (label) => document.querySelector(`input[type="radio"][value="${label}"]`)
const writePolicyRadios = () => Array.from(document.querySelectorAll('fieldset input[type="radio"]'))
const buttonNamed = (text) => Array.from(document.querySelectorAll('button')).find(button => button.textContent.trim() === text)

describe('the repository detail page', () => {
  it('does not repeat the id of the repository as a heading, the page heading names it', async () => {
    await mountDetail({ id: 'releases' })

    const headings = wrapper.findAll('h1, h2, h3, h4, h5, h6').map(heading => heading.text())
    expect(headings, 'no heading of the detail is the id').not.toContain('releases')
  })

  it('starts the headings of the detail at the second level, under the page heading', async () => {
    await mountDetail({ id: 'releases' })

    const levels = wrapper.findAll('h1, h2, h3, h4, h5, h6').map(heading => Number(heading.element.tagName.slice(1)))
    expect(levels.length, 'the detail has section headings').toBeGreaterThan(0)
    expect(Math.min(...levels), 'no level one heading in the detail').toBeGreaterThanOrEqual(2)
    expect(levels[0], 'the first heading is the second level').toBe(2)
  })

  it('draws no back button of its own, the page frame carries the way back', async () => {
    await mountDetail({ id: 'releases' })

    const backs = Array.from(document.querySelectorAll('button, a')).map(control => control.textContent.trim()).filter(text => text.startsWith('Back to'))
    expect(backs, 'no Back to control in the page content').toEqual([])
  })

  it('keeps the type switch and the access card', async () => {
    await mountDetail({ id: 'releases' })

    expect(document.querySelector('#entry-detail-type'), 'the type switch is shown').not.toBeNull()
    expect(document.querySelector('[data-test="repository-access"]'), 'the access card is shown to an administrator').not.toBeNull()
  })

  it('offers the Write policy radio group on a hosted repository', async () => {
    await mountDetail({ id: 'releases' })

    const choices = writePolicyRadios().map(radio => radio.value)
    expect(choices, 'the three choices of the write policy').toEqual(['allow', 'allowOnce', 'readOnly'])
  })

  it('offers no Write policy on a proxy', async () => {
    await mountDetail({ id: 'central' })

    expect(writePolicyRadios().length, 'a proxy has no write policy').toBe(0)
  })

  it('offers only Allow and Allow once when the schema has no readOnly field', async () => {
    await mountDetail({ id: 'releases', schema: repositorySchema({ redeployment: true }) })

    expect(writePolicyRadios().map(radio => radio.value), 'Read only needs the readOnly field').toEqual(['allow', 'allowOnce'])
  })

  it('explains each write policy choice in text', async () => {
    await mountDetail({ id: 'releases' })

    const text = wrapper.text()
    expect(text, 'Allow is explained').toContain('Deploys are accepted.')
    expect(text, 'Allow once is explained, with its status code').toContain('refused with 409.')
    expect(text, 'Read only is explained, with its status codes').toContain('refused with 405 Method Not Allowed.')
  })

  it('offers the Version policy field only when the schema has it', async () => {
    await mountDetail({ id: 'releases' })
    expect(wrapper.text(), 'the field is drawn from the schema').toContain('Version policy')

    wrapper.unmount()
    await mountDetail({ id: 'releases', schema: repositorySchema({ redeployment: true, readOnly: true }) })
    expect(wrapper.text(), 'no field without the schema field').not.toContain('Version policy')
  })

  it('stores Allow once as redeployment false and adds no readOnly key', async () => {
    const { store } = await mountDetail({ id: 'snapshots' })

    radioNamed('allowOnce').click()
    await flushPromises()

    const entry = entryOf(store, 'snapshots')
    expect(entry.redeployment, 'redeployment is switched off').toBe(false)
    expect(Object.keys(entry), 'no readOnly key is added').not.toContain('readOnly')
  })

  it('keeps redeployment through Read only, and Allow once takes the repository out of read only', async () => {
    const { store } = await mountDetail({ id: 'snapshots' })

    radioNamed('readOnly').click()
    await flushPromises()
    expect(entryOf(store, 'snapshots').readOnly, 'read only is stored').toBe(true)
    expect(entryOf(store, 'snapshots').redeployment, 'redeployment is kept as it was').toBe(true)

    radioNamed('allowOnce').click()
    await flushPromises()
    expect(entryOf(store, 'snapshots').readOnly, 'read only is no longer set').not.toBe(true)
  })

  it('writes only the type key when a derived type is declared', async () => {
    const { store } = await mountDetail({ id: 'central' })
    const before = entryOf(store, 'central')

    buttonNamed('Declare type').click()
    await flushPromises()

    const after = entryOf(store, 'central')
    const changed = Object.keys({ ...before, ...after }).filter(key => JSON.stringify(before[key]) !== JSON.stringify(after[key]))
    expect(changed, 'only the type key changes').toEqual(['type'])
    expect(after.type, 'the derived type is written').toBe('PROXY')
  })

  it('asks first when declaring the type would drop empty mirrored entries', async () => {
    const store = await loadedStore()
    store.setEntry('maven', 'repositories', 2, {
      id: 'central',
      visibility: 'PUBLIC',
      proxied: [{ reference: 'https://repo.example.org/maven' }, { reference: '/' }]
    })
    await mountDetail({ id: 'central', store })

    buttonNamed('Declare type').click()
    await flushPromises()

    expect(document.querySelector('[data-test="declare-type-dialog"]'), 'the dialog opens').not.toBeNull()
    expect(document.body.textContent, 'the number of entries is named').toContain('1 empty entry will be removed.')
    expect(entryOf(store, 'central').type, 'nothing changes before the confirmation').toBeUndefined()

    buttonNamed('Declare Proxy').click()
    await flushPromises()
    expect(entryOf(store, 'central').type, 'the type is written after the confirmation').toBe('PROXY')
    expect(entryOf(store, 'central').proxied.length, 'the empty entry is gone').toBe(1)
  })

  it('shows the Mixed warning and offers no declare action', async () => {
    await mountDetail({ id: 'mixed' })

    expect(wrapper.text(), 'the warning is text').toContain('References local and remote repositories. Split it into a group and a proxy.')
    expect(Array.from(document.querySelectorAll('button')).map(button => button.textContent.trim()), 'no declare action')
      .not.toContain('Declare type')
  })

  it('shows a not found pane for an id that does not exist, with no link of its own back to the list', async () => {
    await mountDetail({ id: 'nowhere' })

    expect(wrapper.find('h2').text(), 'the pane says it is not found').toBe('Repository not found')
    expect(wrapper.find('a[href="/settings/repositories"]').exists(), 'the page frame carries the way back').toBe(false)
  })
})
