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
import { ConsoleSocket, type LogFrame, type SocketLike } from './socket'

// A socket the test drives by hand: it records what the client sends and fires the events itself.
class FakeSocket implements SocketLike {
  readyState = 0
  sent: string[] = []
  onopen: (() => void) | null = null
  onmessage: ((event: { data: unknown }) => void) | null = null
  onclose: ((event: { code: number }) => void) | null = null

  constructor(readonly url: string) {}

  send(data: string) {
    this.sent.push(data)
  }

  close() {
    this.readyState = 3
  }

  open() {
    this.readyState = 1
    this.onopen?.()
  }

  receive(frame: object) {
    this.onmessage?.({ data: JSON.stringify(frame) })
  }

  drop(code = 1006) {
    this.readyState = 3
    this.onclose?.({ code })
  }

  frames(): Array<Record<string, unknown>> {
    return this.sent.map(text => JSON.parse(text) as Record<string, unknown>)
  }
}

// Every test gets its own sockets, listener and clock, so nothing carries over between tests.
function harness() {
  const created: FakeSocket[] = []
  const listener = {
    onState: vi.fn(),
    onWelcome: vi.fn(),
    onLog: vi.fn(),
    onResult: vi.fn(),
    onError: vi.fn(),
    onUnknown: vi.fn(),
    onConfirm: vi.fn(),
    onComplete: vi.fn(),
  }
  const client = new ConsoleSocket({
    url: 'ws://dashboard.example.org/api/console/sock',
    client: 'ingot-dashboard/1.0',
    listener,
    createSocket: url => {
      const socket = new FakeSocket(url)
      created.push(socket)
      return socket
    },
    random: () => 0.5,
    nextId: (() => {
      let next = 0
      return () => `e${++next}`
    })(),
  })
  return { client, listener, created, current: () => created[created.length - 1] }
}

// A session that has completed the handshake and is ready for commands.
function readySession(boot = 'boot-1', node = 'node-a') {
  const h = harness()
  h.client.connect()
  h.current().open()
  h.current().receive({ type: 'welcome', protocol: 1, boot, node, seq: 0, replay: null })
  return h
}

