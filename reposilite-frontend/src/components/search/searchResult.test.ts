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
  toDate,
  normalizePage,
  normalizeFiles,
  repositoryLabel,
  rowKey,
  formatDay,
  announcement,
  indexNotice
} from './searchResult'

const gav = (overrides = {}) => ({
  groupId: 'org.apache.commons',
  artifactId: 'commons-lang3',
  version: '3.14.0',
  latestVersion: '3.17.0',
  snapshot: false,
  repositories: [{ name: 'releases', origin: 'HOSTED' }],
  fileCount: 8,
  lastModified: '2026-09-30T08:14:02Z',
  browseLink: '/browse/releases/org/apache/commons/commons-lang3/3.14.0',
  ...overrides
})

describe('toDate', () => {
  it('reads an ISO instant', () => {
    expect(toDate('2026-09-30T08:14:02Z')?.toISOString()).toBe('2026-09-30T08:14:02.000Z')
  })

  it('reads epoch seconds, which is how the backend writes an Instant', () => {
    expect(toDate(1788164042)?.toISOString()).toBe('2026-08-31T08:14:02.000Z')
  })

  it('reads nothing for a missing or broken value', () => {
    expect(toDate(undefined)).toBeNull()
    expect(toDate('soon')).toBeNull()
  })
})

describe('normalizePage', () => {
  it('keeps the fields of a result row', () => {
    const [item] = normalizePage({ items: [gav()], page: 0, size: 20, total: 1 }).items

    expect(item).toMatchObject({
      groupId: 'org.apache.commons', artifactId: 'commons-lang3', version: '3.14.0',
      latestVersion: '3.17.0', snapshot: false, fileCount: 8,
      browseLink: '/browse/releases/org/apache/commons/commons-lang3/3.14.0'
    })
    expect(item.repositories).toEqual([{ name: 'releases', origin: 'HOSTED' }])
    expect(item.lastModified).toBeInstanceOf(Date)
  })

  it('survives an answer without optional fields', () => {
    const page = normalizePage({ items: [{ groupId: 'g', artifactId: 'a', version: '1' }] })

    expect(page.items[0]).toMatchObject({ latestVersion: null, fileCount: 0, repositories: [], lastModified: null, browseLink: '' })
    expect(page).toMatchObject({ page: 0, size: 20, total: 0, totalCapped: false })
    expect(page.facets).toEqual({ repository: [], extension: [], classifier: [], origin: [], snapshot: [] })
    expect(page.indexStatus).toEqual({ state: 'READY', lagSeconds: 0, lastFullRebuild: null, repositoriesDone: null, repositoriesTotal: null })
  })

  it('reads the facets and the index status', () => {
    const page = normalizePage({
      items: [],
      totalCapped: true,
      facets: { extension: [{ value: 'jar', count: 3 }] },
      indexStatus: { state: 'BUILDING', lagSeconds: 12, lastFullRebuild: '2026-10-20T03:30:12Z', repositoriesDone: 2, repositoriesTotal: 5 }
    })

    expect(page.totalCapped).toBe(true)
    expect(page.facets.extension).toEqual([{ value: 'jar', count: 3 }])
    expect(page.indexStatus).toMatchObject({ state: 'BUILDING', lagSeconds: 12, repositoriesDone: 2, repositoriesTotal: 5 })
    expect(page.indexStatus.lastFullRebuild).toBeInstanceOf(Date)
  })
})

describe('normalizeFiles', () => {
  it('keeps the fields of a file and the source of a cached copy', () => {
    const [file] = normalizeFiles({
      items: [{
        repository: 'central-proxy', path: 'org/a/1/a-1.jar', name: 'a-1.jar', classifier: null, extension: 'jar',
        size: 100, lastModified: '2026-02-01T10:00:00Z', origin: 'MIRROR', sourceHost: 'repo.maven.apache.org',
        sha1: 'abc', browseLink: '/browse/central-proxy/org/a/1/a-1.jar'
      }],
      page: 0, size: 50, total: 1
    }).items

    expect(file).toMatchObject({
      repository: 'central-proxy', name: 'a-1.jar', classifier: '', extension: 'jar', size: 100,
      origin: 'MIRROR', sourceHost: 'repo.maven.apache.org', sha1: 'abc'
    })
  })

  it('has no source host for a hosted file', () => {
    const [file] = normalizeFiles({ items: [{ repository: 'r', path: 'p', name: 'n', origin: 'HOSTED' }] }).items

    expect(file.sourceHost).toBe('')
  })
})

describe('repositoryLabel', () => {
  it('names a hosted repository by its name', () => {
    expect(repositoryLabel({ name: 'releases', origin: 'HOSTED' })).toBe('releases')
  })

  it('says where a cached copy comes from', () => {
    expect(repositoryLabel({ name: 'central-proxy', origin: 'MIRROR' })).toBe('cached from central-proxy')
  })
})

describe('rowKey', () => {
  it('is the coordinates of the version', () => {
    expect(rowKey(gav())).toBe('org.apache.commons:commons-lang3:3.14.0')
  })
})

describe('formatDay', () => {
  it('writes the UTC day, so it does not depend on the time zone of the machine', () => {
    expect(formatDay(new Date('2026-09-30T23:59:59Z'))).toBe('2026-09-30')
  })

  it('writes nothing for no date', () => {
    expect(formatDay(null)).toBe('')
  })
})

describe('announcement', () => {
  it('counts the results', () => {
    expect(announcement({ total: 143, totalCapped: false })).toBe('143 results')
  })

  it('uses the singular for one', () => {
    expect(announcement({ total: 1, totalCapped: false })).toBe('1 result')
  })

  it('says so when there are none', () => {
    expect(announcement({ total: 0, totalCapped: false })).toBe('No results')
  })

  it('says more than 10 000 when the total is capped', () => {
    expect(announcement({ total: 10001, totalCapped: true })).toBe('More than 10 000 results')
  })
})

describe('indexNotice', () => {
  const status = (overrides) => ({ state: 'READY', lagSeconds: 0, lastFullRebuild: null, repositoriesDone: null, repositoriesTotal: null, ...overrides })

  it('says nothing while the index is ready', () => {
    expect(indexNotice(status({}))).toBeNull()
  })

  it('names the progress while the index is building', () => {
    expect(indexNotice(status({ state: 'BUILDING', repositoriesDone: 2, repositoriesTotal: 5 })))
      .toEqual({ kind: 'building', text: 'Indexing, 2 of 5 repositories. Results may be partial.' })
  })

  it('does not invent a progress when the server sends none', () => {
    expect(indexNotice(status({ state: 'BUILDING' })))
      .toEqual({ kind: 'building', text: 'Indexing. Results may be partial.' })
  })

  it('explains that nothing is indexed yet when the index is missing', () => {
    expect(indexNotice(status({ state: 'MISSING' })).kind).toBe('missing')
  })

  it('says how far a degraded index lags, in whole minutes, rounded down', () => {
    expect(indexNotice(status({ state: 'DEGRADED', lagSeconds: 400 })))
      .toEqual({ kind: 'degraded', text: 'Results may lag by 6 minutes.' })
  })

  it('does not name minutes for a degraded index that does not lag', () => {
    expect(indexNotice(status({ state: 'DEGRADED', lagSeconds: 0 })).text).toBe('Results may be out of date.')
  })
})
