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

// Every request comes through here. The route decides the answer: the document, a bundled asset, the health
// answer, or the server. The WebSocket hooks serve the console upgrade on the same route.

/* global defineEventHandler */

import { dashboardOrNull } from '../lib/state.js'
import { classify } from '../lib/route-classifier.js'
import { cacheControlFor, securityHeaders } from '../lib/security-headers.js'
import { contentTypeFor } from '../lib/mime.js'
import { forwardedHeaders, upstreamUrl } from '../lib/upstream.js'
import { outgoingRequestHeaders, proxyRequest } from '../lib/proxy.js'
import { socketHooks } from '../lib/console-socket.js'

function respond(status, body, headers, method) {
  return new Response(method === 'HEAD' ? null : body, {
    status,
    headers: { ...securityHeaders(), ...headers },
  })
}

function unavailable() {
  return respond(503, 'The dashboard server has not started', { 'Content-Type': 'text/plain; charset=utf-8' }, 'GET')
}

async function asset(state, path, method, kind) {
  const bytes = await state.storage.getItemRaw(path.slice(1))
  if (bytes === null) {
    return respond(404, 'Not Found', { 'Content-Type': 'text/plain; charset=utf-8' }, method)
  }
  return respond(200, bytes, {
    'Content-Type': contentTypeFor(path),
    'Cache-Control': cacheControlFor(kind),
  }, method)
}

export default defineEventHandler({
  async handler(event) {
    const state = dashboardOrNull()
    if (!state) {
      return unavailable()
    }

    const req = event.node.req
    const method = req.method
    const url = new URL(event.path, 'http://dashboard.local')
    const route = classify(url.pathname, { segments: state.segments, basePath: state.config.basePath })

    if (route.kind === 'health') {
      return respond(200, 'ok\n', { 'Content-Type': 'text/plain; charset=utf-8' }, method)
    }
    if (route.kind === 'outside') {
      return respond(404, 'Not Found', { 'Content-Type': 'text/plain; charset=utf-8' }, method)
    }
    if (route.kind === 'document' && (method === 'GET' || method === 'HEAD')) {
      return respond(200, await state.document(), {
        'Content-Type': 'text/html; charset=utf-8',
        'Cache-Control': cacheControlFor('document'),
      }, method)
    }
    if (route.kind === 'asset' && (method === 'GET' || method === 'HEAD')) {
      return asset(state, route.path, method, 'asset')
    }
    if (route.kind === 'favicon' && (method === 'GET' || method === 'HEAD')) {
      return asset(state, route.path, method, 'favicon')
    }

    const declared = Number(req.headers['content-length'] ?? 0)
    if (declared > state.config.maxUploadBytes) {
      return respond(413, 'Request Entity Too Large', { 'Content-Type': 'text/plain; charset=utf-8' }, method)
    }

    const forwarded = forwardedHeaders({
      host: req.headers.host ?? 'localhost',
      remoteAddress: req.socket.remoteAddress ?? '',
      forwardedFor: req.headers['x-forwarded-for'] ?? '',
      protocol: req.headers['x-forwarded-proto'] ?? 'http',
    })
    return proxyRequest({
      req,
      method,
      target: upstreamUrl(state.config.backend, route.path, url.search),
      headers: outgoingRequestHeaders(req.headers, forwarded),
      maxUploadBytes: state.config.maxUploadBytes,
    })
  },
  websocket: socketHooks,
})
