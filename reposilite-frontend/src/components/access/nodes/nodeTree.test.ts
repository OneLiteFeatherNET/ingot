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
import { buildTree, leafCount } from './nodeTree'

const rows = () => [
  { node: 'ingot.repository.releases.read', allowed: true },
  { node: 'ingot.repository.releases.deploy', allowed: true },
  { node: 'ingot.repository.private.read', allowed: false },
  { node: 'ingot.admin.users', allowed: true }
]

const child = (item, segment) => item.children.find(candidate => candidate.segment === segment)

describe('buildTree', () => {
  it('groups nodes by their dot segments under one root', () => {
    const tree = buildTree(rows())

    expect(tree.map(item => item.segment)).toEqual(['ingot'])
    expect(tree[0].children.map(item => item.segment)).toEqual(['admin', 'repository'])
    expect(child(tree[0], 'repository').children.map(item => item.segment)).toEqual(['private', 'releases'])
  })

  it('keeps the allowed value and the full node on each leaf', () => {
    const tree = buildTree(rows())

    const leaf = child(child(child(tree[0], 'repository'), 'private'), 'read')

    expect(leaf.entry).toEqual({ node: 'ingot.repository.private.read', allowed: false })
    expect(leaf.key).toBe('ingot.repository.private.read')
  })

  it('leaves the entry of a group empty', () => {
    const tree = buildTree(rows())

    expect(tree[0].entry, 'ingot is a group').toBeNull()
  })

  it('orders siblings by segment, not by insertion', () => {
    const releases = child(child(buildTree(rows())[0], 'repository'), 'releases')

    expect(releases.children.map(item => item.segment)).toEqual(['deploy', 'read'])
  })

  it('puts a lone wildcard at the root', () => {
    const tree = buildTree([{ node: '*', allowed: true }])

    expect(tree).toHaveLength(1)
    expect(tree[0].entry).toEqual({ node: '*', allowed: true })
  })

  it('returns an empty tree for no nodes', () => {
    expect(buildTree([])).toEqual([])
  })
})

describe('leafCount', () => {
  it('counts the listed nodes below an item', () => {
    expect(leafCount(buildTree(rows())[0])).toBe(4)
  })
})
