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
import { qualifierOf, browseLocation, parentPathOf, childPath } from './paths'

describe('qualifierOf', () => {
  it('joins the segments the browse route captured', () => {
    expect(qualifierOf({ qualifier: ['releases', 'gav', '0.1.0'] })).toBe('releases/gav/0.1.0')
  })

  it('is empty when the route has no qualifier, as on overview', () => {
    expect(qualifierOf({})).toBe('')
  })

  it('is empty for a browse route without segments', () => {
    expect(qualifierOf({ qualifier: [] })).toBe('')
  })

  it('drops the empty segment left by a trailing slash', () => {
    expect(qualifierOf({ qualifier: ['releases', ''] })).toBe('releases')
  })

  it('accepts a single string as well as a list of segments', () => {
    expect(qualifierOf({ qualifier: 'releases/gav' })).toBe('releases/gav')
  })
})

describe('browseLocation', () => {
  it('builds a browse location with one segment per path element', () => {
    expect(browseLocation('releases/gav/0.1.0')).toEqual({
      name: 'browse',
      params: { qualifier: ['releases', 'gav', '0.1.0'] }
    })
  })

  it('points the empty path at overview rather than an empty browse route', () => {
    expect(browseLocation('')).toEqual({ name: 'overview' })
  })

  it('ignores leading and trailing slashes', () => {
    expect(browseLocation('/releases/')).toEqual({
      name: 'browse',
      params: { qualifier: ['releases'] }
    })
  })
})

describe('parentPathOf', () => {
  it('drops the last segment', () => {
    expect(parentPathOf('releases/net/example')).toBe('releases/net')
  })

  it('is empty for a top level directory', () => {
    expect(parentPathOf('releases')).toBe('')
  })
})

describe('childPath', () => {
  it('appends an entry name to the browsed path', () => {
    expect(childPath('releases/net', 'example')).toBe('releases/net/example')
  })

  it('creates a top level path from the root', () => {
    expect(childPath('', 'releases')).toBe('releases')
  })
})
