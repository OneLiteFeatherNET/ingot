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
  LEGACY_HINT,
  SETTING_TYPE_OPTIONS,
  TYPE_FILTERS,
  blankEntriesNotice,
  blankEntriesRemovedBy,
  filterByType,
  hiddenFormFields,
  isRemoteUrl,
  removalsFor,
  settingsTypeBadge,
  switchedEntry,
  typeBadgeOf,
  typesFromParam,
  unusedStorageNoteOf,
  violationsOf,
  warningsFor
} from './repositoryTypes'

describe('type badge of a repository in the list', () => {
  it('shows a typed repository with its effective type and no legacy hint', () => {
    expect(typeBadgeOf({ type: 'proxy', typeExplicit: true })).toEqual({
      key: 'proxy',
      label: 'Proxy',
      legacy: false,
      hint: ''
    })
  })

  it('marks an untyped repository as legacy and gives the hint that says what it does', () => {
    expect(typeBadgeOf({ type: 'mixed', typeExplicit: false })).toEqual({
      key: 'mixed',
      label: 'Mixed',
      legacy: true,
      hint: 'No type set, behaves as before. Set a type to enforce the rules.'
    })
  })

  it('uses the exact wording of the legacy hint', () => {
    expect(LEGACY_HINT).toBe('No type set, behaves as before. Set a type to enforce the rules.')
  })

  it('reads the upper case type that the settings use', () => {
    expect(typeBadgeOf({ type: 'GROUP', typeExplicit: true }).key).toBe('group')
  })

  it('treats a repository without a type field as mixed and legacy', () => {
    expect(typeBadgeOf({ id: 'old' })).toMatchObject({ key: 'mixed', legacy: true })
  })

  it('treats an unknown type as mixed rather than showing a word the list does not know', () => {
    expect(typeBadgeOf({ type: 'archive', typeExplicit: true })).toMatchObject({ key: 'mixed', label: 'Mixed' })
  })
})

describe('type badge of a repository in the settings', () => {
  it('shows the type the entry has', () => {
    expect(settingsTypeBadge({ id: 'a', type: 'HOSTED' })).toMatchObject({ key: 'hosted', legacy: false })
  })

  it('shows an entry without a type as legacy', () => {
    expect(settingsTypeBadge({ id: 'a' })).toMatchObject({ key: 'mixed', legacy: true })
  })
})

describe('type filter', () => {
  const repositories = [
    { id: 'releases', type: 'hosted', typeExplicit: true },
    { id: 'central', type: 'proxy', typeExplicit: true },
    { id: 'all', type: 'group', typeExplicit: true },
    { id: 'legacy' }
  ]

  it('offers the four effective types in a fixed order', () => {
    expect(TYPE_FILTERS.map(filter => filter.key)).toEqual(['hosted', 'proxy', 'group', 'mixed'])
    expect(TYPE_FILTERS.map(filter => filter.label)).toEqual(['Hosted', 'Proxy', 'Group', 'Mixed'])
  })

  it('keeps every repository when no type is selected', () => {
    expect(filterByType(repositories, [])).toEqual(repositories)
  })

  it('keeps the repositories of the selected types, in their order', () => {
    expect(filterByType(repositories, ['proxy', 'mixed']).map(repository => repository.id)).toEqual(['central', 'legacy'])
  })

  it('reads the selected types from the address, lower cased, without unknown or repeated values', () => {
    expect(typesFromParam(['Proxy', 'hosted', 'proxy', 'nope', ''])).toEqual(['proxy', 'hosted'])
  })

  it('accepts one type in the address as well as a repeated parameter', () => {
    expect(typesFromParam('group')).toEqual(['group'])
    expect(typesFromParam(undefined)).toEqual([])
  })
})

