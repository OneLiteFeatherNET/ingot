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

import { describe, it, expect, vi } from 'vitest'
import { openSessionChannel, SESSION_CHANNEL_NAME } from './session-channel'

// A channel double: it records what is posted and lets a test deliver a message.
const fakeTransport = () => {
  const transport = {
    name: null,
    posted: [],
    listener: null,
    closed: false,
    postMessage: (message) => transport.posted.push(message),
    addEventListener: (type, listener) => {
      if (type === 'message') transport.listener = listener
    },
    removeEventListener: () => {
      transport.listener = null
    },
    close: () => {
      transport.closed = true
    },
    receive: (data) => transport.listener?.({ data })
  }
  return transport
}

const open = () => {
  const transport = fakeTransport()
  const channel = openSessionChannel((name) => {
    transport.name = name
    return transport
  })
  return { transport, channel }
}

describe('session channel', () => {
  it('is named ingot-session', () => {
    const { transport } = open()

    expect(transport.name, 'every tab must meet on the same channel').toBe('ingot-session')
    expect(SESSION_CHANNEL_NAME).toBe('ingot-session')
  })

  it('posts a signed-in message that carries the principal and nothing else', () => {
    const { transport, channel } = open()

    channel.signedIn({ id: 'p1', kind: 'USER', name: 'alice', displayName: 'Alice', password: 'hunter2', secret: 'x' })

    expect(transport.posted).toEqual([
      { type: 'signed-in', principal: { id: 'p1', kind: 'USER', name: 'alice', displayName: 'Alice' } }
    ])
  })

  it('posts a signed-out message without a payload', () => {
    const { transport, channel } = open()

    channel.signedOut()

    expect(transport.posted).toEqual([{ type: 'signed-out' }])
  })

  it('hands a valid message to the subscriber', () => {
    const { transport, channel } = open()
    const handler = vi.fn()
    channel.subscribe(handler)

    transport.receive({ type: 'signed-in', principal: { id: 'p1', kind: 'USER', name: 'alice', displayName: 'Alice' } })

    expect(handler).toHaveBeenCalledWith({
      type: 'signed-in',
      principal: { id: 'p1', kind: 'USER', name: 'alice', displayName: 'Alice' }
    })
  })

  it('drops fields that are not part of the message contract', () => {
    const { transport, channel } = open()
    const handler = vi.fn()
    channel.subscribe(handler)

    transport.receive({ type: 'signed-in', principal: { id: 'p1', kind: 'USER', name: 'alice', displayName: 'A' }, secret: 'leak' })

    expect(handler.mock.calls[0][0], 'a secret smuggled into a message must never reach the session')
      .not.toHaveProperty('secret')
  })

  it('ignores a message of an unknown type', () => {
    const { transport, channel } = open()
    const handler = vi.fn()
    channel.subscribe(handler)

    transport.receive({ type: 'something-else' })
    transport.receive('not an object')
    transport.receive(null)

    expect(handler).not.toHaveBeenCalled()
  })

  it('stops delivering after unsubscribe', () => {
    const { transport, channel } = open()
    const handler = vi.fn()
    const unsubscribe = channel.subscribe(handler)

    unsubscribe()
    transport.receive({ type: 'signed-out' })

    expect(handler).not.toHaveBeenCalled()
  })

  it('closes the underlying channel', () => {
    const { transport, channel } = open()

    channel.close()

    expect(transport.closed).toBe(true)
  })

  it('is a no-op when no channel can be created', () => {
    const channel = openSessionChannel(() => {
      throw new ReferenceError('BroadcastChannel is not defined')
    })
    const handler = vi.fn()

    expect(() => {
      channel.signedIn({ id: 'p1', kind: 'USER', name: 'alice', displayName: 'Alice' })
      channel.signedOut()
      channel.subscribe(handler)()
      channel.close()
    }, 'a browser without BroadcastChannel must still sign in').not.toThrow()
    expect(handler).not.toHaveBeenCalled()
  })
})
