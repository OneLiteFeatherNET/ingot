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

const buildTree = (entries) => {
  const roots = []
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
  const sort = (items) => {
    items.sort((left, right) => left.segment.localeCompare(right.segment))
    items.forEach(item => sort(item.children))
    return items
  }
  return sort(roots)
}

/** How many listed nodes sit at or below a tree item. */
const leafCount = (item) =>
  (item.entry ? 1 : 0) + item.children.reduce((sum, child) => sum + leafCount(child), 0)

export { buildTree, leafCount }
