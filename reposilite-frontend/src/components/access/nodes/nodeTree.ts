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

/*
 * Groups a flat list of { node, allowed } into a tree by dot segments, so that
 * ingot.repository.releases.read sits under ingot > repository > releases. A tree item is
 * { key, segment, entry, children }: `key` is the path up to and including the segment (for a
 * leaf that is the full node), `entry` is the { node, allowed } entry when a listed node ends
 * here and null for a group, and `children` is ordered by segment. Pure.
 */

/** One listed node as the tree receives it. */
export interface TreeEntry {
  node: string
  allowed: boolean
}

/** One item of the tree: a group or a listed node, with its children ordered by segment. */
export interface TreeItem<E extends TreeEntry = TreeEntry> {
  key: string
  segment: string
  entry: E | null
  children: TreeItem<E>[]
}

const buildTree = <E extends TreeEntry>(entries?: E[] | null): TreeItem<E>[] => {
  const roots: TreeItem<E>[] = []
  for (const entry of entries ?? []) {
    let level = roots
    const segments = entry.node.split('.')
    segments.forEach((segment, index) => {
      let item = level.find(candidate => candidate.segment === segment)
      if (!item) {
        item = { key: segments.slice(0, index + 1).join('.'), segment, entry: null, children: [] }
        level.push(item)
      }
      if (index === segments.length - 1) item.entry = entry
      level = item.children
    })
  }
  // A group with a single child and no node of its own adds no level: its label joins the child's,
  // so ingot.admin.* with no siblings is one row, and a level only appears where the nodes branch.
  const collapse = (items: TreeItem<E>[]): TreeItem<E>[] =>
    items.map((item) => {
      let current = item
      while (!current.entry && current.children.length === 1) {
        const [child] = current.children
        current = {
          key: child.key,
          segment: `${current.segment}.${child.segment}`,
          entry: child.entry,
          children: child.children
        }
      }
      current.children = collapse(current.children)
      return current
    })
  const sort = (items: TreeItem<E>[]): TreeItem<E>[] => {
    items.sort((left, right) => left.segment.localeCompare(right.segment))
    items.forEach(item => sort(item.children))
    return items
  }
  return sort(collapse(roots))
}

/** The listed nodes at or below a tree item, in tree order. */
const entriesBelow = <E extends TreeEntry>(item: TreeItem<E>): E[] => [
  ...(item.entry ? [item.entry] : []),
  ...item.children.flatMap(child => entriesBelow(child))
]

/** How many listed nodes sit at or below a tree item. */
const leafCount = (item: TreeItem<TreeEntry>): number => entriesBelow(item).length

/** True when the node is listed at or below a tree item. */
const containsNode = (item: TreeItem<TreeEntry>, node: string): boolean =>
  entriesBelow(item).some(entry => entry.node === node)

/**
 * The state of the nodes below a tree item: 'allow' when all of them allow, 'deny' when all of them
 * deny, 'mixed' otherwise.
 */
const stateBelow = (item: TreeItem<TreeEntry>): 'allow' | 'deny' | 'mixed' => {
  const entries = entriesBelow(item)
  const allowed = entries.filter(entry => entry.allowed).length
  if (allowed === entries.length) return 'allow'
  return allowed === 0 ? 'deny' : 'mixed'
}

/** A wildcard node such as ingot.admin.* or *, which covers everything below its prefix. */
const isWildcard = (node: string): boolean => node === '*' || node.endsWith('.*')

export { buildTree, containsNode, entriesBelow, isWildcard, leafCount, stateBelow }
