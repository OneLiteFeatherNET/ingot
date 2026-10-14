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
import { ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

// The view reads the session for its token and client, and streams through EventSource. The
// stream is a fake the test drives event by event: nothing here opens a socket or waits.
const session = vi.hoisted(() => ({ client: null, token: null }))
vi.mock('../../store/session', () => ({ useSession: () => session }))
vi.mock('../../helpers/toast', () => ({
  createErrorToast: vi.fn(),
  errorMessage: (error) => error.message
}))

const { FakeEventSource } = vi.hoisted(() => {
  class FakeEventSource {
    constructor(url, options) {
      this.url = url
      this.options = options
      this.listeners = new Map()
      FakeEventSource.instances.push(this)
    }

    addEventListener(type, handler) {
      this.listeners.set(type, handler)
    }

    close() {
      this.closed = true
    }
  }
  FakeEventSource.instances = []
  return { FakeEventSource }
})
vi.mock('extended-eventsource', () => ({ EventSource: FakeEventSource }))

const emitLine = (stream, text) => stream.listeners.get('log')({ data: text })

const buttonLabelled = (wrapper, label) =>
  wrapper.findAll('button').find(button => button.text() === label)

describe('ConsoleView', () => {
  let wrapper

  beforeEach(() => {
    // The log is module state that outlives a mount, so each test loads a fresh copy of the
    // view and its stores. Otherwise the lines of one test would show up in the next.
    vi.resetModules()
    FakeEventSource.instances.length = 0
    session.token = ref({ name: 'ops', secret: 'secret' })
    session.client = ref({ auth: { me: vi.fn(() => Promise.resolve()) }, console: { execute: vi.fn() } })
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.restoreAllMocks()
  })

  const mountView = async () => {
    const { default: ConsoleView } = await import('./ConsoleView.vue')
    wrapper = mount(ConsoleView, { attachTo: document.body })
    return wrapper
  }

  it('keeps the lines printed before a reconnect and marks where the connection came back', async () => {
    await mountView()
    const first = FakeEventSource.instances[0]
    first.onopen()
    emitLine(first, 'hello')
    await flushPromises()
    first.onerror(new Event('error'))
    await flushPromises()

    buttonLabelled(wrapper, 'Reconnect').trigger('click')
    await flushPromises()
    FakeEventSource.instances[1].onopen()
    await flushPromises()

    const output = wrapper.find('#console').text()
    expect(output, 'lines printed before the drop must stay visible after reconnecting').toContain('hello')
    expect(output, 'the gap in the stream should be marked in the log').toContain('Reconnected')
  })
})
