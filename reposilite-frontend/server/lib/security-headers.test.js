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
import fixture from '../test/fixtures/security-headers.json'
import { securityHeaders, cacheControlFor, documentHeaders } from './security-headers.js'

// The fixture holds the values of the nginx template this server replaces. A change to any of them
// is then a visible change in this test.
describe('security headers', () => {
  it('sends the same values as the previous nginx configuration', () => {
    expect(securityHeaders(), 'the headers of the previous configuration').toEqual(fixture)
  })

  it('allows script-src unsafe-eval, which Ajv needs to compile the form schemas', () => {
    const policy = securityHeaders()['Content-Security-Policy']

    expect(policy, 'the script-src directive').toMatch(/script-src 'self' 'unsafe-eval'(;|$)/)
  })

  it('returns a fresh object, so a caller cannot change the shared values', () => {
    const first = securityHeaders()
    first['X-Frame-Options'] = 'SAMEORIGIN'

    expect(securityHeaders()['X-Frame-Options']).toBe('DENY')
  })
})

describe('cache control', () => {
  it('caches hashed assets for a year as immutable', () => {
    expect(cacheControlFor('asset')).toBe('public, max-age=31536000, immutable')
  })

  it('caches the favicon for seven days', () => {
    expect(cacheControlFor('favicon')).toBe('public, max-age=604800')
  })

  it('never caches the document, which names the bundle to load', () => {
    expect(cacheControlFor('document')).toBe('no-cache')
  })
})

describe('document headers', () => {
  it('open the dashboard document in its own browsing context group', () => {
    expect(documentHeaders()['Cross-Origin-Opener-Policy'], 'the opener policy of the document').toBe('same-origin')
  })

  it('keep every security header of the shared set on the document', () => {
    expect(documentHeaders(), 'the document headers').toMatchObject(securityHeaders())
  })

  it('leave the opener policy off the shared set that assets and the api get', () => {
    expect(securityHeaders(), 'the headers of assets and api answers').not.toHaveProperty('Cross-Origin-Opener-Policy')
  })
})

// The policy the backend sends for the embedded dashboard (DASHBOARD_POLICY in SecurityHeaders.kt, asserted there
// against the same text). Plugin pages add no source to it, so the image and the server stay in step.
const RECORDED_DASHBOARD_POLICY = "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; " +
  "img-src 'self' data: https: http:; font-src 'self' data:; connect-src 'self'; " +
  "object-src 'none'; base-uri 'self'; frame-ancestors 'none'"

describe('dashboard policy with plugin pages', () => {
  it('sends the backend dashboard policy plus form-action self, unchanged by plugin pages', () => {
    expect(securityHeaders()['Content-Security-Policy']).toBe(`${RECORDED_DASHBOARD_POLICY}; form-action 'self'`)
  })
})
