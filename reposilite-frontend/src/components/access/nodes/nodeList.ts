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
 * The rules of the node list that need no component: the filter, the duplicate check, the
 * catalog suggestions, the difference between the saved and the draft list, and the wording of
 * where an inherited node comes from. Pure.
 */

import { ARTIFACT_ACTIONS, parseNode } from './nodeGrammar'

const normalize = (text) => String(text ?? '').trim().toLowerCase()

/** The entries whose node contains the query, ignoring case and outer spaces. */
const filterRows = (rows, query) => {
  const needle = normalize(query)
  if (needle === '') return rows
  return rows.filter(row => normalize(row.node).includes(needle))
}

/** True when the node is already in the list. */
const hasNode = (rows, node) => (rows ?? []).some(row => row.node === node)

const KIND_ORDER = ['STATIC', 'REPOSITORY', 'ARTIFACT', 'WILDCARD']
const KIND_LABEL = { STATIC: 'Static', REPOSITORY: 'Repository', ARTIFACT: 'Artifact', WILDCARD: 'Wildcard' }

/**
 * The catalog entries to suggest for a text: not yet listed and containing the text (ignoring
 * case). They come ordered by kind (Static, Repository, Wildcard), and inside a kind the ones that
 * start with the text come first. At most `limit` are returned. A blank text suggests everything
 * that is left.
 */
const suggestNodes = (catalog, text, listed = [], limit = 12) => {
  const needle = normalize(text)
  const taken = new Set(listed.map(row => row.node))
  const kindIndex = (item) => {
    const index = KIND_ORDER.indexOf(item.kind)
    return index === -1 ? KIND_ORDER.length : index
  }
  return (catalog ?? [])
    .filter(item => !taken.has(item.node) && item.node.toLowerCase().includes(needle))
    .map((item, index) => ({ item, index, prefix: item.node.toLowerCase().startsWith(needle) ? 0 : 1 }))
    .sort((left, right) =>
      (kindIndex(left.item) - kindIndex(right.item)) || (left.prefix - right.prefix) || (left.index - right.index))
    .slice(0, limit)
    .map(({ item }) => item)
}

const ACTION_DESCRIPTION = {
  read: 'Read the artifacts of this group or artifact',
  deploy: 'Deploy into this group or artifact',
  delete: 'Delete from this group or artifact',
  '*': 'Every artifact action on this group or artifact'
}

const ARTIFACT_PREFIX = 'ingot.artifact.'
const QUALIFIED_PREFIX = /^ingot\.repository\.[A-Za-z0-9_-]+\.artifact\./
const ACTION_WORDS = [...ARTIFACT_ACTIONS, '*']

/** The valid nodes made of a base (a text ending in '.') and one of the words, not listed yet. */
const completionsFrom = (base, words, listed) => {
  const taken = new Set(listed.map(row => row.node))
  return words
    .map(word => `${base}${word}`)
    .filter(node => !taken.has(node) && parseNode(node).valid)
    .map(node => ({ node, description: ACTION_DESCRIPTION[node.slice(node.lastIndexOf('.') + 1)], kind: 'ARTIFACT' }))
}

/**
 * The completions of an artifact text, which the catalog does not list: the repository prefix of a qualified
 * node (continue with '.artifact.'), and the actions after a groupId or artifactId. A continuation is marked
 * `continuation`, because choosing it fills the field and adds no node.
 */
const artifactSuggestions = (text, catalog, listed = []) => {
  const typed = String(text ?? '').trim()
  const repositoryPart = typed.match(/^ingot\.repository\.([A-Za-z0-9_-]*)\.?$/)
  if (repositoryPart) {
    const partial = repositoryPart[1]
    const repositories = repositoryNames(catalog).filter(name => name.startsWith(partial))
    return repositories.map(name => ({
      node: `ingot.repository.${name}.artifact.`,
      description: `Artifact nodes on repository ${name}`,
      kind: 'ARTIFACT',
      continuation: true
    }))
  }
  const head = typed.startsWith(ARTIFACT_PREFIX) ? ARTIFACT_PREFIX : typed.match(QUALIFIED_PREFIX)?.[0]
  if (!head) return []
  const rest = typed.slice(head.length)
  if (rest === '') return []
  const dot = rest.lastIndexOf('.')
  // No dot yet: the groupId is being typed, so the actions follow it.
  if (dot === -1) return completionsFrom(`${typed}.`, ACTION_WORDS, listed)
  if (rest.slice(0, dot) === '') return []
  const fragment = rest.slice(dot + 1)
  if (fragment === '') return completionsFrom(typed, ACTION_WORDS, listed)
  // A part of an action completes in place, anything else is a further part of the groupId.
  if (ACTION_WORDS.some(word => word.startsWith(fragment))) {
    return completionsFrom(typed.slice(0, typed.length - fragment.length), ACTION_WORDS.filter(word => word.startsWith(fragment)), listed)
  }
  return completionsFrom(`${typed}.`, ACTION_WORDS, listed)
}

