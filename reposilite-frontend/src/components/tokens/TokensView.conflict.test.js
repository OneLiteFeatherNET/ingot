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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
// Imported once, outside the tests: loading the view inside a test body is slow enough to hit the test timeout under load.
import TokensView from './TokensView.vue'
import { useTokens } from '../../store/tokens'

// The client is swapped per test through this holder. The store reads it lazily.
const session = vi.hoisted(() => ({ client: null }))

vi.mock('../../store/session', () => ({
  useSession: () => ({ client: { get value() { return session.client } } })
}))

vi.mock('../../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  createWarningToast: vi.fn(),
  createInfoToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const preconditionFailed = () =>
  Object.assign(new Error('Precondition Failed'), { response: { status: 412, data: {} } })

/*
 * One stored token with its tag. An update carrying a tag that no longer describes the stored token is refused
 * with 412 and changes nothing, as the backend does.
 */
const fakeTokenServer = () => {
  const stored = {
    name: 'ci',
    identifier: { type: 'PERSISTENT', value: 1 },
    createdAt: [2026, 1, 1],
    expiresAt: null,
    description: 'first',
    permissions: [],
    routes: []
  }
  let version = 1
  const tagOf = () => `"ci-${version}"`

  const update = vi.fn(async (name, request, etag) => {
    if (etag !== undefined && etag !== tagOf()) throw preconditionFailed()
    stored.description = request.description
    version += 1
    return {}
  })

  return {
    stored,
    update,
    /* Another admin edits the token. */
    changeElsewhere: (description) => {
      stored.description = description
      version += 1
    },
    client: {
      tokens: {
        list: vi.fn(async () => ({ data: [{ ...stored }] })),
        get: vi.fn(async () => ({ data: { ...stored }, headers: { etag: tagOf() } })),
        update,
        createOrUpdate: vi.fn(async () => ({ data: {} })),
        delete: vi.fn(async () => ({})),
        regenerateSecret: vi.fn(async () => ({ data: 'unused' }))
      }
    }
  }
}

// The dialog is teleported to the body, so its buttons are found there rather than inside the wrapper.
const dialog = () => document.querySelector('[role="alertdialog"]')
const dialogButton = (label) => [...dialog().querySelectorAll('button')].find(button => button.textContent.trim() === label)
const pressDialogButton = async (label) => {
  const button = dialogButton(label)
  if (!button) throw new Error(`no dialog button labelled "${label}"`)
  button.click()
  await flushPromises()
}

describe('TokensView conflict dialog', () => {
  let wrapper
  let server
  let store

  /*
   * The token store is a shared singleton, so each test starts it from a fresh server and clears the state the
   * previous test left in it: the conflict, and the list, which the load below refills with this server's token.
   */
  const mountWithConflict = async () => {
    server = fakeTokenServer()
    session.client = server.client
    store = useTokens()
    store.conflict.value = null
    await store.fetchTokens()
    wrapper = mount(TokensView, { props: { selectedTab: 'Tokens' }, attachTo: document.body })
    await flushPromises()

    // The draft is saved against the tag read at load; the server changes first, so the save is refused.
    server.changeElsewhere('changed elsewhere')
    await store.saveTokenMeta(store.tokens.value[0], { description: 'mine', manager: false, expiresAt: null })
    await flushPromises()
  }

  beforeEach(() => {
    session.client = null
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('shows the dialog when a save is refused because the token changed elsewhere', async () => {
    await mountWithConflict()

    expect(dialog(), 'the conflict dialog should be open').not.toBeNull()
    expect(dialog().textContent).toContain('Token changed elsewhere')
    expect(dialog().textContent).toContain('ci')
    expect(server.update).toHaveBeenCalledTimes(1)
    expect(server.stored.description, 'a refused save writes nothing').toBe('changed elsewhere')
  })

  it('offers no review for a token, only reload and overwrite', async () => {
    await mountWithConflict()

    expect(dialogButton('Review')).toBeUndefined()
    expect(dialogButton('Reload and discard my changes')).toBeDefined()
    expect(dialogButton('Overwrite')).toBeDefined()
  })

  it('reloads the stored token and drops the edit when the user chooses reload', async () => {
    await mountWithConflict()

    await pressDialogButton('Reload and discard my changes')

    expect(store.conflict.value, 'the conflict should be cleared').toBeNull()
    expect(document.querySelector('[role="alertdialog"]'), 'the dialog should be closed').toBeNull()
    expect(store.tokens.value[0].description, 'the list shows the stored token').toBe('changed elsewhere')
    expect(server.update, 'reload writes nothing').toHaveBeenCalledTimes(1)
  })

  it('overwrites only after the second press, and then writes the edit without a precondition', async () => {
    await mountWithConflict()

    await pressDialogButton('Overwrite')
    expect(server.update, 'the first press only asks for confirmation').toHaveBeenCalledTimes(1)
    expect(store.conflict.value).not.toBeNull()

    await pressDialogButton('Confirm overwrite')

    expect(server.update).toHaveBeenLastCalledWith('ci', expect.objectContaining({ description: 'mine' }))
    expect(server.stored.description).toBe('mine')
    expect(store.conflict.value).toBeNull()
  })

  it('keeps the edit and writes nothing when the user keeps the draft', async () => {
    await mountWithConflict()

    await pressDialogButton('Keep my draft')

    expect(store.conflict.value).toBeNull()
    expect(server.update).toHaveBeenCalledTimes(1)
    expect(server.stored.description).toBe('changed elsewhere')
  })
})
