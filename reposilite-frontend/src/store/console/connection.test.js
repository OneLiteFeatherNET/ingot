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
import { flushPromises } from '@vue/test-utils'

// The store reads the session for its client and token at import time, and it talks to the
// stream through EventSource. Both are replaced so the test runs without a server, a token
// or a browser stream.
const session = vi.hoisted(() => ({ client: null, token: null, authMode: null }))
vi.mock('../session.js', () => ({ useSession: () => session }))
vi.mock('../client', () => ({ createURL: (path) => `http://localhost${path}` }))
vi.mock('../../helpers/toast', () => ({
  createErrorToast: vi.fn(),
  errorMessage: (error) => error.message
}))

// A stream that never leaves the test: the test reads what the store attached to it and
// fires the events itself, so nothing waits on a socket.
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

describe('useConsole', () => {
  beforeEach(() => {
    // Every test imports the store afresh, so the command history and the other module-scope
    // state of one test cannot leak into the next one, whatever order they run in.
    vi.resetModules()
    FakeEventSource.instances.length = 0
    session.authMode = null
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('sending a command', () => {
    it('restores the typed command when sending it fails', async () => {
      session.client = ref({
        console: { execute: vi.fn(() => Promise.reject(new Error('connection refused'))) }
      })
      const { default: useConsole } = await import('./connection')
      const { command, execute } = useConsole()
      command.value = 'say hello'

      await execute()

      expect(command.value, 'a command that was not sent must stay in the input so it can be sent again').toBe('say hello')
    })
  })

  describe('command history', () => {
    it('keeps the commands sent before the console was opened again', async () => {
      session.client = ref({ console: { execute: vi.fn(() => Promise.resolve()) } })
      const { default: useConsole } = await import('./connection')
      const beforeRemount = useConsole()
      beforeRemount.command.value = 'list'
      await beforeRemount.execute()

      // Opening the view again creates a second call to the composable, as a remount does.
      const afterRemount = useConsole()
      afterRemount.previousCommand()

      expect(afterRemount.command.value, 'the arrow key should still recall the command sent before the remount').toBe('list')
    })
  })

  describe('authenticating the stream', () => {
    const open = async (mode, token) => {
      session.client = ref({ console: { execute: vi.fn() } })
      session.authMode = ref(mode)
      const { default: useConsole } = await import('./connection')
      useConsole().connect(token)
      return FakeEventSource.instances[0]
    }

    it('sends the session cookie and no token header in cookie mode', async () => {
      const stream = await open('cookie', { name: '', secret: '' })

      expect(stream.options.withCredentials, 'the browser must attach the HttpOnly session cookie').toBe(true)
      expect(stream.options.headers?.Authorization, 'a cookie session has no token to send').toBeUndefined()
    })

    it('ignores a token that is still around in cookie mode', async () => {
      const stream = await open('cookie', { name: 'ops', secret: 'secret' })

      expect(stream.options.headers?.Authorization).toBeUndefined()
    })

    it('keeps the token header in token mode', async () => {
      const stream = await open('token', { name: 'ops', secret: 'secret' })

      expect(stream.options.headers.Authorization).toBe(`xBasic ${btoa('ops:secret')}`)
      expect(stream.options.withCredentials, 'a token session does not need cookies').toBeFalsy()
    })

    it('keeps the token header when nobody is known to be signed in', async () => {
      const stream = await open(undefined, { name: 'ops', secret: 'secret' })

      expect(stream.options.headers.Authorization).toBe(`xBasic ${btoa('ops:secret')}`)
    })

    it('still never retries on its own', async () => {
      const stream = await open('cookie', { name: '', secret: '' })

      expect(stream.options.disableRetry).toBe(true)
    })
  })

  describe('stream failure', () => {
    const streamWithProbe = (probe) => {
      session.client = ref({ auth: { me: probe }, console: { execute: vi.fn() } })
    }

    const failStream = async (useConsole) => {
      const store = useConsole()
      store.connect({ name: 'ops', secret: 'secret' })
      FakeEventSource.instances[0].onerror(new Event('error'))
      await flushPromises()
      return store
    }

    it('says the session expired when the stream fails and the session check answers 401', async () => {
      const probe = vi.fn(() => Promise.reject(Object.assign(new Error('Unauthorized'), { response: { status: 401 } })))
      streamWithProbe(probe)
      const { default: useConsole } = await import('./connection')

      const { failure, sessionExpired } = await failStream(useConsole)

      expect(probe, 'the failure should be checked against the session, not guessed').toHaveBeenCalledTimes(1)
      expect(sessionExpired.value, 'a 401 from the session check means the session expired').toBe(true)
      expect(failure.value, 'the reader should be told the session expired, not that the stream closed').toContain('session has expired')
    })

    it('keeps the generic failure when the session check cannot reach the server', async () => {
      streamWithProbe(vi.fn(() => Promise.reject(new Error('Network Error'))))
      const { default: useConsole } = await import('./connection')

      const { failure, sessionExpired } = await failStream(useConsole)

      expect(sessionExpired.value, 'an unreachable server is not an expired session').toBe(false)
      expect(failure.value, 'without a 401 the message must not blame the session').toBe(
        'The server closed the stream or could not be reached.'
      )
    })
  })
})