/**
 * The suggestions for the node field: the artifact completions first, then the catalog matches, in kind order
 * (Static, Repository, Artifact, Wildcard), at most `limit`.
 */
const suggestWithArtifacts = (catalog, text, listed = [], limit = 12) => {
  const completions = artifactSuggestions(text, catalog, listed)
  const seen = new Set(completions.map(item => item.node))
  const rest = suggestNodes(catalog, text, listed, limit).filter(item => !seen.has(item.node))
  const merged = [...completions, ...rest]
  const kindIndex = (item) => {
    const index = KIND_ORDER.indexOf(item.kind)
    return index === -1 ? KIND_ORDER.length : index
  }
  return merged
    .map((item, index) => ({ item, index }))
    .sort((left, right) => (kindIndex(left.item) - kindIndex(right.item)) || (left.index - right.index))
    .map(({ item }) => item)
    .slice(0, limit)
}

/** Splits suggestions into the headed groups Static, Repository, Artifact and Wildcard, in that order. */
const groupSuggestions = (suggestions) =>
  KIND_ORDER
    .map(kind => ({ kind, label: KIND_LABEL[kind], options: suggestions.filter(item => item.kind === kind) }))
    .filter(group => group.options.length > 0)

/** The repository names found in the catalog, from the REPOSITORY nodes, without duplicates. */
const repositoryNames = (catalog) => {
  const names = []
  for (const item of catalog ?? []) {
    if (item.kind !== 'REPOSITORY') continue
    const name = item.node.split('.')[2]
    if (name && !names.includes(name)) names.push(name)
  }
  return names
}

/**
 * What changed between the saved and the draft list: { added, removed, changed, count }.
 * `added` and `removed` are entries, `changed` holds { node, from, to } for a flipped allowed value.
 */
const diffNodes = (saved, draft) => {
  const before = new Map((saved ?? []).map(row => [row.node, row]))
  const after = new Map((draft ?? []).map(row => [row.node, row]))
  const added = (draft ?? []).filter(row => !before.has(row.node))
  const removed = (saved ?? []).filter(row => !after.has(row.node))
  const changed = (draft ?? [])
    .filter(row => before.has(row.node) && before.get(row.node).allowed !== row.allowed)
    .map(row => ({ node: row.node, from: before.get(row.node).allowed, to: row.allowed }))
  return { added, removed, changed, count: added.length + removed.length + changed.length }
}

const levels = (depth) => `${depth} ${depth === 1 ? 'level' : 'levels'} up`

/** Where an inherited node comes from, for example "from team developers, 1 level up". */
const inheritedSourceLabel = ({ source, subject, depth }) => {
  if (source === 'INHERITED_TEAM') return `from team ${subject}, ${levels(depth ?? 1)}`
  if (source === 'TEAM') return `from team ${subject}`
  if (source === 'LEGACY') return `from the legacy instance admin grant of ${subject}`
  if (source === 'USER') return 'set directly'
  return subject ? `from ${subject}` : 'inherited'
}

/** True when a node held directly has the same node text as the inherited one. */
const isOverridden = (inheritedEntry, direct) => hasNode(direct, inheritedEntry.node)

export {
  diffNodes,
  filterRows,
  groupSuggestions,
  hasNode,
  inheritedSourceLabel,
  isOverridden,
  KIND_LABEL,
  repositoryNames,
  suggestNodes,
  suggestWithArtifacts
}
