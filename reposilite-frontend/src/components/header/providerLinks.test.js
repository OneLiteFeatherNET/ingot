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
import { providerLoginHref } from './providerLinks'

// The test page runs at the origin of the test environment, so the origin is read from it.
const SAME_ORIGIN = window.location.origin

describe('providerLoginHref', () => {
  it('keeps a login path of the same origin relative, with the current path as redirect', () => {
    const href = providerLoginHref('/api/auth/oidc/keycloak/login', '/access/sso', (path) => path)

    expect(href).toBe('/api/auth/oidc/keycloak/login?redirect=%2Faccess%2Fsso')
  })

  it('resolves the login path against the API base when the base is another origin', () => {
    const resolve = (path) => `https://ingot.example.org/mount${path}`

    const href = providerLoginHref('/api/auth/oidc/github/login', '/', resolve)

    expect(href).toBe('https://ingot.example.org/mount/api/auth/oidc/github/login?redirect=%2F')
  })

  it('keeps an absolute login url of another origin as it is and adds the redirect', () => {
    const href = providerLoginHref('https://sso.example.org/api/auth/oidc/okta/login', '/audit')

    expect(href).toBe('https://sso.example.org/api/auth/oidc/okta/login?redirect=%2Faudit')
  })

  it('makes an absolute login url of the same origin relative', () => {
    const href = providerLoginHref(`${SAME_ORIGIN}/api/auth/oidc/okta/login`, '/audit')

    expect(href).toBe('/api/auth/oidc/okta/login?redirect=%2Faudit')
  })

  it('keeps the query of the page in the redirect, encoded as one value', () => {
    const href = providerLoginHref('/api/auth/oidc/keycloak/login', '/access/users?tab=2&q=a', (path) => path)

    const redirect = new URL(href, SAME_ORIGIN).searchParams.get('redirect')
    expect(redirect).toBe('/access/users?tab=2&q=a')
  })

  it('does not duplicate a redirect that the login url already names', () => {
    const href = providerLoginHref('/api/auth/oidc/keycloak/login?redirect=/old', '/new', (path) => path)

    const params = new URL(href, SAME_ORIGIN).searchParams
    expect(params.getAll('redirect')).toEqual(['/new'])
  })
})
