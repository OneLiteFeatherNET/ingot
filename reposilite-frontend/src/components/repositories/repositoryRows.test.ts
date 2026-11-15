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
import { addressOf, captionOf, filterRows, rowsOf, sortRows } from './repositoryRows'

const ORIGIN = 'https://repo.example.net'
const entries = [
  { id: 'releases', type: 'HOSTED', visibility: 'PUBLIC', storageProvider: { type: 'fs' }, redeployment: true, tags: ['team-a'] },
  { id: 'snapshots', type: 'HOSTED', visibility: 'HIDDEN', storageProvider: { type: 's3' }, description: 'Nightly builds', tags: [] },
  { id: 'all', type: 'GROUP', visibility: 'PRIVATE', proxied: [{ reference: 'releases' }], tags: ['team-a'] },
  { id: 'central', visibility: 'PUBLIC', proxied: [{ reference: 'https://repo.example.net/maven' }], storageProvider: { type: 'fs' } }
]

describe('rows of the repositories table', () => {
  const rows = rowsOf(entries, { origin: ORIGIN, basePath: '/', savedIds: ['releases', 'snapshots', 'all'] })

  it('makes one row per repository of the draft, in the order of the draft', () => {
    expect(rows.map(row => row.id)).toEqual(['releases', 'snapshots', 'all', 'central'])
  })

  it('gives the type in words, and says derived for a repository without a declared type', () => {
    expect(rows.find(row => row.id === 'releases').type).toMatchObject({ label: 'Hosted', derived: false })
    expect(rows.find(row => row.id === 'central').type).toMatchObject({ label: 'Proxy', derived: true })
  })

  it('shows visibility as a word', () => {
    expect(rows.map(row => row.visibility)).toEqual(['Public', 'Hidden', 'Private', 'Public'])
  })

  it('shows the storage kind as a word, and a hyphen for a group, which has no storage of its own', () => {
    expect(rows.map(row => row.storage)).toEqual(['File system', 'S3', '-', 'File system'])
  })

  it('builds the address from the origin, the base path and the id', () => {
    expect(rows[0].address).toBe('https://repo.example.net/releases')
    expect(addressOf(ORIGIN, '/base', 'releases')).toBe('https://repo.example.net/base/releases')
    expect(addressOf(ORIGIN, '/base/', 'releases')).toBe('https://repo.example.net/base/releases')
  })

  it('marks a repository that the server has not saved as unsaved', () => {
    const fresh = rowsOf([...entries, { id: 'new-one', type: 'HOSTED' }], { origin: ORIGIN, basePath: '/', savedIds: ['releases'] })
    expect(fresh.find(row => row.id === 'new-one').unsaved).toBe(true)
    expect(fresh.find(row => row.id === 'releases').unsaved).toBe(false)
  })

  it('shows every row as saved when no list of saved ids is given', () => {
    expect(rowsOf(entries, { origin: ORIGIN, basePath: '/' }).every(row => row.unsaved === false)).toBe(true)
  })
})

describe('filter of the table', () => {
  const rows = rowsOf(entries, { origin: ORIGIN, basePath: '/' })

  it('keeps every row for an empty query', () => {
    expect(filterRows(rows, '')).toHaveLength(4)
  })

  it('matches the text in the id, the description and the tags', () => {
    expect(filterRows(rows, 'nightly').map(row => row.id)).toEqual(['snapshots'])
    expect(filterRows(rows, 'release').map(row => row.id)).toEqual(['releases'])
  })

  it('reads a tag token as a tag, as the repository search does', () => {
    expect(filterRows(rows, 'tag:team-a').map(row => row.id)).toEqual(['releases', 'all'])
  })
})

describe('sort of the table', () => {
  const rows = rowsOf(entries, { origin: ORIGIN, basePath: '/' })

  it('sorts by name ascending and descending', () => {
    expect(sortRows(rows, 'name', 'ascending').map(row => row.id)).toEqual(['all', 'central', 'releases', 'snapshots'])
    expect(sortRows(rows, 'name', 'descending').map(row => row.id)).toEqual(['snapshots', 'releases', 'central', 'all'])
  })

  it('sorts by type, visibility and storage by their words', () => {
    expect(sortRows(rows, 'type', 'ascending').map(row => row.type.label)).toEqual(['Group', 'Hosted', 'Hosted', 'Proxy'])
    expect(sortRows(rows, 'visibility', 'ascending').map(row => row.visibility)).toEqual(['Hidden', 'Private', 'Public', 'Public'])
    expect(sortRows(rows, 'storage', 'ascending').map(row => row.storage)).toEqual(['-', 'File system', 'File system', 'S3'])
  })

  it('keeps equal values in the same order, so the result does not depend on the sort run', () => {
    const ascending = sortRows(rows, 'visibility', 'ascending').map(row => row.id)
    expect(ascending.slice(2)).toEqual(['releases', 'central'])
  })

  it('does not change the rows it is given', () => {
    const before = rows.map(row => row.id)
    sortRows(rows, 'name', 'descending')
    expect(rows.map(row => row.id)).toEqual(before)
  })
})

describe('caption of the table', () => {
  it('says how many repositories are shown of how many', () => {
    expect(captionOf(2, 4)).toBe('Repositories, 2 of 4 shown')
    expect(captionOf(4, 4)).toBe('Repositories, 4 of 4 shown')
  })
})
