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

import { describe, it, expect } from 'vitest'
import { upstreamUrl, forwardedHeaders, isHopByHop } from './upstream.js'

describe('upstream url', () => {
  it('keeps the path and the query of the request', () => {
    expect(upstreamUrl('http://ingot-backend:8080', '/releases/com/example/app-1.0.jar', '?download=1'))
      .toBe('http://ingot-backend:8080/releases/com/example/app-1.0.jar?download=1')
  })

  it('does not double the slash when the backend ends with one', () => {
    expect(upstreamUrl('http://server:8080/', '/api/status/health', '')).toBe('http://server:8080/api/status/health')
  })

  it('keeps a path prefix of the backend in front of the request path', () => {
    expect(upstreamUrl('https://proxy.internal/ingot', '/api/status/health', '')).toBe('https://proxy.internal/ingot/api/status/health')
  })
})

describe('forwarded headers', () => {
  it('sets the forwarding headers the nginx location set', () => {
    const headers = forwardedHeaders({
      host: 'repo.example.org',
      remoteAddress: '203.0.113.7',
      forwardedFor: '198.51.100.2',
      protocol: 'https',
    })

    expect(headers.get('Host')).toBe('repo.example.org')
    expect(headers.get('X-Real-IP')).toBe('203.0.113.7')
    expect(headers.get('X-Forwarded-For')).toBe('198.51.100.2, 203.0.113.7')
    expect(headers.get('X-Forwarded-Proto')).toBe('https')
  })

  it('passes the websocket upgrade headers on', () => {
    const headers = forwardedHeaders({ host: 'a', remoteAddress: '1.2.3.4', forwardedFor: '', protocol: 'http', upgrade: 'websocket' })

    expect(headers.get('Upgrade')).toBe('websocket')
    expect(headers.get('Connection'), 'the connection header follows the upgrade').toBe('upgrade')
  })

  it('treats hop by hop headers as not to be forwarded', () => {
    expect(isHopByHop('connection')).toBe(true)
    expect(isHopByHop('Transfer-Encoding')).toBe(true)
    expect(isHopByHop('content-type')).toBe(false)
  })
})
