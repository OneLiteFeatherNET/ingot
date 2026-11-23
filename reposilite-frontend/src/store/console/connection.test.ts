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
import { ref, type Ref } from 'vue'
import { flushPromises } from '@vue/test-utils'

// The store reads the session for its client and token at import time, and it talks to the
// console through a WebSocket. Both are replaced so the test runs without a server, a token
// or a browser socket.
const session = vi.hoisted(() => ({
  client: null as Ref<unknown> | null,
  token: null,
  authMode: null as Ref<string | undefined> | null
}))
vi.mock('../session', () => ({ useSession: () => session }))
vi.mock('../client', () => ({ createURL: (path: string) => `http://localhost${path}` }))
vi.mock('../../helpers/toast', () => ({
  createErrorToast: vi.fn(),
  errorMessage: (error: Error) => error.message
}))

// A socket that never leaves the test: the test reads what the store sent and plays the server itself.
const { FakeWebSocket } = vi.hoisted(() => {
  class FakeWebSocket {
    static instances: FakeWebSocket[] = []

    sent: string[] = []
    readyState = 0
    onopen: (() => void) | null = null
    onmessage: ((event: { data: unknown }) => void) | null = null
    onclose: ((event: { code: number }) => void) | null = null

    constructor(readonly url: string) {
      FakeWebSocket.instances.push(this)
      queueMicrotask(() => {
        this.readyState = 1
        this.onopen?.()
      })
    }

    send(data: string) {
      this.sent.push(data)
    }

    close() {
      this.readyState = 3
    }

    receive(frame: object) {
      this.onmessage?.({ data: JSON.stringify(frame) })
    }

    drop(code: number) {
      this.readyState = 3
      this.onclose?.({ code })
    }

    hello(): Record<string, unknown> {
      return JSON.parse(this.sent[0]) as Record<string, unknown>
    }
  }
  return { FakeWebSocket }
})

describe('useConsole', () => {
  beforeEach(() => {
    // Every test imports the store afresh, so the command history and the other module-scope
    // state of one test cannot leak into the next one, whatever order they run in.
    vi.resetModules()
    FakeWebSocket.instances.length = 0
    vi.stubGlobal('WebSocket', FakeWebSocket)
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('the command listing is not answered'))))
    sessionStorage.clear()
    session.authMode = null
    session.client = ref({ auth: { me: vi.fn(() => Promise.resolve()) }, console: { execute: vi.fn() } })
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  const welcome = (socket: InstanceType<typeof FakeWebSocket>) =>
    socket.receive({ type: 'welcome', protocol: 1, boot: 'boot-1', node: 'node-a', seq: 0, replay: null })

  describe('sending a command', () => {
    it('keeps the typed command in the input and sends no HTTP request while the socket is not ready', async () => {
      const execute = vi.fn()
      session.client = ref({ console: { execute } })
      const { default: useConsole } = await import('./connection')
      const { command, execute: send, open } = useConsole()
      open({ name: 'ops', secret: 'secret' })
      command.value = 'say hello'

      send()

      expect(command.value, 'a command that was not sent must stay in the input so it can be sent again').toBe('say hello')
      expect(execute, 'a command never goes over HTTP').not.toHaveBeenCalled()
    })

    it('sends an execute frame once the server has welcomed the socket', async () => {
      const { default: useConsole } = await import('./connection')
      const { command, execute, open } = useConsole()
      open({ name: 'ops', secret: 'secret' })
      await flushPromises()
      welcome(FakeWebSocket.instances[0])
      command.value = 'status'

      execute()

      expect(JSON.parse(FakeWebSocket.instances[0].sent.at(-1)!), 'the command goes over the socket').toMatchObject({ type: 'execute', command: 'status' })
      expect(command.value, 'a sent command leaves the input').toBe('')
    })
  })

  describe('command history', () => {
    it('keeps the commands sent before the console was opened again', async () => {
      const { default: useConsole } = await import('./connection')
      const beforeRemount = useConsole()
      beforeRemount.command.value = 'list'
      beforeRemount.execute()

      // Opening the view again creates a second call to the composable, as a remount does.
      const afterRemount = useConsole()
      afterRemount.previousCommand()

      expect(afterRemount.command.value, 'the arrow key should still recall the command sent before the remount').toBe('list')
    })
  })

  describe('authenticating the socket', () => {
    const open = async (mode: string | undefined, token: { name: string; secret: string }) => {
      session.authMode = ref(mode)
      const { default: useConsole } = await import('./connection')
      useConsole().open(token)
      await flushPromises()
      return FakeWebSocket.instances[0]
    }

    it('sends no token in cookie mode, so the browser cookie is the credential', async () => {
      const socket = await open('cookie', { name: '', secret: '' })

      expect(socket.hello(), 'a cookie session has no secret for the hello').not.toHaveProperty('token')
    })

    it('ignores a token that is still around in cookie mode', async () => {
      const socket = await open('cookie', { name: 'ops', secret: 'secret' })

      expect(socket.hello()).not.toHaveProperty('token')
    })

    it('sends the token in the hello in token mode', async () => {
      const socket = await open('token', { name: 'ops', secret: 'secret' })

      expect(socket.hello()).toMatchObject({ type: 'hello', protocols: [1], token: 'ops:secret' })
    })

    it('sends the token when nobody is known to be signed in', async () => {
      const socket = await open(undefined, { name: 'ops', secret: 'secret' })

      expect(socket.hello()).toMatchObject({ token: 'ops:secret' })
    })
  })

  describe('socket failure', () => {
    const refuse = async (probe: () => Promise<unknown>) => {
      session.client = ref({ auth: { me: probe }, console: { execute: vi.fn() } })
      const { default: useConsole } = await import('./connection')
      const store = useConsole()
      store.open({ name: 'ops', secret: 'secret' })
      await flushPromises()
      FakeWebSocket.instances[0].drop(1008)
      await flushPromises()
      return store
    }

    it('says the session expired when the server refuses the socket and the session check answers 401', async () => {
      const probe = vi.fn(() => Promise.reject(Object.assign(new Error('Unauthorized'), { response: { status: 401 } })))

      const { failure, sessionExpired, status } = await refuse(probe)

      expect(probe, 'the failure should be checked against the session, not guessed').toHaveBeenCalledTimes(1)
      expect(status.value, 'a refused socket is an error').toBe('error')
      expect(sessionExpired.value, 'a 401 from the session check means the session expired').toBe(true)
      expect(failure.value, 'the reader should be told the session expired').toContain('session has expired')
    })

    it('keeps the generic failure when the session check cannot reach the server', async () => {
      const { failure, sessionExpired } = await refuse(vi.fn(() => Promise.reject(new Error('Network Error'))))

      expect(sessionExpired.value, 'an unreachable server is not an expired session').toBe(false)
      expect(failure.value, 'without a 401 the message must not blame the session').toBe('The server ended the console session.')
    })

    it('does not open another socket after a refusal', async () => {
      await refuse(vi.fn(() => Promise.resolve()))

      expect(FakeWebSocket.instances, 'a refusal is final until the reader reconnects').toHaveLength(1)
    })
  })
})
