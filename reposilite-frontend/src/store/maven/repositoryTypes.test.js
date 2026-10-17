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
  TYPE_FILTERS,
  filterByType,
  settingsTypeBadge,
  typeBadgeOf,
  typesFromParam
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
