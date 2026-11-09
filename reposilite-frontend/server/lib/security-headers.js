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

// Sent on every response, including the ones proxied from the server, as the nginx configuration did.
// The policy is the one the backend sends for the embedded dashboard (DASHBOARD_POLICY in SecurityHeaders.kt),
// keep both in step. 'unsafe-eval' is there because Ajv, used by the JSON forms, compiles schemas with
// `Function`; drop it once the validators are precompiled. Images stay open because the organisation logo
// is a configurable remote URL.
const SECURITY_HEADERS = Object.freeze({
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'same-origin',
  'Content-Security-Policy': "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https: http:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'",
})

const CACHE_CONTROL = Object.freeze({
  asset: 'public, max-age=31536000, immutable',
  favicon: 'public, max-age=604800',
  document: 'no-cache',
})

export function securityHeaders() {
  return { ...SECURITY_HEADERS }
}

export function cacheControlFor(kind) {
  return CACHE_CONTROL[kind]
}
