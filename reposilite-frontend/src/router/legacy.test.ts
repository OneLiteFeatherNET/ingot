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
import { legacyLocation } from './legacy'

describe('legacyLocation', () => {
  it('ignores a hash that is not a legacy route', () => {
    expect(legacyLocation('')).toBeNull()
    expect(legacyLocation('#')).toBeNull()
    expect(legacyLocation('#tokens')).toBeNull()
    expect(legacyLocation(undefined)).toBeNull()
  })

  it('maps the root of the old dashboard to overview', () => {
    expect(legacyLocation('#/')).toEqual({ name: 'overview', query: {} })
  })

  it('maps an overview tab with a repository path to the file browser', () => {
    expect(legacyLocation('#/releases/gav/0.1.0?tab=overview')).toEqual({
      name: 'browse',
      params: { qualifier: ['releases', 'gav', '0.1.0'] },
      query: {}
    })
  })

  it('maps a path without a tab to the file browser', () => {
    expect(legacyLocation('#/releases/net')).toEqual({
      name: 'browse',
      params: { qualifier: ['releases', 'net'] },
      query: {}
    })
  })

  it('falls back to the file browser for a tab name it does not know', () => {
    expect(legacyLocation('#/releases?tab=unknown')).toEqual({
      name: 'browse',
      params: { qualifier: ['releases'] },
      query: {}
    })
  })

  it('falls back to overview for an unknown tab without a path', () => {
    expect(legacyLocation('#/?tab=unknown')).toEqual({ name: 'overview', query: {} })
  })

  it('treats a repeated tab parameter as no tab, as the old page did', () => {
    expect(legacyLocation('#/releases?tab=dashboard&tab=tokens')).toEqual({
      name: 'browse',
      params: { qualifier: ['releases'] },
      query: {}
    })
  })

  it('maps the dashboard tab to its route and drops the path', () => {
    expect(legacyLocation('#/org/repo?tab=dashboard')).toEqual({ name: 'dashboard', query: {} })
  })

  it('maps the tokens tab to its route and drops the path', () => {
    expect(legacyLocation('#/?tab=tokens')).toEqual({ name: 'tokens', query: {} })
  })

  it('maps the console tab to its route', () => {
    expect(legacyLocation('#/?tab=console')).toEqual({ name: 'console', query: {} })
  })

  it('maps the settings tab to its route and lets the first domain be the default', () => {
    expect(legacyLocation('#/?tab=settings')).toEqual({ name: 'settings', query: {} })
  })

  it('keeps unrelated query parameters when it maps a tab', () => {
    expect(legacyLocation('#/?tab=tokens&q=ci')).toEqual({ name: 'tokens', query: { q: 'ci' } })
  })

  it('keeps unrelated query parameters on a browse location', () => {
    expect(legacyLocation('#/releases?tab=overview&q=x')).toEqual({
      name: 'browse',
      params: { qualifier: ['releases'] },
      query: { q: 'x' }
    })
  })

  it('decodes percent encoded segments so the router can encode them once', () => {
    expect(legacyLocation('#/my%20dir/x')).toEqual({
      name: 'browse',
      params: { qualifier: ['my dir', 'x'] },
      query: {}
    })
  })

  it('keeps an encoded slash inside a single segment', () => {
    expect(legacyLocation('#/a%2Fb/c')).toEqual({
      name: 'browse',
      params: { qualifier: ['a/b', 'c'] },
      query: {}
    })
  })

  it('keeps a segment that is not valid percent encoding as it was written', () => {
    expect(legacyLocation('#/broken%E0%A4%A/x')).toEqual({
      name: 'browse',
      params: { qualifier: ['broken%E0%A4%A', 'x'] },
      query: {}
    })
  })

  it('ignores empty segments from doubled or trailing slashes', () => {
    expect(legacyLocation('#//releases//gav/')).toEqual({
      name: 'browse',
      params: { qualifier: ['releases', 'gav'] },
      query: {}
    })
  })
})