describe('the editor of a repository type', () => {
  const hosted = (extra = {}) => ({
    id: 'releases',
    visibility: 'PUBLIC',
    type: 'HOSTED',
    storageProvider: { type: 'fs', quota: '10GB' },
    redeployment: true,
    ...extra
  })
  const proxy = (extra = {}) => ({
    id: 'central',
    visibility: 'PUBLIC',
    type: 'PROXY',
    proxied: [{ reference: 'https://repo.example.net/maven', store: true }],
    storagePolicy: 'STRICT',
    metadataMaxAge: 300,
    ...extra
  })
  const group = (extra = {}) => ({
    id: 'all',
    visibility: 'PUBLIC',
    type: 'GROUP',
    proxied: [{ reference: 'releases' }, { reference: 'central' }],
    ...extra
  })

  describe('which fields a type shows', () => {
    it('hides the proxy and group fields from a hosted repository', () => {
      expect(hiddenFormFields('HOSTED')).toEqual(['proxied', 'storagePolicy', 'metadataMaxAge', 'resolutionCacheMaxEntries'])
    })

    it('hides the upload settings from a proxy, and keeps its cache storage, remotes and cache settings', () => {
      expect(hiddenFormFields('PROXY')).toEqual(['redeployment', 'preserveSnapshots'])
    })

    it('hides storage, the upload settings and the mirrored list from a group, whose members are edited as a list', () => {
      expect(hiddenFormFields('GROUP')).toEqual(['storageProvider', 'redeployment', 'preserveSnapshots', 'proxied', 'storagePolicy', 'metadataMaxAge', 'resolutionCacheMaxEntries'])
    })

    it('hides nothing from a repository without a type, which behaves as before', () => {
      expect(hiddenFormFields(undefined)).toEqual([])
      expect(hiddenFormFields('')).toEqual([])
    })
  })

  describe('switching the type', () => {
    it('says what a hosted repository loses when it becomes a proxy: its redeployment setting, and not its storage', () => {
      expect(removalsFor(hosted(), 'HOSTED', 'PROXY')).toEqual([
        { key: 'redeployment', label: 'Redeployment' }
      ])
    })

    it('says what a proxy loses when it becomes hosted, with the number of mirrored entries', () => {
      expect(removalsFor(proxy({ proxied: [{ reference: 'https://a.example/' }, { reference: 'https://b.example/' }] }), 'PROXY', 'HOSTED')).toEqual([
        { key: 'proxied', label: 'Mirrored repositories (2)' },
        { key: 'storagePolicy', label: 'Storage policy' },
        { key: 'metadataMaxAge', label: 'Metadata max age' }
      ])
    })

    it('says that a proxy list of addresses goes when the type becomes a group, since members are repository ids', () => {
      expect(removalsFor(proxy(), 'PROXY', 'GROUP')).toEqual([
        { key: 'proxied', label: 'Mirrored repositories' },
        { key: 'storagePolicy', label: 'Storage policy' },
        { key: 'metadataMaxAge', label: 'Metadata max age' }
      ])
    })

    it('names the credentials of the mirrored repositories that a switch removes, on a line of their own', () => {
      const withCredentials = proxy({
        proxied: [
          { reference: 'https://a.example/', authorization: { method: 'BASIC', login: 'alpha', password: 'alpha-secret' } },
          { reference: 'https://b.example/' }
        ]
      })

      expect(removalsFor(withCredentials, 'PROXY', 'GROUP')).toEqual([
        { key: 'proxied', label: 'Mirrored repositories (2)' },
        { key: 'credentials', label: 'Credentials of mirrored repositories (1)' },
        { key: 'storagePolicy', label: 'Storage policy' },
        { key: 'metadataMaxAge', label: 'Metadata max age' }
      ])
    })

    it('names no credentials when the mirrored repositories carry none', () => {
      expect(removalsFor(proxy(), 'PROXY', 'GROUP').map(removal => removal.key)).not.toContain('credentials')
    })

    it('keeps the members of a group when the group stays a group', () => {
      expect(removalsFor(group(), 'GROUP', 'GROUP')).toEqual([])
    })

    it('removes nothing when a repository gets no type, which keeps its settings as they are', () => {
      expect(removalsFor(hosted(), 'HOSTED', '')).toEqual([])
    })

    it('does not list a setting that is empty, since there is nothing to lose', () => {
      expect(removalsFor(hosted({ redeployment: false }), 'HOSTED', 'PROXY')).toEqual([])
    })

    it('never lists storage as removed, because a switch keeps it', () => {
      expect(removalsFor(hosted(), 'HOSTED', 'GROUP').map(removal => removal.key)).not.toContain('storageProvider')
      expect(removalsFor(proxy({ storageProvider: { type: 's3' } }), 'PROXY', 'GROUP').map(removal => removal.key)).not.toContain('storageProvider')
    })

    it('writes the entry the new type takes: the storage stays, and the settings that do not apply to a proxy go', () => {
      expect(switchedEntry(hosted(), 'PROXY')).toEqual({
        id: 'releases',
        visibility: 'PUBLIC',
        type: 'PROXY',
        storageProvider: { type: 'fs', quota: '10GB' }
      })
    })

    it('drops the mirrored entries of a proxy that becomes a group, because they are addresses', () => {
      expect(switchedEntry(proxy(), 'GROUP')).toEqual({ id: 'central', visibility: 'PUBLIC', type: 'GROUP' })
    })

    it('keeps the members when a group becomes a group, and keeps its unused storage rather than deleting it', () => {
      expect(switchedEntry(group({ storageProvider: { type: 'fs' } }), 'GROUP')).toEqual({
        id: 'all',
        visibility: 'PUBLIC',
        type: 'GROUP',
        storageProvider: { type: 'fs' },
        proxied: [{ reference: 'releases' }, { reference: 'central' }]
      })
    })

    it('keeps the cache storage of a proxy that becomes a group, so it is unused but not lost', () => {
      const entry = switchedEntry(proxy({ storageProvider: { type: 's3', bucketName: 'onelitefeather-proxy' } }), 'GROUP')
      expect(entry.storageProvider).toEqual({ type: 's3', bucketName: 'onelitefeather-proxy' })
      expect(entry.proxied).toBeUndefined()
    })

    it('warns that the cache storage of a proxy stays configured but unused when it becomes a group', () => {
      expect(warningsFor(proxy({ storageProvider: { type: 'fs' } }), 'PROXY', 'GROUP')).toEqual([
        'The cache storage stays configured, but a group does not use it.'
      ])
    })

    it('warns about the storage of a hosted repository that becomes a group, in its own words', () => {
      expect(warningsFor(hosted(), 'HOSTED', 'GROUP')).toEqual([
        'The storage stays configured, but a group does not use it.'
      ])
    })

    it('warns about nothing when there is no storage, or when the type is not a group', () => {
      expect(warningsFor(proxy(), 'PROXY', 'GROUP')).toEqual([])
      expect(warningsFor(proxy({ storageProvider: { type: 'fs' } }), 'PROXY', 'HOSTED')).toEqual([])
    })

    it('notes under a group that still has storage that the storage is not used', () => {
      expect(unusedStorageNoteOf(group({ storageProvider: { type: 'fs' } }))).toBe('The storage stays configured, but a group does not use it.')
      expect(unusedStorageNoteOf(group())).toBe('')
      expect(unusedStorageNoteOf(proxy({ storageProvider: { type: 'fs' } }))).toBe('')
    })

    it('removes the type when the repository goes back to none, and keeps the rest', () => {
      expect(switchedEntry(proxy(), '')).toEqual({
        id: 'central',
        visibility: 'PUBLIC',
        proxied: [{ reference: 'https://repo.example.net/maven', store: true }],
        storagePolicy: 'STRICT',
        metadataMaxAge: 300
      })
    })

    it('does not change the entry it is given', () => {
      const entry = hosted()
      switchedEntry(entry, 'PROXY')
      expect(entry.storageProvider).toEqual({ type: 'fs', quota: '10GB' })
    })
  })

  describe('the rules a repository of its type must keep', () => {
    it('finds nothing wrong with a valid proxy', () => {
      expect(violationsOf(proxy())).toEqual([])
    })

    it('refuses a hosted repository that mirrors other repositories', () => {
      expect(violationsOf(hosted({ proxied: [{ reference: 'releases' }] }))).toEqual([
        'A hosted repository cannot mirror other repositories. Remove the mirrored repositories, or choose Proxy or Group.'
      ])
    })

    it('refuses a proxy without remote repositories', () => {
      expect(violationsOf(proxy({ proxied: [] }))).toEqual(['A proxy needs at least one remote repository.'])
    })

    it('refuses a proxy whose mirror is a repository id rather than an address', () => {
      expect(violationsOf(proxy({ proxied: [{ reference: 'releases' }] }))).toEqual([
        '"releases" is not a remote address. A proxy fetches from http or https addresses.'
      ])
    })

    it('refuses a group without members', () => {
      expect(violationsOf(group({ proxied: [] }))).toEqual(['A group needs at least one repository to list.'])
    })

    it('refuses a group member that is an address', () => {
      expect(violationsOf(group({ proxied: [{ reference: 'https://a.example/' }] }))).toEqual([
        '"https://a.example/" is an address. A group lists repository ids.'
      ])
    })

    it('refuses a group that lists itself', () => {
      expect(violationsOf(group({ proxied: [{ reference: 'all' }] }), { otherIds: ['all', 'releases'] })).toEqual([
        'A group cannot list itself.'
      ])
    })

    it('refuses a group member that is not a repository of this instance', () => {
      expect(violationsOf(group(), { otherIds: ['releases'] })).toEqual([
        '"central" is not a repository of this instance.'
      ])
    })

    it('finds nothing wrong with a repository that has no type, whatever it holds', () => {
      expect(violationsOf({ id: 'old', proxied: [{ reference: 'releases' }] })).toEqual([])
    })
  })

  it('offers the choices of the type, starting with the one that keeps the repository as it was', () => {
    expect(SETTING_TYPE_OPTIONS.map(option => option.value)).toEqual(['', 'HOSTED', 'PROXY', 'GROUP'])
    expect(SETTING_TYPE_OPTIONS[0].label).toBe('Not set, as before')
  })

  it('reads an address as a remote, and nothing else', () => {
    expect(isRemoteUrl('https://repo.example.net/maven')).toBe(true)
    expect(isRemoteUrl('releases')).toBe(false)
  })
})

