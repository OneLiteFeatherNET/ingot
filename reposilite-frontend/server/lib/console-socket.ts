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

// The WebSocket of the console, relayed to the server. The upgrade is accepted here, and every message
// is passed on in both directions. The console protocol is text: the first message carries the credentials,
// and every later one is a command or a keep-alive. Messages from the browser are therefore sent upstream as
// text, and text from the server is sent back as text.

import type { Hooks, Peer } from 'crossws'
import WebSocket from 'ws'
import { dashboardOrNull } from './state'
import { classify } from './route-classifier'
import { upstreamUrl, forwardedHeaders } from './upstream'

// The headers of the browser that the server needs to know who is connecting.
const FORWARDED_BY_CLIENT = ['cookie', 'authorization', 'origin', 'user-agent']

interface Link {
  // Null only when the upstream socket could not be created; the link then never opens.
  upstream: WebSocket | null
  pending: string[]
  open: boolean
}

const links = new WeakMap<Peer, Link>()

function dashboardState() {
  const state = dashboardOrNull()
  if (!state) {
    throw new Error('the dashboard server has not started')
  }
  return state
}

function upstreamSocketUrl(peer: Peer): string {
  const { config, segments } = dashboardState()
  const url = new URL(peer.request.url)
  const route = classify(url.pathname, { segments, basePath: config.basePath })
  // The upgrade hook accepts only proxied paths, so a route that reaches this point has a path.
  const path = 'path' in route ? route.path : ''
  return upstreamUrl(config.backend, path, url.search).replace(/^http/, 'ws')
}

function upstreamHeaders(peer: Peer): Record<string, string> {
  const incoming = peer.request.headers
  const headers: Record<string, string> = {}
  for (const name of FORWARDED_BY_CLIENT) {
    const value = incoming.get(name)
    if (value !== null) {
      headers[name] = value
    }
  }
  const forwarded = forwardedHeaders({
    host: incoming.get('host') ?? 'localhost',
    remoteAddress: peer.remoteAddress ?? '',
    forwardedFor: incoming.get('x-forwarded-for') ?? '',
    protocol: incoming.get('x-forwarded-proto') ?? 'http',
  })
  for (const [name, value] of forwarded) {
    if (name.toLowerCase() !== 'upgrade' && name.toLowerCase() !== 'connection') {
      headers[name.toLowerCase()] = value
    }
  }
  return headers
}

export const socketHooks: Partial<Hooks> = {
  // Only the proxied paths upgrade. Every other address is refused with 404, before the socket is accepted.
  upgrade(request) {
    const { config, segments } = dashboardState()
    const route = classify(new URL(request.url).pathname, { segments, basePath: config.basePath })
    if (route.kind !== 'proxy') {
      return new Response(null, { status: 404 })
    }
    return undefined
  },

  open(peer) {
    const link: Link = { upstream: null, pending: [], open: false }
    links.set(peer, link)

    const upstream = new WebSocket(upstreamSocketUrl(peer), { headers: upstreamHeaders(peer), perMessageDeflate: false })
    link.upstream = upstream

    upstream.on('open', () => {
      link.open = true
      link.pending.splice(0).forEach(text => upstream.send(text))
    })
    upstream.on('message', (data, isBinary) => {
      try {
        // ws delivers the frames as Buffers (its default binary type), so a text frame is read as UTF-8 as it always was.
        // eslint-disable-next-line @typescript-eslint/no-base-to-string -- the union includes ArrayBuffer, which is never delivered
        peer.send(isBinary ? data : data.toString('utf8'))
      } catch {
        upstream.close(1000)
      }
    })
    upstream.on('close', () => {
      try {
        peer.close(1000)
      } catch {
        // The browser has already gone.
      }
    })
    upstream.on('error', () => {
      try {
        peer.close(1011, 'the server is not reachable')
      } catch {
        // The browser has already gone.
      }
    })
  },

  message(peer, message) {
    const link = links.get(peer)
    if (!link) {
      return
    }
    const text = message.text()
    if (link.open) {
      link.upstream?.send(text)
    } else {
      link.pending.push(text)
    }
  },

  close(peer) {
    const link = links.get(peer)
    if (!link) {
      return
    }
    links.delete(peer)
    const upstream = link.upstream
    if (upstream && (upstream.readyState === WebSocket.OPEN || upstream.readyState === WebSocket.CONNECTING)) {
      upstream.close(1000)
    }
  },
}
