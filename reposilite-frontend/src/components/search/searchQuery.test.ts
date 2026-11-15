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
import {
  DEFAULT_QUERY,
  parseSearchQuery,
  toRouteQuery,
  toRequestQuery,
  changeQuery,
  resetFilters,
  activeFilters,
  dateInputValue,
  instantFromDateInput,
  lastPage
} from './searchQuery'

describe('parseSearchQuery', () => {
  it('reads an empty address as the defaults', () => {
    expect(parseSearchQuery({})).toEqual(DEFAULT_QUERY)
  })

  it('reads the text and the single value filters', () => {
    const state = parseSearchQuery({ q: 'commons', groupId: 'org.apache', artifactId: 'lang', version: '3.14.0' })

    expect(state).toMatchObject({ q: 'commons', groupId: 'org.apache', artifactId: 'lang', version: '3.14.0' })
  })

  it('reads a repeated parameter as a list and a single one as a list of one', () => {
    const state = parseSearchQuery({ repository: ['releases', 'snapshots'], extension: 'jar' })

    expect(state.repository).toEqual(['releases', 'snapshots'])
    expect(state.extension).toEqual(['jar'])
  })

  it('counts a repeated value once and drops blank values', () => {
    const state = parseSearchQuery({ repository: ['releases', 'releases', ' ', ''] })

    expect(state.repository).toEqual(['releases'])
  })

  it('lower-cases extensions and keeps the case of classifiers', () => {
    const state = parseSearchQuery({ extension: 'JAR', classifier: 'Sources' })

    expect(state.extension).toEqual(['jar'])
    expect(state.classifier).toEqual(['Sources'])
  })

  it('falls back to the default for a value outside the allowed ones', () => {
    const state = parseSearchQuery({ snapshot: 'maybe', origin: 'cloud', sort: 'colour', order: 'sideways' })

    expect(state).toMatchObject({ snapshot: 'all', origin: 'all', sort: 'groupId', order: 'asc' })
  })

  it('reads the allowed enum values', () => {
    const state = parseSearchQuery({ snapshot: 'true', origin: 'mirror', sort: 'modified', order: 'desc' })

    expect(state).toMatchObject({ snapshot: 'true', origin: 'mirror', sort: 'modified', order: 'desc' })
  })

  it('reads the page and the size as numbers', () => {
    expect(parseSearchQuery({ page: '3', size: '50' })).toMatchObject({ page: 3, size: 50 })
  })

  it('falls back to the first page and the default size for nonsense', () => {
    expect(parseSearchQuery({ page: '-2', size: 'many' })).toMatchObject({ page: 0, size: 20 })
  })

  it('caps the size at 100', () => {
    expect(parseSearchQuery({ size: '500' }).size).toBe(100)
  })

  it('keeps a byte count and drops one that is not a whole number', () => {
    const state = parseSearchQuery({ sizeMin: '1024', sizeMax: '12.5' })

    expect(state.sizeMin).toBe('1024')
    expect(state.sizeMax).toBe('')
  })

  it('keeps an instant and drops a date that does not parse', () => {
    const state = parseSearchQuery({ modifiedAfter: '2026-01-01T00:00:00Z', modifiedBefore: 'yesterday' })

    expect(state.modifiedAfter).toBe('2026-01-01T00:00:00Z')
    expect(state.modifiedBefore).toBe('')
  })

  it('counts a repeated single value parameter as absent', () => {
    expect(parseSearchQuery({ q: ['a', 'b'] }).q).toBe('')
  })
})

