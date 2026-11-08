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

import { Readable, Transform, pipeline } from 'node:stream'
import { Agent, request } from 'undici'
import { securityHeaders } from './security-headers.js'
import { isHopByHop } from './upstream.js'

// The same budget the nginx location gave a read and a send: five minutes between two chunks, so a slow
// artifact is not cut off while it streams, and a stalled connection does not hold a socket forever.
const TIMEOUT_MS = 300000

// Bodies are not decoded or buffered: the bytes of the server reach the client as they arrive.
const agent = new Agent({ headersTimeout: TIMEOUT_MS, bodyTimeout: TIMEOUT_MS, connectTimeout: 10000 })

// Status codes that cannot carry a body.
const NO_BODY = new Set([101, 103, 204, 205, 304])

// A limit on the request body. It counts the bytes as they stream, so a chunked upload without a length is
// stopped too. The returned state says whether the limit was the reason the upload ended.
export function limitBody(stream, maxBytes) {
  const state = { exceeded: false }
  if (!Number.isFinite(maxBytes)) {
    return { body: stream, state }
  }
  let seen = 0
  const limiter = new Transform({
    transform(chunk, _encoding, done) {
      seen += chunk.length
      if (seen > maxBytes) {
        state.exceeded = true
        done(new Error('request body above the upload limit'))
        return
      }
      done(null, chunk)
    },
  })
  pipeline(stream, limiter, () => {})
  return { body: limiter, state }
}

// Headers of the request to the server: the client's own, without the hop by hop ones, and the forwarding
// headers rebuilt from the connection.
export function outgoingRequestHeaders(incoming, forwarded) {
  const headers = {}
  for (const [name, value] of Object.entries(incoming)) {
    if (value === undefined || isHopByHop(name) || name === 'host' || name.startsWith('x-forwarded-') || name === 'x-real-ip') {
      continue
    }
    headers[name] = Array.isArray(value) ? value.join(', ') : value
  }
  for (const [name, value] of forwarded) {
    headers[name.toLowerCase()] = value
  }
  return headers
}

function responseHeaders(upstreamHeaders) {
  const headers = new Headers()
  for (const [name, value] of Object.entries(upstreamHeaders)) {
    if (isHopByHop(name)) {
      continue
    }
    if (Array.isArray(value)) {
      value.forEach(item => headers.append(name, item))
    } else {
      headers.set(name, value)
    }
  }
  for (const [name, value] of Object.entries(securityHeaders())) {
    headers.set(name, value)
  }
  return headers
}

function plain(status, text) {
  return new Response(text, {
    status,
    headers: { ...securityHeaders(), 'Content-Type': 'text/plain; charset=utf-8' },
  })
}

// Sends the request to the server and answers with what it answers. `req` is the incoming Node request, so
// its body streams through without being read here.
export async function proxyRequest({ req, method, target, headers, maxUploadBytes }) {
  const sendsBody = method !== 'GET' && method !== 'HEAD'
  const limited = sendsBody ? limitBody(req, maxUploadBytes) : null

  let upstream
  try {
    upstream = await request(target, {
      method,
      headers,
      body: limited ? limited.body : undefined,
      dispatcher: agent,
    })
  } catch (error) {
    if (limited?.state.exceeded) {
      return plain(413, 'Request Entity Too Large')
    }
    return plain(502, `Bad Gateway: ${error.code ?? error.message}`)
  }

  const { statusCode, headers: upstreamHeaders, body } = upstream
  const empty = method === 'HEAD' || NO_BODY.has(statusCode)
  if (empty) {
    body.dump()
  }
  return new Response(empty ? null : Readable.toWeb(body), {
    status: statusCode,
    headers: responseHeaders(upstreamHeaders),
  })
}
