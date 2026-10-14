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
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import FileBrowserView from './FileBrowserView.vue'
import { sessionState } from '../../store/session'

// The session store is replaced per file; each test swaps in its own client below.
vi.mock('../../store/session', async () => {
  const { ref } = await import('vue')
  const state = {
    details: ref({ routes: [] }),
    client: ref(null)
  }
  return {
    sessionState: state,
    useSession: () => ({
      details: state.details,
      client: state.client,
      hasPermissionTo: () => false,
      token: ref({ name: '', secret: '' })
    })
  }
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/releases/net/example' }),
  useRouter: () => ({ push: () => {} })
}))

vi.mock('../../store/qualifier', () => ({
  default: () => ({
    qualifier: { path: 'releases/net/example', watchable: 0 },
    getParentPath: () => '/releases/net',
    refreshQualifier: () => {},
    redirectTo: () => {}
  })
}))

vi.mock('../card/SnippetsCard.vue', () => ({
  default: { name: 'SnippetsCard', render: () => null }
}))

const networkError = () => ({ message: 'Network Error', request: {} })
const httpError = (status, message = '') => ({ response: { status, data: { message } } })

const clientWithDetails = (details) => ({
  maven: {
    details,
    download: () => Promise.reject(new Error('not used')),
    delete: () => Promise.reject(new Error('not used'))
  }
})

// Each test unmounts what it mounted, so a later change to the shared session cannot reach
// a view that a previous test left behind.
const mounted = []

const mountView = () => {
  const wrapper = mount(FileBrowserView, {
    props: { qualifier: { path: 'releases/net/example', watchable: 0 } },
    global: { stubs: { 'router-link': RouterLinkStub } }
  })
  mounted.push(wrapper)
  return wrapper
}

const buttonWithText = (wrapper, text) =>
  wrapper.findAll('button').find(button => button.text() === text)

describe('FileBrowserView listing errors', () => {
  beforeEach(() => {
    sessionState.details.value = { routes: [] }
    sessionState.client.value = null
  })

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount())
  })

  it('shows the cannot-reach state with a retry when the server does not answer', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.reject(networkError()))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text(), 'a network failure must not leave the skeleton on screen').toContain('Cannot reach the repository')
    expect(buttonWithText(wrapper, 'Retry'), 'the cannot-reach state offers a retry').toBeDefined()
  })

  it('loads the listing again when retry is pressed', async () => {
    const details = vi.fn()
      .mockRejectedValueOnce(networkError())
      .mockResolvedValueOnce({ data: { files: [{ name: 'example-1.0.jar', type: 'FILE' }] } })
    sessionState.client.value = clientWithDetails(details)

    const wrapper = mountView()
    await flushPromises()
    await buttonWithText(wrapper, 'Retry').trigger('click')
    await flushPromises()

    expect(details, 'retry must request the directory a second time').toHaveBeenCalledTimes(2)
    expect(wrapper.text(), 'the listing appears once the retry succeeds').toContain('example-1.0.jar')
  })

  it('reports a missing directory as not found, not as unreachable', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.reject(httpError(404, 'Not found')))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text(), 'a 404 names the missing directory').toContain('Directory not found')
    expect(wrapper.text(), 'a 404 is not a connectivity problem').not.toContain('Cannot reach the repository')
  })

  it('reports a forbidden directory as an access problem', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.reject(httpError(403, 'Forbidden')))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text(), 'a 403 explains the missing access').toContain('do not have access to this directory')
    expect(wrapper.text(), 'a 403 is not reported as a missing directory').not.toContain('Directory not found')
  })

  it('reports other server failures with the server message', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.reject(httpError(500, 'Storage is offline')))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text(), 'a 500 gets its own heading').toContain('Cannot load this directory')
    expect(wrapper.text(), 'the server message is shown').toContain('Storage is offline')
    expect(wrapper.text(), 'a 500 is not reported as a missing directory').not.toContain('Directory not found')
  })

  it('does not crash on a failure without a response object', async () => {
    sessionState.client.value = clientWithDetails(() => Promise.reject(new Error('boom')))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text(), 'a bare error still ends the loading state').toContain('Cannot load this directory')
  })
})
