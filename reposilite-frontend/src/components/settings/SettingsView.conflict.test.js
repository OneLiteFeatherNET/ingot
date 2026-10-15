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

// No timers and no network: the fake server answers from memory and enforces If-Match the way the backend does.
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

const initialState = () => ({
  maven: {
    repositories: [
      { id: 'releases', visibility: 'PUBLIC', redeployment: false },
      { id: 'snapshots', visibility: 'PRIVATE', redeployment: true }
    ]
  }
})

const preconditionFailed = () =>
  Object.assign(new Error('Precondition Failed'), { response: { status: 412, data: {} } })

/* One state per domain, with a tag that moves on every write. A write with a stale tag is refused and changes nothing. */
const fakeServer = () => {
  const state = initialState()
  let version = 1
  const tagOf = () => `"maven-${version}"`

  const update = vi.fn(async (domain, content, etag) => {
    if (etag !== undefined && etag !== tagOf()) throw preconditionFailed()
    state[domain] = JSON.parse(JSON.stringify(content))
    version += 1
    return { data: {} }
  })

  return {
    state,
    update,
    /* Another admin saves the domain. */
    changeElsewhere: (domain, content) => {
      state[domain] = JSON.parse(JSON.stringify(content))
      version += 1
    },
    client: {
      settings: {
        domains: async () => ({ data: Object.keys(state) }),
        schema: async () => ({ data: SCHEMA }),
        fetch: async (domain) => ({ data: JSON.parse(JSON.stringify(state[domain])), headers: { etag: tagOf() } }),
        update
      }
    }
  }
}

const namedRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: [
    { name: 'settings', path: '/settings/:domain?', component: { render: () => null } },
    { name: 'settings-entry', path: '/settings/:domain/:collection/:entry', component: { render: () => null } }
  ]
})

let wrapper

const mountView = async () => {
  const server = fakeServer()
  const store = createConfigurationStore(ref(server.client), { secretsHidden: () => false })
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
  return { server, store }
}

// The dialog and the sheet are teleported to the body, so they are found there rather than inside the wrapper.
const conflictDialog = () => document.querySelector('[role="alertdialog"]')
const reviewSheet = () => document.querySelector('[role="dialog"][aria-labelledby="settings-review-title"]')
const buttonIn = (root, label) => root && [...root.querySelectorAll('button')].find(button => button.textContent.trim() === label)
const press = async (root, label) => {
  const button = buttonIn(root, label)
  if (!button) throw new Error(`no button labelled "${label}"`)
  button.click()
  await flushPromises()
}