describe('console socket client', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('handshake', () => {
    it('sends a hello that lists protocol 1 and names the client when the socket opens', () => {
      const { client, current } = harness()
      client.connect()
      current().open()

      expect(current().frames()[0], 'the first frame of a version 1 connection is the hello').toMatchObject({
        type: 'hello',
        protocols: [1],
        client: 'ingot-dashboard/1.0',
      })
    })

    it('sends no resume on the first connection', () => {
      const { client, current } = harness()
      client.connect()
      current().open()

      expect(current().frames()[0]).not.toHaveProperty('resume')
    })

    it('becomes ready and names the node when the welcome arrives', () => {
      const { client, listener, current } = harness()
      client.connect()
      current().open()
      current().receive({ type: 'welcome', protocol: 1, boot: 'boot-1', node: 'node-a', seq: 0, replay: null })

      expect(listener.onWelcome, 'the welcome tells the page which node it is talking to').toHaveBeenCalledWith(
        expect.objectContaining({ node: 'node-a', boot: 'boot-1' }),
      )
      expect(listener.onState).toHaveBeenLastCalledWith('ready')
    })

    it('sends nothing for a command before the welcome, because the input is disabled while the socket is down', () => {
      const { client, current } = harness()
      client.connect()
      current().open()

      const id = client.execute('status')

      expect(id, 'a command typed before the welcome is not sent').toBeNull()
      expect(current().frames().map(frame => frame.type), 'only the hello has been sent').toEqual(['hello'])
    })
  })

  describe('resume after a drop', () => {
    it('resumes after the boot and the sequence number of the last log line it holds', () => {
      const { listener, created, current } = readySession('boot-1', 'node-a')
      current().receive({ type: 'log', seq: 411, node: 'node-a', level: 'INFO', text: 'a', dropped: 0 })
      current().receive({ type: 'log', seq: 412, node: 'node-a', level: 'INFO', text: 'b', dropped: 0 })
      expect(listener.onLog).toHaveBeenCalledTimes(2)

      current().drop()
      vi.advanceTimersByTime(1000)
      current().open()

      expect(created).toHaveLength(2)
      expect(current().frames()[0], 'the hello after a drop asks to resume after the last line').toMatchObject({
        resume: { boot: 'boot-1', seq: 412 },
      })
    })

    it('drops a replayed line whose node and sequence number it already shows', () => {
      const { listener, current } = readySession('boot-1', 'node-a')
      current().receive({ type: 'log', seq: 411, node: 'node-a', level: 'INFO', text: 'a', dropped: 0 })
      current().receive({ type: 'log', seq: 412, node: 'node-a', level: 'INFO', text: 'b', dropped: 0 })
      current().drop()
      vi.advanceTimersByTime(1000)
      current().open()
      current().receive({ type: 'welcome', protocol: 1, boot: 'boot-1', node: 'node-a', seq: 412, replay: 'resumed' })

      current().receive({ type: 'log', seq: 412, node: 'node-a', level: 'INFO', text: 'b', dropped: 0 })
      current().receive({ type: 'log', seq: 413, node: 'node-a', level: 'INFO', text: 'c', dropped: 0 })

      expect(listener.onLog.mock.calls.map(call => (call[0] as LogFrame).seq), 'the replayed line 412 is shown once').toEqual([411, 412, 413])
    })

    it('shows two lines with the same sequence number when they come from different nodes', () => {
      const { listener, current } = readySession('boot-1', 'node-a')

      current().receive({ type: 'log', seq: 5, node: 'node-a', level: 'INFO', text: 'a', dropped: 0 })
      current().receive({ type: 'log', seq: 5, node: 'node-b', level: 'INFO', text: 'b', dropped: 0 })

      expect(listener.onLog, 'the identity of a line is its node and its sequence number together').toHaveBeenCalledTimes(2)
    })
  })

  describe('commands', () => {
    it('shows an execute without a result as unknown when the socket drops', () => {
      const { client, listener, current } = readySession()
      client.execute('restart')

      current().drop()

      expect(listener.onUnknown, 'a command that lost its result must not look like a failure or a success').toHaveBeenCalledWith('e1', 'restart')
    })

    it('does not send an unknown execute again after reconnecting', () => {
      const { client, created, current } = readySession()
      client.execute('restart')
      current().drop()
      vi.advanceTimersByTime(1000)
      current().open()
      current().receive({ type: 'welcome', protocol: 1, boot: 'boot-1', node: 'node-a', seq: 0, replay: 'resumed' })

      const executes = created.flatMap(socket => socket.frames()).filter(frame => frame.type === 'execute')
      expect(executes, 'the command ran at most once, so it must not be sent again').toHaveLength(1)
    })

    it('shows a busy answer to a second execute and does not count it as unknown', () => {
      const { client, listener, current } = readySession()
      client.execute('status')
      const second = client.execute('list')
      expect(second).toBe('e2')

      current().receive({ type: 'error', code: 'busy', message: 'a command is already running', id: 'e2', retryable: true })

      expect(listener.onError, 'the busy answer reaches the page').toHaveBeenCalledWith(
        expect.objectContaining({ code: 'busy', id: 'e2', retryable: true }),
      )
      current().drop()
      expect(listener.onUnknown.mock.calls.map(call => call[0] as string), 'only the first command, still without a result, is unknown').toEqual(['e1'])
    })
  })

  describe('confirmation', () => {
    it('hands a confirm frame of a dangerous command to the page', () => {
      const { client, listener, current } = readySession()
      client.execute('token-revoke ci')

      current().receive({ type: 'confirm', id: 'e1', command: 'token-revoke', reason: 'Revoke token' })

      expect(listener.onConfirm, 'the page asks the user before the command runs').toHaveBeenCalledWith(
        expect.objectContaining({ id: 'e1', command: 'token-revoke', reason: 'Revoke token' }),
      )
    })

    it('answers a confirmation with a confirm frame for the same id', () => {
      const { client, current } = readySession()
      client.execute('token-revoke ci')
      current().receive({ type: 'confirm', id: 'e1', command: 'token-revoke', reason: 'Revoke token' })

      expect(client.confirm('e1', false), 'the answer is sent while the socket is ready').toBe(true)

      expect(current().frames().at(-1), 'a declined confirmation tells the server not to run the command').toEqual({ type: 'confirm', id: 'e1', accept: false })
    })

    it('keeps a confirmed command pending until its result arrives', () => {
      const { client, listener, current } = readySession()
      client.execute('token-revoke ci')
      current().receive({ type: 'confirm', id: 'e1', command: 'token-revoke', reason: 'Revoke token' })
      client.confirm('e1', true)

      current().drop()

      expect(listener.onUnknown, 'an accepted command without a result may have run').toHaveBeenCalledWith('e1', 'token-revoke ci')
    })

    it('sends no answer when the socket is not ready', () => {
      const { client, current } = readySession()
      client.execute('token-revoke ci')
      current().receive({ type: 'confirm', id: 'e1', command: 'token-revoke', reason: 'Revoke token' })
      current().drop()

      expect(client.confirm('e1', true), 'an answer is not queued for a later connection').toBe(false)
      expect(current().frames().filter(frame => frame.type === 'confirm'), 'nothing is sent on a dropped socket').toHaveLength(0)
    })
  })

  describe('refusal and backoff', () => {
    it('stops reconnecting after the server closes with 1008', () => {
      const { listener, created, current } = readySession()

      current().drop(1008)
      vi.advanceTimersByTime(120_000)

      expect(listener.onState, 'a refused connection is shown as refused').toHaveBeenLastCalledWith('refused')
      expect(created, 'no reconnect is attempted after a refusal').toHaveLength(1)
    })

    it('waits 1, 2, 4, 8, 16, 30 and 30 seconds between attempts while no welcome arrives', () => {
      const h = harness()
      h.client.connect()
      h.current().open()
      const expected = [1000, 2000, 4000, 8000, 16000, 30000, 30000]

      for (const delay of expected) {
        const before = h.created.length
        h.current().drop()
        vi.advanceTimersByTime(delay - 1)
        expect(h.created.length, `no attempt before ${delay} ms`).toBe(before)
        vi.advanceTimersByTime(1)
        expect(h.created.length, `one attempt at ${delay} ms`).toBe(before + 1)
      }
    })

    it('starts the backoff again at 1 second after a welcome', () => {
      const h = harness()
      h.client.connect()
      h.current().open()
      h.current().drop()
      vi.advanceTimersByTime(1000)
      h.current().open()
      h.current().receive({ type: 'welcome', protocol: 1, boot: 'boot-1', node: 'node-a', seq: 0, replay: null })

      const before = h.created.length
      h.current().drop()
      vi.advanceTimersByTime(999)
      expect(h.created.length, 'the first step after a welcome is 1 second, not the 4 seconds of the earlier attempts').toBe(before)
      vi.advanceTimersByTime(1)
      expect(h.created.length).toBe(before + 1)
    })
  })
  describe('completion', () => {
    it('sends one complete frame with the line and the cursor', () => {
      const { client, current } = readySession()

      const id = client.complete('sta', 3)

      expect(id, 'a ready socket takes the request').toBe('e1')
      expect(current().frames().at(-1), 'the frame names the line and the cursor').toEqual({ type: 'complete', id: 'e1', line: 'sta', cursor: 3 })
    })

    it('sends nothing while the socket is not ready', () => {
      const { client, current } = harness()
      client.connect()

      expect(client.complete('sta', 3), 'a completion is not queued').toBeNull()
      expect(current().frames(), 'no frame went out').toHaveLength(0)
    })

    it('reports the answer to the listener and keeps the command pending list clean', () => {
      const { client, listener, current } = readySession()
      const id = client.complete('sta', 3)
      current().receive({ type: 'complete', id, replace: { start: 0, end: 3 }, candidates: ['stats', 'status'] })

      expect(listener.onComplete).toHaveBeenCalledWith(expect.objectContaining({ id, replace: { start: 0, end: 3 }, candidates: ['stats', 'status'] }))
      current().drop()
      expect(listener.onUnknown, 'a completion is no command that may have run').not.toHaveBeenCalled()
    })
  })
})
