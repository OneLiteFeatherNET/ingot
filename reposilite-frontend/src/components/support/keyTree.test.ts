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

import { describe, expect, it } from 'vitest'
import { buildKeyTree, filterTree, groupLabels, splitMatch, type GroupNode, type LeafNode, type TreeNode } from './keyTree'

// Pure grouping of key/value rows into a tree: no component, no router and no clock.
const leaf = (key: string): LeafNode => ({ kind: 'leaf', key, value: 'v' })
const group = (label: string, count: number, children: TreeNode[]): GroupNode => ({ kind: 'group', label, count, children })
const rows = (keys: string[]): [string, string][] => keys.map((key) => [key, 'v'])

describe('buildKeyTree with the underscore separator', () => {
  it('groups two or more environment variables that share a prefix', () => {
    const tree = buildKeyTree(rows(['INGOT_TOKEN_A', 'INGOT_TOKEN_B']), 'underscore')

    expect(tree, 'two keys with the prefix INGOT_TOKEN form one group').toEqual([
      group('INGOT_TOKEN', 2, [leaf('INGOT_TOKEN_A'), leaf('INGOT_TOKEN_B')]),
    ])
  })

  it('keeps a key that shares no prefix with another key as a leaf', () => {
    const tree = buildKeyTree(rows(['PUID', 'JAVA_HOME']), 'underscore')

    expect(tree, 'a single key stays a leaf and forms no group').toEqual([leaf('JAVA_HOME'), leaf('PUID')])
  })

  it('leaves a single key of a prefix outside the group of its siblings', () => {
    const tree = buildKeyTree(rows(['INGOT_A', 'INGOT_B', 'PUID']), 'underscore')

    expect(tree, 'the lone key PUID is a leaf beside the group INGOT').toEqual([
      group('INGOT', 2, [leaf('INGOT_A'), leaf('INGOT_B')]),
      leaf('PUID'),
    ])
  })

  it('collapses a chain of single-child groups into one node', () => {
    // given: three service variables that share a long prefix
    const keys = [
      'INGOT_DASHBOARD_PORT_8080_TCP_ADDR',
      'INGOT_DASHBOARD_PORT_8080_TCP_PORT',
      'INGOT_DASHBOARD_PORT_8080_TCP_PROTO',
    ]

    // when: the keys are grouped
    const tree = buildKeyTree(rows(keys), 'underscore')

    // then: one node holds them, not one node per segment
    expect(tree, 'the chain INGOT, DASHBOARD, PORT, 8080, TCP is one node').toEqual([
      group('INGOT_DASHBOARD_PORT_8080_TCP', 3, keys.map(leaf)),
    ])
  })

  it('shows a key equal to the prefix as the first leaf of its group', () => {
    const tree = buildKeyTree(rows(['A_B', 'A_B_C', 'A_B_D']), 'underscore')

    expect(tree, 'the bare key A_B sits in the group A_B beside its children').toEqual([
      group('A_B', 3, [leaf('A_B'), leaf('A_B_C'), leaf('A_B_D')]),
    ])
  })

  it('sorts the leaves and the groups by key', () => {
    const tree = buildKeyTree(rows(['Z_ONE', 'M', 'A_2', 'A_1']), 'underscore')

    expect(tree, 'the groups and leaves come in key order, and the children too').toEqual([
      group('A', 2, [leaf('A_1'), leaf('A_2')]),
      leaf('M'),
      leaf('Z_ONE'),
    ])
  })
})

describe('buildKeyTree with the dot separator', () => {
  it('splits configuration keys on the dot and leaves underscores alone', () => {
    const tree = buildKeyTree(rows(['storage.type', 'storage.secretKey', 'a_b.c']), 'dot')

    expect(tree, 'the dot separates the storage keys, the underscore is part of a_b').toEqual([
      leaf('a_b.c'),
      group('storage', 2, [leaf('storage.secretKey'), leaf('storage.type')]),
    ])
  })
})

describe('buildKeyTree with the camel case strategy', () => {
  it('groups camel case keys by their first word, with the bare word as the first leaf', () => {
    // given: the database settings of the local configuration
    const keys = ['database', 'databaseConnectionTimeout', 'databaseKeepalive', 'databaseMaxLifetime', 'databaseThreadPool']

    // when / then: the five keys form one group, the bare key among its leaves
    expect(buildKeyTree(rows(keys), 'camel'), 'the database group has five keys').toEqual([
      group('database', 5, keys.map(leaf)),
    ])
  })

  it('does not merge two keys that only share a letter, because the word boundary is a capital', () => {
    // basePath and bypassExternalCache both start with "b", but their first words differ
    const tree = buildKeyTree(rows(['basePath', 'bypassExternalCache']), 'camel')

    expect(tree, 'neither key forms a group with the other').toEqual([leaf('basePath'), leaf('bypassExternalCache')])
  })

  it('keeps a single camel case key as a leaf', () => {
    expect(buildKeyTree(rows(['cachedLogSize']), 'camel')).toEqual([leaf('cachedLogSize')])
  })
})

describe('buildKeyTree with a custom split function', () => {
  it('groups by the segments the function returns', () => {
    // given: a split on the colon, which the built-in strategies do not know
    const byColon = (key: string) => key.split(':')

    // when / then
    expect(buildKeyTree(rows(['cache:size', 'cache:ttl']), byColon), 'the colon groups the cache keys').toEqual([
      group('cache', 2, [leaf('cache:size'), leaf('cache:ttl')]),
    ])
  })
})

describe('groupLabels', () => {
  it('lists the label of every group in the tree, including the nested ones', () => {
    const tree = buildKeyTree(rows(['A_B_1', 'A_B_2', 'A_C_1', 'A_C_2']), 'underscore')

    expect(groupLabels(tree), 'the outer group A and its two inner groups').toEqual(['A', 'A_B', 'A_C'])
  })
})

describe('filterTree', () => {
  const tree = buildKeyTree(rows(['INGOT_TOKEN_A', 'INGOT_TOKEN_B', 'PUID']), 'underscore')

  it('keeps only the leaves that match the key or the value, without regard to case', () => {
    expect(filterTree(tree, 'token_b'), 'the match is found by key, in any case').toEqual([
      group('INGOT_TOKEN', 1, [leaf('INGOT_TOKEN_B')]),
    ])
  })

  it('matches a value as well as a key', () => {
    const valued: [string, string][] = [['PUID', '1000'], ['GID', '2000']]

    expect(filterTree(buildKeyTree(valued, 'underscore'), '2000'), 'the value 2000 selects GID').toEqual([
      { kind: 'leaf', key: 'GID', value: '2000' },
    ])
  })

  it('returns no nodes when nothing matches', () => {
    expect(filterTree(tree, 'nothing-like-this'), 'no leaf matches').toEqual([])
  })

  it('returns the tree unchanged for an empty query', () => {
    expect(filterTree(tree, '   '), 'a blank query filters nothing').toEqual(tree)
  })
})

describe('splitMatch', () => {
  it('splits a text into parts, marking the part that matches the query', () => {
    expect(splitMatch('INGOT_TOKEN', 'token'), 'the match TOKEN is marked, the rest is not').toEqual([
      { text: 'INGOT_', hit: false },
      { text: 'TOKEN', hit: true },
    ])
  })

  it('returns the whole text unmarked for an empty query', () => {
    expect(splitMatch('PUID', '')).toEqual([{ text: 'PUID', hit: false }])
  })
})
