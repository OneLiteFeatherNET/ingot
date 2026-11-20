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

import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { Peer } from 'crossws'
import type { DashboardState } from './state'

// The upstream socket is replaced by a stub that the test drives by hand, so the proxy runs without a server or a network.
const { FakeWebSocket } = vi.hoisted(() => {
  class FakeWebSocket {
    static OPEN = 1
    static CONNECTING = 0
    static instances: FakeWebSocket[] = []
    readyState = 0
    sent: string[] = []
    private handlers = new Map<string, (...args: unknown[]) => void>()

    constructor(readonly url: string, readonly options: { headers?: Record<string, string> }) {
      FakeWebSocket.instances.push(this)
    }

    on(event: string, handler: (...args: unknown[]) => void) {
      this.handlers.set(event, handler)
      return this
    }

    send(text: string) {
      this.sent.push(text)
    }

    close() {
      this.readyState = 3
    }

    emit(event: string, ...args: unknown[]) {
      this.handlers.get(event)?.(...args)
    }
  }
  return { FakeWebSocket }
})
vi.mock('ws', () => ({ default: FakeWebSocket }))

const state = vi.hoisted((): { value: unknown } => ({ value: null }))
vi.mock('./state', () => ({ dashboardOrNull: () => state.value }))

const { socketHooks } = await import('./console-socket')

const HELLO = '{"type":"hello","protocols":[1],"client":"ingot-dashboard/1.0"}'
const RESULT = '{"type":"result","id":"e1","status":"ok","exitCode":0,"output":[],"data":null,"node":"n1","confirmed":false}'

function browserPeer(url: string, headers: Record<string, string>) {
  const sent: string[] = []
  const peer = {
    request: new Request(url, { headers }),
    remoteAddress: '203.0.113.7',
    send: vi.fn((text: string) => sent.push(text)),
    close: vi.fn(),
  }
  return { peer: peer as unknown as Peer, sent, raw: peer }
}

function text(value: string) {
  return { text: () => value } as unknown as Parameters<NonNullable<typeof socketHooks.message>>[1]
}

describe('console socket proxy', () => {
  beforeEach(() => {
    FakeWebSocket.instances.length = 0
    state.value = {
      config: { backend: 'http://ingot-backend:8080', basePath: '/' } as DashboardState['config'],
      segments: ['repositories'],
    } satisfies Partial<DashboardState>
  })

  it('relays a version 1 hello from the browser to the server as the same text', () => {
    const { peer } = browserPeer('http://dashboard.example.org/api/console/sock', { origin: 'http://dashboard.example.org' })
    void socketHooks.open?.(peer)
    const upstream = FakeWebSocket.instances[0]
    upstream.emit('open')

    void socketHooks.message?.(peer, text(HELLO))

    expect(upstream.sent, 'the hello must reach the server unchanged, as a text frame').toEqual([HELLO])
  })

  it('holds a frame sent before the server socket is open and sends it on open', () => {
    const { peer } = browserPeer('http://dashboard.example.org/api/console/sock', { origin: 'http://dashboard.example.org' })
    void socketHooks.open?.(peer)
    const upstream = FakeWebSocket.instances[0]

    void socketHooks.message?.(peer, text(HELLO))
    expect(upstream.sent, 'nothing is sent before the server socket opens').toEqual([])

    upstream.emit('open')
    expect(upstream.sent, 'the held hello is sent once the server socket opens').toEqual([HELLO])
  })

  it('relays a text frame from the server to the browser unchanged', () => {
    const { peer, raw } = browserPeer('http://dashboard.example.org/api/console/sock', { origin: 'http://dashboard.example.org' })
    void socketHooks.open?.(peer)
    const upstream = FakeWebSocket.instances[0]

    upstream.emit('message', Buffer.from(RESULT, 'utf8'), false)

    expect(raw.send, 'the result must reach the browser as the same text').toHaveBeenCalledWith(RESULT)
  })

  it('forwards the cookie and origin headers of the browser unchanged', () => {
    const { peer } = browserPeer('http://dashboard.example.org/api/console/sock', {
      cookie: 'ingot_session=abc123',
      origin: 'http://dashboard.example.org',
    })
    void socketHooks.open?.(peer)

    const headers = FakeWebSocket.instances[0].options.headers ?? {}
    expect(headers.cookie, 'the session cookie is what authenticates a dashboard socket').toBe('ingot_session=abc123')
    expect(headers.origin, 'the origin is what the server checks against the cookie').toBe('http://dashboard.example.org')
  })
})
