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

import WebSocket from 'ws'
import { dashboardOrNull } from './state.js'
import { classify } from './route-classifier.js'
import { upstreamUrl, forwardedHeaders } from './upstream.js'

// The headers of the browser that the server needs to know who is connecting.
const FORWARDED_BY_CLIENT = ['cookie', 'authorization', 'origin', 'user-agent']

const links = new WeakMap()

function dashboardState() {
  const state = dashboardOrNull()
  if (!state) {
    throw new Error('the dashboard server has not started')
  }
  return state
}

function upstreamSocketUrl(peer) {
  const { config, segments } = dashboardState()
  const url = new URL(peer.request.url)
  const route = classify(url.pathname, { segments, basePath: config.basePath })
  return upstreamUrl(config.backend, route.path, url.search).replace(/^http/, 'ws')
}

function upstreamHeaders(peer) {
  const incoming = peer.request.headers
  const headers = Object.fromEntries(
    FORWARDED_BY_CLIENT.filter(name => incoming.has(name)).map(name => [name, incoming.get(name)])
  )
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

export const socketHooks = {
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
    const link = { upstream: null, pending: [], open: false }
    links.set(peer, link)

    const upstream = new WebSocket(upstreamSocketUrl(peer), { headers: upstreamHeaders(peer), perMessageDeflate: false })
    link.upstream = upstream

    upstream.on('open', () => {
      link.open = true
      link.pending.splice(0).forEach(text => upstream.send(text))
    })
    upstream.on('message', (data, isBinary) => {
      try {
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
      link.upstream.send(text)
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
    if (link.upstream.readyState === WebSocket.OPEN || link.upstream.readyState === WebSocket.CONNECTING) {
      link.upstream.close(1000)
    }
  },
}