describe('blank entries of a repository', () => {
  const withBlanks = (type, proxied) => ({ id: 'all', visibility: 'PUBLIC', type, proxied })

  it('removes the blank entries of a repository that gets a typed group, and keeps the order of the others', () => {
    const entry = withBlanks(undefined, [{ reference: 'releases' }, { reference: '' }, { reference: '   ' }, { reference: 'snapshots' }])

    expect(switchedEntry(entry, 'GROUP')).toEqual({
      id: 'all',
      visibility: 'PUBLIC',
      type: 'GROUP',
      proxied: [{ reference: 'releases' }, { reference: 'snapshots' }]
    })
  })

  it('removes the blank entries of a repository that gets a typed proxy', () => {
    const entry = withBlanks(undefined, [{ reference: 'https://repo.example.net/maven' }, { reference: '' }])

    expect(switchedEntry(entry, 'PROXY').proxied).toEqual([{ reference: 'https://repo.example.net/maven' }])
  })

  it('treats a lone slash as blank, as the server does', () => {
    const entry = withBlanks('GROUP', [{ reference: 'releases' }, { reference: '/' }])

    expect(blankEntriesRemovedBy(entry, 'GROUP')).toBe(1)
  })

  it('keeps the blank entries of a repository that gets no type, so untyped data is not rewritten', () => {
    const entry = withBlanks('GROUP', [{ reference: 'releases' }, { reference: '' }])

    expect(switchedEntry(entry, '').proxied).toEqual([{ reference: 'releases' }, { reference: '' }])
  })

  it('counts the blank entries a typed target would drop, and none for an untyped target', () => {
    const entry = withBlanks(undefined, [{ reference: '' }, { reference: 'releases' }, { reference: ' ' }])

    expect(blankEntriesRemovedBy(entry, 'GROUP')).toBe(2)
    expect(blankEntriesRemovedBy(entry, 'PROXY')).toBe(2)
    expect(blankEntriesRemovedBy(entry, '')).toBe(0)
  })

  it('counts nothing for an entry without a mirrored list', () => {
    expect(blankEntriesRemovedBy({ id: 'releases', type: 'HOSTED' }, 'GROUP')).toBe(0)
  })

  it('says how many empty entries a switch removes, in the singular and the plural', () => {
    expect(blankEntriesNotice(0)).toBe('')
    expect(blankEntriesNotice(1)).toBe('1 empty entry will be removed.')
    expect(blankEntriesNotice(2)).toBe('2 empty entries will be removed.')
  })

  it('does not count a blank entry as a violation of a group, because the save drops it', () => {
    expect(violationsOf(withBlanks('GROUP', [{ reference: 'releases' }, { reference: '' }]), { otherIds: ['releases'] })).toEqual([])
  })

  it('does not count a blank entry as a violation of a proxy', () => {
    expect(violationsOf(withBlanks('PROXY', [{ reference: 'https://repo.example.net/maven' }, { reference: '' }]))).toEqual([])
  })
})