describe('toRouteQuery', () => {
  it('writes nothing for the defaults', () => {
    expect(toRouteQuery(DEFAULT_QUERY)).toEqual({})
  })

  it('writes only what differs from the defaults', () => {
    const state = { ...DEFAULT_QUERY, q: 'commons', origin: 'mirror', page: 2 }

    expect(toRouteQuery(state)).toEqual({ q: 'commons', origin: 'mirror', page: '2' })
  })

  it('repeats a list parameter', () => {
    const state = { ...DEFAULT_QUERY, repository: ['releases', 'snapshots'] }

    expect(toRouteQuery(state)).toEqual({ repository: ['releases', 'snapshots'] })
  })

  it('round-trips every parameter through the address', () => {
    const state = {
      q: 'commons lang', groupId: 'org.apache', artifactId: 'lang', version: '3.14.0',
      repository: ['releases'], extension: ['jar', 'pom'], classifier: ['-', 'sources'],
      snapshot: 'false', origin: 'hosted', modifiedAfter: '2026-01-01T00:00:00Z',
      modifiedBefore: '2026-06-01T00:00:00Z', sizeMin: '10', sizeMax: '9000',
      sort: 'size', order: 'desc', page: 4, size: 50
    }

    expect(parseSearchQuery(toRouteQuery(state))).toEqual(state)
  })
})

describe('toRequestQuery', () => {
  it('is empty for the defaults', () => {
    expect(toRequestQuery(DEFAULT_QUERY)).toBe('')
  })

  it('spells a repeated parameter once per value', () => {
    const state = { ...DEFAULT_QUERY, extension: ['jar', 'pom'] }

    expect(toRequestQuery(state)).toBe('extension=jar&extension=pom')
  })

  it('encodes the text', () => {
    expect(toRequestQuery({ ...DEFAULT_QUERY, q: 'a b&c' })).toBe('q=a+b%26c')
  })
})

describe('changeQuery', () => {
  it('sets the page back to the first when a filter changes', () => {
    const state = { ...DEFAULT_QUERY, page: 3 }

    expect(changeQuery(state, { origin: 'mirror' }).page).toBe(0)
  })

  it('keeps the page the caller asks for', () => {
    expect(changeQuery(DEFAULT_QUERY, { page: 2 }).page).toBe(2)
  })

  it('does not change the state it is given', () => {
    const state = { ...DEFAULT_QUERY }

    changeQuery(state, { q: 'x' })

    expect(state.q).toBe('')
  })
})

describe('resetFilters', () => {
  it('clears every filter and keeps the text and the sort', () => {
    const state = {
      ...DEFAULT_QUERY, q: 'commons', groupId: 'org', repository: ['releases'], origin: 'mirror',
      sizeMin: '5', sort: 'size', order: 'desc', page: 4
    }

    expect(resetFilters(state)).toEqual({ ...DEFAULT_QUERY, q: 'commons', sort: 'size', order: 'desc' })
  })
})

describe('activeFilters', () => {
  it('lists nothing for the defaults', () => {
    expect(activeFilters(DEFAULT_QUERY)).toEqual([])
  })

  it('names each filter that is set, in a form a person can read', () => {
    const state = { ...DEFAULT_QUERY, repository: ['releases'], origin: 'mirror', snapshot: 'false', sizeMin: '10' }

    expect(activeFilters(state)).toEqual([
      'repository releases',
      'releases only',
      'cached copies only',
      'size at least 10 bytes'
    ])
  })
})

describe('date inputs', () => {
  it('shows the day of an instant', () => {
    expect(dateInputValue('2026-03-04T00:00:00Z')).toBe('2026-03-04')
  })

  it('shows nothing for no instant', () => {
    expect(dateInputValue('')).toBe('')
  })

  it('turns a picked day into the UTC midnight instant', () => {
    expect(instantFromDateInput('2026-03-04')).toBe('2026-03-04T00:00:00Z')
  })

  it('turns a cleared or broken day into no instant', () => {
    expect(instantFromDateInput('')).toBe('')
    expect(instantFromDateInput('04.03.2026')).toBe('')
  })
})

describe('lastPage', () => {
  it('is the last page that holds results', () => {
    expect(lastPage(143, 20)).toBe(7)
  })

  it('is the first page when there are no results', () => {
    expect(lastPage(0, 20)).toBe(0)
  })

  it('stops at the result window of 10 000 entries', () => {
    expect(lastPage(10001, 20)).toBe(499)
    expect(lastPage(10001, 100)).toBe(99)
  })
})
