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

// Groups key/value rows into a tree by their prefix, for the long lists of the system information page. Pure: no
// component, no clock and no logging. Values are only read for the filter; they are never changed or unmasked.

export type KeyValue = readonly [string, string]

// How a key is cut into segments. A prefix group is formed where two or more keys share leading segments.
export type KeySplit = 'underscore' | 'dot' | 'camel' | ((key: string) => string[])

export interface LeafNode {
  kind: 'leaf'
  key: string
  value: string
}

export interface GroupNode {
  kind: 'group'
  // The key prefix the group shares, as written in the keys, such as INGOT_DASHBOARD_PORT_8080_TCP.
  label: string
  // The keys below the group.
  count: number
  children: TreeNode[]
}

export type TreeNode = LeafNode | GroupNode

// Groups start collapsed when there are more keys than this. At or below it, every group starts open.
export const OPEN_GROUPS_UP_TO = 20

// A camel case word: an upper case run that is not followed by a lower case letter (HTTP in HTTPServer), or a capital
// with the lower case letters and digits that follow it. A lower case run starts a new word after its capital.
const CAMEL_WORD = /[A-Z]+(?![a-z])|[A-Z]?[a-z0-9]+/g

const segmentsOf = (key: string, split: KeySplit): string[] => {
  if (typeof split === 'function') return split(key)
  if (split === 'dot') return key.split('.')
  if (split === 'camel') return key.match(CAMEL_WORD) ?? []
  return key.split('_')
}

// The offset in the key where each segment ends, so a group label can be cut from the key as it was written. An empty
// segment, as left by a leading or a doubled separator, sits after its separator, so it ends one character further on.
// Otherwise a key that starts with a separator would name its group with an empty label.
const endsOf = (key: string, segments: readonly string[]): number[] => {
  let position = 0
  return segments.map((segment) => {
    if (segment === '') {
      position = Math.min(position + 1, key.length)
      return position
    }
    const at = key.indexOf(segment, position)
    position = at === -1 ? position : at + segment.length
    return position
  })
}

interface Entry {
  key: string
  value: string
  ends: number[]
}

interface Trie {
  depth: number
  total: number
  own: Entry[]
  children: Map<string, Trie>
}

const newTrie = (depth: number): Trie => ({ depth, total: 0, own: [], children: new Map() })

const insert = (root: Trie, entry: Entry, segments: readonly string[]) => {
  let node = root
  node.total += 1
  segments.forEach((segment, index) => {
    let next = node.children.get(segment)
    if (!next) {
      next = newTrie(index + 1)
      node.children.set(segment, next)
    }
    next.total += 1
    node = next
  })
  node.own.push(entry)
}

const anyEntry = (node: Trie): Entry => node.own[0] ?? anyEntry(node.children.values().next().value as Trie)

const leafOf = (entry: Entry): LeafNode => ({ kind: 'leaf', key: entry.key, value: entry.value })

const sortByKey = (nodes: TreeNode[]): TreeNode[] => nodes.sort((left, right) => sortKeyOf(left).localeCompare(sortKeyOf(right)))
const sortKeyOf = (node: TreeNode) => (node.kind === 'leaf' ? node.key : node.label)

const childrenOf = (node: Trie): TreeNode[] => {
  const nodes: TreeNode[] = node.own.map(leafOf)
  for (const child of node.children.values()) {
    nodes.push(nodeFor(child))
  }
  return sortByKey(nodes)
}

// A child with one key is a leaf. Otherwise it is a group, and a chain of single-child groups collapses into one node.
const nodeFor = (child: Trie): TreeNode => {
  if (child.total === 1) return leafOf(anyEntry(child))
  let node = child
  while (node.own.length === 0 && node.children.size === 1) {
    node = node.children.values().next().value as Trie
  }
  const entry = anyEntry(node)
  return {
    kind: 'group',
    label: entry.key.slice(0, entry.ends[node.depth - 1]),
    count: node.total,
    children: childrenOf(node),
  }
}

// The tree of the rows, grouped by the prefixes of the keys, with the nodes of every level in key order.
export const buildKeyTree = (rows: readonly KeyValue[], split: KeySplit): TreeNode[] => {
  const root = newTrie(0)
  for (const [key, value] of rows) {
    const segments = segmentsOf(key, split)
    insert(root, { key, value, ends: endsOf(key, segments) }, segments)
  }
  return childrenOf(root)
}

// The labels of every group in the tree, the outer groups first. Expand all and Collapse all act on these.
export const groupLabels = (nodes: readonly TreeNode[]): string[] =>
  nodes.flatMap((node) => (node.kind === 'group' ? [node.label, ...groupLabels(node.children)] : []))

const countLeaves = (nodes: readonly TreeNode[]): number =>
  nodes.reduce((sum, node) => sum + (node.kind === 'leaf' ? 1 : countLeaves(node.children)), 0)

// Whether a key or a value contains the query, without regard to case. A blank query matches nothing here.
const matches = (node: LeafNode, needle: string) =>
  node.key.toLowerCase().includes(needle) || node.value.toLowerCase().includes(needle)

const prune = (nodes: readonly TreeNode[], needle: string): TreeNode[] => nodes.flatMap((node): TreeNode[] => {
  if (node.kind === 'leaf') return matches(node, needle) ? [node] : []
  const children = prune(node.children, needle)
  return children.length === 0 ? [] : [{ kind: 'group', label: node.label, count: countLeaves(children), children }]
})

// The tree narrowed to the leaves that match the query, with only the groups on the path to them. A blank query keeps
// the tree as it is. The count of a group is the number of matching keys below it.
export const filterTree = (nodes: TreeNode[], query: string): TreeNode[] => {
  const needle = query.trim().toLowerCase()
  return needle === '' ? nodes : prune(nodes, needle)
}

export interface TextPart {
  text: string
  hit: boolean
}

// The text cut where the query matches, so the matching parts can be marked. Case is ignored.
export const splitMatch = (text: string, query: string): TextPart[] => {
  const needle = query.trim().toLowerCase()
  if (needle === '') return [{ text, hit: false }]
  const lower = text.toLowerCase()
  const parts: TextPart[] = []
  let position = 0
  let at = lower.indexOf(needle, position)
  while (at !== -1) {
    if (at > position) parts.push({ text: text.slice(position, at), hit: false })
    parts.push({ text: text.slice(at, at + needle.length), hit: true })
    position = at + needle.length
    at = lower.indexOf(needle, position)
  }
  if (position < text.length) parts.push({ text: text.slice(position), hit: false })
  return parts
}

// The rows of a nested configuration, with the keys of nested objects joined by dots and the items of arrays numbered by
// their index. A domain holding a scalar is one row. An empty object or array keeps its key, shown as its brackets.
const flattenValue = (key: string, value: unknown): [string, string][] => {
  if (value !== null && typeof value === 'object') {
    const entries = Object.entries(value)
    if (entries.length === 0) return [[key, Array.isArray(value) ? '[]' : '{}']]
    return entries.flatMap(([child, item]) => flattenValue(`${key}.${child}`, item))
  }
  return [[key, typeof value === 'string' ? value : JSON.stringify(value)]]
}

export const flattenNested = (configuration: Readonly<Record<string, unknown>>): [string, string][] =>
  Object.entries(configuration).flatMap(([domain, value]) => flattenValue(domain, value))