/* The draft changes releases to PRIVATE; the server meanwhile has snapshots PUBLIC, so the save below is refused. */
const conflictingEdit = async ({ server, store }) => {
  store.setEntry('maven', 'repositories', 0, { id: 'releases', visibility: 'PRIVATE', redeployment: false })
  await flushPromises()
  server.changeElsewhere('maven', {
    repositories: [
      { id: 'releases', visibility: 'PUBLIC', redeployment: false },
      { id: 'snapshots', visibility: 'PUBLIC', redeployment: true }
    ]
  })
  await press(wrapper.element, 'Save')
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('SettingsView conflict dialog', () => {
  it('shows the dialog when a save is refused because the settings changed elsewhere', async () => {
    const context = await mountView()

    await conflictingEdit(context)

    expect(conflictDialog(), 'the conflict dialog should be open').not.toBeNull()
    expect(conflictDialog().textContent).toContain('Settings changed elsewhere')
    expect(conflictDialog().textContent).toContain('Maven')
    expect(context.server.update).toHaveBeenLastCalledWith('maven', expect.any(Object), '"maven-1"')
    expect(context.store.conflict.value).toEqual(expect.objectContaining({ kind: 'settings', name: 'maven', reviewed: false }))
  })

  it('opens the review sheet over the stored settings when Review is pressed, and hides the dialog meanwhile', async () => {
    const context = await mountView()
    await conflictingEdit(context)

    await press(conflictDialog(), 'Review')

    expect(conflictDialog(), 'the dialog steps aside while the review is open').toBeNull()
    expect(reviewSheet().textContent).toContain('Stored settings compared')
    expect(reviewSheet().textContent).toContain('releases')
    expect(context.store.conflictChanges.value.length, 'the review lists the server state against the draft').toBeGreaterThan(0)
    expect(context.store.isDirty('maven'), 'reviewing changes nothing').toBe(true)
    expect(context.server.update, 'reviewing writes nothing').toHaveBeenCalledTimes(1)
  })

  it('returns to the dialog, with the number of changes, when the review is closed', async () => {
    const context = await mountView()
    await conflictingEdit(context)
    await press(conflictDialog(), 'Review')

    await press(reviewSheet(), 'Close')

    expect(reviewSheet()).toBeNull()
    expect(conflictDialog().textContent).toMatch(/\d+ changes? differ/)
  })

  it('takes the stored value of one change into the draft from the review, and the list shrinks', async () => {
    const context = await mountView()
    await conflictingEdit(context)
    await press(conflictDialog(), 'Review')
    const before = context.store.conflictChanges.value.length

    await press(reviewSheet(), 'Use stored')

    expect(context.store.configurations.value.maven.repositories[0].visibility).toBe('PUBLIC')
    expect(context.store.conflictChanges.value.length).toBeLessThan(before)
    expect(context.server.update, 'taking a stored value writes nothing').toHaveBeenCalledTimes(1)
  })

  it('offers no save in the conflict review, because overwriting has its own confirmed path', async () => {
    const context = await mountView()
    await conflictingEdit(context)
    await press(conflictDialog(), 'Review')

    expect(buttonIn(reviewSheet(), 'Save')).toBeUndefined()
    expect(buttonIn(reviewSheet(), 'Take stored settings')).toBeDefined()
  })

  it('takes the stored settings and closes both when the review asks for them', async () => {
    const context = await mountView()
    await conflictingEdit(context)
    await press(conflictDialog(), 'Review')

    await press(reviewSheet(), 'Take stored settings')

    expect(context.store.conflict.value).toBeNull()
    expect(reviewSheet()).toBeNull()
    expect(conflictDialog()).toBeNull()
    expect(context.store.configurations.value.maven.repositories[0].visibility).toBe('PUBLIC')
    expect(context.store.isDirty('maven')).toBe(false)
  })

  it('reloads the stored settings and discards the draft when the user chooses reload', async () => {
    const context = await mountView()
    await conflictingEdit(context)

    await press(conflictDialog(), 'Reload and discard my changes')

    expect(context.store.conflict.value).toBeNull()
    expect(conflictDialog()).toBeNull()
    expect(context.store.configurations.value.maven.repositories[0].visibility).toBe('PUBLIC')
    expect(context.store.isDirty('maven')).toBe(false)
    expect(context.server.update, 'reload writes nothing').toHaveBeenCalledTimes(1)
  })

  it('overwrites only after a second press, and then writes the draft without a precondition', async () => {
    const context = await mountView()
    await conflictingEdit(context)

    await press(conflictDialog(), 'Overwrite')
    expect(context.server.update, 'the first press only asks for confirmation').toHaveBeenCalledTimes(1)

    await press(conflictDialog(), 'Confirm overwrite')

    expect(context.server.update).toHaveBeenLastCalledWith('maven', expect.any(Object))
    expect(context.server.state.maven.repositories[0].visibility).toBe('PRIVATE')
    expect(context.store.conflict.value).toBeNull()
    expect(conflictDialog()).toBeNull()
  })

  it('keeps the draft pending when dismissed, so the next save is refused again with the same tag', async () => {
    const context = await mountView()
    await conflictingEdit(context)

    await press(conflictDialog(), 'Keep my draft')
    expect(conflictDialog()).toBeNull()
    expect(context.store.isDirty('maven')).toBe(true)

    await press(wrapper.element, 'Save')

    expect(context.server.update).toHaveBeenLastCalledWith('maven', expect.any(Object), '"maven-1"')
    expect(conflictDialog()).not.toBeNull()
  })
})
