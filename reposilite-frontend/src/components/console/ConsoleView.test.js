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
const login = vi.hoisted(() => ({ openLogin: vi.fn() }))
vi.mock('../../store/login', () => ({ openLogin: login.openLogin }))
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

  describe('empty output', () => {
    it('says the first line is awaited while nothing has been printed', async () => {
      await mountView()
      FakeEventSource.instances[0].onopen()
      await flushPromises()

      expect(wrapper.text(), 'an open console with no output yet should say it is waiting').toContain('Waiting for the first log line')
    })

    it('says no line matches the filter instead of waiting for a first line', async () => {
      await mountView()
      const stream = FakeEventSource.instances[0]
      stream.onopen()
      emitLine(stream, 'hello')
      await flushPromises()

      await wrapper.find('input').setValue('zzz')
      await flushPromises()

      expect(wrapper.text(), 'a filter that hides everything should say so').toContain('No lines match the filter')
      expect(wrapper.text(), 'lines have arrived, so the view must not claim to be waiting for one').not.toContain('Waiting for the first log line')
    })
  })

  describe('following the output', () => {
    // jsdom has no layout, so the panel's geometry is set by hand and its scroll position is
    // a plain value the view can read and write.
    const measurePanel = (panel, { top, height = 1000, view = 200 }) => {
      let position = top
      Object.defineProperty(panel, 'scrollTop', {
        configurable: true,
        get: () => position,
        set: (value) => { position = value }
      })
      Object.defineProperty(panel, 'scrollHeight', { configurable: true, value: height })
      Object.defineProperty(panel, 'clientHeight', { configurable: true, value: view })
      panel.dispatchEvent(new Event('scroll'))
    }

    it('keeps the reader in place when new output arrives while they have scrolled up', async () => {
      await mountView()
      const stream = FakeEventSource.instances[0]
      stream.onopen()
      await flushPromises()
      const panel = wrapper.find('#console').element
      measurePanel(panel, { top: 100 })

      emitLine(stream, 'hello')
      await flushPromises()

      expect(panel.scrollTop, 'a reader reading earlier output must not be pulled to the end').toBe(100)
    })

    it('follows new output while the reader is at the end of the panel', async () => {
      await mountView()
      const stream = FakeEventSource.instances[0]
      stream.onopen()
      await flushPromises()
      const panel = wrapper.find('#console').element
      measurePanel(panel, { top: 800 })

      emitLine(stream, 'hello')
      await flushPromises()

      expect(panel.scrollTop, 'a reader at the end should keep seeing the newest line').toBe(1000)
    })

    it('offers a jump to the latest output once the reader has scrolled away from the end', async () => {
      await mountView()
      const panel = wrapper.find('#console').element
      expect(buttonLabelled(wrapper, 'Jump to latest'), 'the jump should not be offered while at the end').toBeUndefined()

      measurePanel(panel, { top: 100 })
      await flushPromises()

      expect(buttonLabelled(wrapper, 'Jump to latest'), 'scrolling away from the end should offer the jump').toBeDefined()
    })

    it('does not announce every log line to assistive technology', async () => {
      await mountView()

      expect(wrapper.find('#console').attributes('aria-live'), 'the whole log must not be a live region').toBeUndefined()
    })

    it('announces the connection state through a status region', async () => {
      await mountView()
      FakeEventSource.instances[0].onopen()
      await flushPromises()

      expect(wrapper.find('[role="status"]').text(), 'a change in the connection should be announced').toContain('Connected')
    })
  })

  describe('expired session', () => {
    const unauthorized = () => Promise.reject(Object.assign(new Error('Unauthorized'), { response: { status: 401 } }))

    // The stream fails and the session check answers 401, which is the case the console
    // reports as an expired session.
    const expireSession = async () => {
      session.client = ref({ auth: { me: vi.fn(unauthorized) }, console: { execute: vi.fn() } })
      await mountView()
      FakeEventSource.instances[0].onerror(new Event('error'))
      await flushPromises()
    }

    it('offers a sign in button when the session has expired', async () => {
      await expireSession()

      expect(buttonLabelled(wrapper, 'Sign in'), 'an expired session should offer a sign in button').toBeDefined()
    })

    it('does not tell the reader to look for sign in in the menu', async () => {
      await expireSession()

      expect(wrapper.text(), 'the instruction is now a button, not a pointer to the menu').not.toContain('from the menu')
    })

    it('opens the sign in dialog when the sign in button is pressed', async () => {
      await expireSession()

      buttonLabelled(wrapper, 'Sign in').trigger('click')

      expect(login.openLogin, 'pressing sign in should ask the header for the sign in dialog').toHaveBeenCalledTimes(1)
    })

    it('offers no sign in button for an ordinary stream failure', async () => {
      session.client = ref({ auth: { me: vi.fn(() => Promise.reject(new Error('Network Error'))) }, console: { execute: vi.fn() } })
      await mountView()
      FakeEventSource.instances[0].onerror(new Event('error'))
      await flushPromises()

      expect(buttonLabelled(wrapper, 'Sign in'), 'a server that cannot be reached is not an expired session').toBeUndefined()
    })
  })
})
