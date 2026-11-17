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

// The address of a request on the server, and the headers that travel with it.

// A path prefix of the backend stays in front of the request path.
export function upstreamUrl(backend, path, search = '') {
  const url = new URL(backend)
  const prefix = url.pathname.replace(/\/$/, '')
  return `${url.origin}${prefix}${path}${search}`
}

const HOP_BY_HOP = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
])

export function isHopByHop(name) {
  return HOP_BY_HOP.has(name.toLowerCase())
}

// The forwarding headers of the nginx location: the host, the address of the client, the chain of
// addresses it came through, and the scheme it used to reach this container.
export function forwardedHeaders({ host, remoteAddress, forwardedFor = '', protocol, upgrade }) {
  const headers = new Headers()
  headers.set('Host', host)
  headers.set('X-Real-IP', remoteAddress)
  headers.set('X-Forwarded-For', forwardedFor ? `${forwardedFor}, ${remoteAddress}` : remoteAddress)
  headers.set('X-Forwarded-Proto', protocol)
  if (upgrade) {
    headers.set('Upgrade', upgrade)
    headers.set('Connection', 'upgrade')
  }
  return headers
}
