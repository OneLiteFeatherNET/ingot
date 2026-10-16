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
  diffNodes,
  filterRows,
  groupSuggestions,
  hasNode,
  inheritedSourceLabel,
  isOverridden,
  repositoryNames,
  suggestNodes
} from './nodeList'

const CATALOG = [
  { node: 'ingot.admin.users', description: 'Manage users', kind: 'STATIC' },
  { node: 'ingot.admin.teams', description: 'Manage teams', kind: 'STATIC' },
  { node: 'ingot.repository.releases.read', description: 'Read releases', kind: 'REPOSITORY' },
  { node: 'ingot.repository.releases.deploy', description: 'Deploy releases', kind: 'REPOSITORY' },
  { node: 'ingot.repository.snapshots.read', description: 'Read snapshots', kind: 'REPOSITORY' },
  { node: '*', description: 'Everything', kind: 'WILDCARD' },
  { node: 'ingot.admin.*', description: 'All admin areas', kind: 'WILDCARD' }
]

describe('filterRows', () => {
  const rows = () => [
    { node: 'ingot.repository.Releases.read', allowed: true },
    { node: 'ingot.admin.users', allowed: false }
  ]

  it('keeps rows whose node contains the query, ignoring case', () => {
    expect(filterRows(rows(), 'RELEASES').map(row => row.node)).toEqual(['ingot.repository.Releases.read'])
  })

  it('keeps every row for a blank query', () => {
    expect(filterRows(rows(), '  ')).toHaveLength(2)
  })
})

describe('hasNode', () => {
  it('finds a listed node by its exact text', () => {
    expect(hasNode([{ node: 'ingot.admin.users', allowed: true }], 'ingot.admin.users')).toBe(true)
    expect(hasNode([{ node: 'ingot.admin.users', allowed: true }], 'ingot.admin')).toBe(false)
  })
})

describe('suggestNodes', () => {
  it('suggests catalog nodes containing the text', () => {
    expect(suggestNodes(CATALOG, 'releases').map(item => item.node)).toEqual([
      'ingot.repository.releases.read',
      'ingot.repository.releases.deploy'
    ])
  })

  it('leaves out nodes that are already listed', () => {
    const listed = [{ node: 'ingot.repository.releases.read', allowed: true }]

    expect(suggestNodes(CATALOG, 'releases', listed).map(item => item.node)).toEqual(['ingot.repository.releases.deploy'])
  })

  it('orders by kind', () => {
    expect(suggestNodes(CATALOG, 'ingot.admin.').map(item => item.node)).toEqual([
      'ingot.admin.users',
      'ingot.admin.teams',
      'ingot.admin.*'
    ])
  })

  it('puts prefix matches before other matches of the same kind', () => {
    const catalog = [
      { node: 'ingot.repository.xusers.read', description: '', kind: 'REPOSITORY' },
      { node: 'users.repository', description: '', kind: 'REPOSITORY' }
    ]

    expect(suggestNodes(catalog, 'users').map(item => item.node)).toEqual([
      'users.repository',
      'ingot.repository.xusers.read'
    ])
  })

  it('caps the number of suggestions', () => {
    expect(suggestNodes(CATALOG, '', [], 3)).toHaveLength(3)
  })
})

describe('groupSuggestions', () => {
  it('groups by kind under the headings Static, Repository and Wildcard', () => {
    const groups = groupSuggestions(suggestNodes(CATALOG, ''))

    expect(groups.map(group => group.label)).toEqual(['Static', 'Repository', 'Wildcard'])
    expect(groups[1].options).toHaveLength(3)
  })

  it('omits a kind without suggestions', () => {
    expect(groupSuggestions(suggestNodes(CATALOG, 'snapshots')).map(group => group.label)).toEqual(['Repository'])
  })
})

describe('repositoryNames', () => {
  it('lists each repository of the catalog once', () => {
    expect(repositoryNames(CATALOG)).toEqual(['releases', 'snapshots'])
  })
})

describe('diffNodes', () => {
  const saved = () => [
    { node: 'a', allowed: true },
    { node: 'b', allowed: true }
  ]

  it('reports no change for equal lists', () => {
    expect(diffNodes(saved(), saved()).count).toBe(0)
  })

  it('reports an added node', () => {
    const result = diffNodes(saved(), [...saved(), { node: 'c', allowed: true }])

    expect(result.added).toEqual([{ node: 'c', allowed: true }])
    expect(result.count).toBe(1)
  })

  it('reports a removed node', () => {
    const result = diffNodes(saved(), [{ node: 'a', allowed: true }])

    expect(result.removed).toEqual([{ node: 'b', allowed: true }])
  })

  it('reports a flipped allowed value', () => {
    const result = diffNodes(saved(), [{ node: 'a', allowed: false }, { node: 'b', allowed: true }])

    expect(result.changed).toEqual([{ node: 'a', from: true, to: false }])
  })
})

describe('inheritedSourceLabel', () => {
  it('names the team and the distance for an inherited team node', () => {
    expect(inheritedSourceLabel({ source: 'INHERITED_TEAM', subject: 'developers', depth: 1 }))
      .toBe('from team developers, 1 level up')
  })

  it('uses the plural for a larger distance', () => {
    expect(inheritedSourceLabel({ source: 'INHERITED_TEAM', subject: 'core', depth: 2 }))
      .toBe('from team core, 2 levels up')
  })

  it('names only the team for a direct membership', () => {
    expect(inheritedSourceLabel({ source: 'TEAM', subject: 'developers', depth: 0 })).toBe('from team developers')
  })
})

describe('isOverridden', () => {
  it('is true when the same node text is held directly', () => {
    expect(isOverridden({ node: 'x' }, [{ node: 'x', allowed: false }])).toBe(true)
  })

  it('is false otherwise', () => {
    expect(isOverridden({ node: 'x' }, [{ node: 'y', allowed: false }])).toBe(false)
  })
})
