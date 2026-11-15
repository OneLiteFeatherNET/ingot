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
 * The changes between the saved settings and the draft, as a list the review sheet can show and
 * revert one by one. It knows nothing about Vue: the inputs are plain data and the schemas of the
 * domains, and nothing is written.
 *
 * A change is one of:
 *  - changed: a value that differs, including a oneOf whose variant differs (one change for the whole node)
 *  - added:   a key or an entry that only the draft has
 *  - removed: a key or an entry that only the saved state has
 *
 * Entries of a list with ids are matched by id, so a reordered list is not a change. `path` addresses
 * the value in the domain: a key is its name, an entry is { id }, and a position in a list without ids
 * is its index. `group` is the repository entry (or other collection entry) a change belongs to, or
 * null for the fields of the domain itself.
 */

import isEqual from 'lodash/isEqual'
import { isSensitive } from '../renderers/sensitive'
import type { SettingsSchema } from './collections'

/* A settings object: its keys are field names, and a value is any JSON. */
type Plain = Record<string, unknown>

/* An entry of a list with ids. The id is the only field the diff reads by name. */
interface Entry {
  id: string
  [field: string]: unknown
}

/* The kind of one change: a value that differs, a key or entry that only the draft has, or one only the saved state has. */
export type ChangeKind = 'changed' | 'added' | 'removed'

/* A segment of the path to a value: a key, a position in a list without ids, or an entry of a list with ids. */
export type PathSegment = string | number | { id: string }

/* The repository entry (or other collection entry) a change belongs to. */
export interface GroupRef {
  key: string
  title: string
  id: string
}

/* One change between the saved settings and the draft, as the review sheet lists it. */
export interface SettingsChange {
  id: string
  domain: string
  domainTitle: string
  kind: ChangeKind
  group: GroupRef | null
  path: PathSegment[]
  label: string
  masked: boolean
  before: unknown
  after: unknown
  index?: number
}

/* The changes of one domain, grouped the way the review sheet draws them. */
export interface ReviewGroup {
  key: string
  id: string | null
  title: string
  changes: SettingsChange[]
}

export interface ReviewSection {
  domain: string
  title: string
  groups: ReviewGroup[]
}

const isPlainObject = (value: unknown): value is Plain => value !== null && typeof value === 'object' && !Array.isArray(value)

const clone = <T>(value: T): T => (value === undefined ? value : JSON.parse(JSON.stringify(value)) as T)

const unique = <T>(values: T[]): T[] => [...new Set(values)]

/* "secretKey" -> "Secret key", used when the schema gives no title. */
export const humanize = (key: string): string => {
  const words = String(key).replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[-_]+/g, ' ').trim().toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

const titleOf = (schema: SettingsSchema | undefined, key: string): string => schema?.title ?? humanize(key)

/* A list of entries: its items are objects with an id, and the entries are matched by it. */
const isEntrySchema = (schema: SettingsSchema | undefined): boolean => schema?.type === 'array' && schema.items?.properties !== undefined && 'id' in schema.items.properties

const isKeyed = (list: unknown[]): list is Entry[] => list.every(entry => isPlainObject(entry) && typeof entry.id === 'string')
  && new Set(list.map(entry => (entry as Entry).id)).size === list.length

const matchesBranch = (branch: SettingsSchema | undefined, value: unknown): boolean => isPlainObject(value)
  && Object.entries(branch?.properties ?? {}).some(([key, property]) => property?.const !== undefined && value[key] === property.const)

/* The index of the oneOf branch a value belongs to, found by a const field such as `type: "fs"`. -1 when none. */
const variantOf = (schema: SettingsSchema, value: unknown): number => (schema.oneOf as SettingsSchema[]).findIndex(branch => matchesBranch(branch, value))

const variantChanged = (schema: SettingsSchema | undefined, before: unknown, after: unknown): boolean => schema?.oneOf !== undefined
  && variantOf(schema, before) !== variantOf(schema, after)

const entryGroup = (group: GroupRef | null, collection: { key: string; title: string }, id: string): GroupRef => group ?? { key: collection.key, title: collection.title, id }

const entryLabels = (group: GroupRef | null, labels: string[], id: string): string[] => (group ? [...labels, id] : [])

interface Context {
  domain: string
  domainTitle: string
  out: SettingsChange[]
}

interface EmitInput {
  kind: ChangeKind
  path: PathSegment[]
  group: GroupRef | null
  labels: string[]
  schema: SettingsSchema | undefined
  before?: unknown
  after?: unknown
  index?: number
}

/*
 * Adds one change. A secret is masked: its values never leave this function, so the review can show
 * that it changed without showing what it was.
 */
const emit = (context: Context, { kind, path, group, labels, schema, before, after, index }: EmitInput): void => {
  const lastKey = typeof path.at(-1) === 'string' ? path.at(-1) as string : ''
  const masked = isSensitive(lastKey, schema)
  context.out.push({
    id: [context.domain, ...path.map(segment => (isPlainObject(segment) ? `@${segment.id}` : String(segment)))].join('/'),
    domain: context.domain,
    domainTitle: context.domainTitle,
    kind,
    group,
    path,
    label: labels.join(' > '),
    masked,
    before: masked ? null : (kind === 'added' ? undefined : before),
    after: masked ? null : (kind === 'removed' ? undefined : after),
    ...(index === undefined ? {} : { index })
  })
}

interface DiffState {
  before: unknown
  after: unknown
  schema: SettingsSchema | undefined
  path: PathSegment[]
  labels: string[]
  group: GroupRef | null
  key?: string
}

const diffAt = (state: DiffState, context: Context): void => {
  const { before, after, schema, path, labels, group, key } = state
  if (isEqual(before, after)) return

  if (isPlainObject(before) && isPlainObject(after) && !variantChanged(schema, before, after)) {
    const branch = schema?.oneOf ? schema.oneOf[variantOf(schema, after)] : schema
    for (const property of unique([...Object.keys(before), ...Object.keys(after)])) {
      const childSchema = branch?.properties?.[property]
      const childLabels = [...labels, titleOf(childSchema, property)]
      const childPath = [...path, property]
      const inBefore = before[property] !== undefined
      const inAfter = after[property] !== undefined
      if (!inBefore && !inAfter) continue
      if (!inBefore) {
        emit(context, { kind: 'added', path: childPath, group, labels: childLabels, schema: childSchema, after: after[property] })
      } else if (!inAfter) {
        emit(context, { kind: 'removed', path: childPath, group, labels: childLabels, schema: childSchema, before: before[property] })
      } else {
        diffAt({ before: before[property], after: after[property], schema: childSchema, path: childPath, labels: childLabels, group, key: property }, context)
      }
    }
    return
  }

  if (Array.isArray(before) && Array.isArray(after)) {
    const beforeList: unknown[] = before
    const afterList: unknown[] = after
    const collection = { key: key ?? '', title: titleOf(schema, key ?? '') }
    if (isEntrySchema(schema) && isKeyed(beforeList) && isKeyed(afterList)) {
      diffEntries({ before: beforeList, after: afterList, schema: schema as SettingsSchema, path, labels, group, collection }, context)
      return
    }
    const length = Math.max(beforeList.length, afterList.length)
    for (let index = 0; index < length; index++) {
      const childPath = [...path, index]
      const childLabels = [...labels, String(index + 1)]
      if (index >= afterList.length) {
        emit(context, { kind: 'removed', path: childPath, group, labels: childLabels, schema: schema?.items, before: beforeList[index], index })
      } else if (index >= beforeList.length) {
        emit(context, { kind: 'added', path: childPath, group, labels: childLabels, schema: schema?.items, after: afterList[index], index })
      } else {
        diffAt({ before: beforeList[index], after: afterList[index], schema: schema?.items, path: childPath, labels: childLabels, group }, context)
      }
    }
    return
  }

  const kind = 'changed'
  emit(context, { kind, path, group, labels, schema, before, after })
}

interface EntryDiff {
  before: Entry[]
  after: Entry[]
  schema: SettingsSchema
  path: PathSegment[]
  labels: string[]
  group: GroupRef | null
  collection: { key: string; title: string }
}

const diffEntries = ({ before, after, schema, path, labels, group, collection }: EntryDiff, context: Context): void => {
  const itemSchema = schema.items
  const savedById = new Map<string, { entry: Entry; index: number }>(before.map((entry, index) => [entry.id, { entry, index }]))
  const draftIds = new Set(after.map(entry => entry.id))

  for (const entry of after) {
    const id = entry.id
    const entryPath: PathSegment[] = [...path, { id }]
    const saved = savedById.get(id)
    if (!saved) {
      emit(context, { kind: 'added', path: entryPath, group: entryGroup(group, collection, id), labels: entryLabels(group, labels, id), schema: itemSchema })
      continue
    }
    diffAt({
      before: saved.entry,
      after: entry,
      schema: itemSchema,
      path: entryPath,
      labels: entryLabels(group, labels, id),
      group: entryGroup(group, collection, id)
    }, context)
  }

  before.forEach((entry, index) => {
    if (draftIds.has(entry.id)) return
    emit(context, {
      kind: 'removed',
      path: [...path, { id: entry.id }],
      group: entryGroup(group, collection, entry.id),
      labels: entryLabels(group, labels, entry.id),
      schema: itemSchema,
      index
    })
  })
}

/*
 * The changes of every domain. `saved` and `draft` map each domain to its values, and `schemas` maps
 * each domain to its schema. The result is in domain order, then in the order of the draft.
 */
export const diffSettings = (saved: Plain = {}, draft: Plain = {}, schemas: Record<string, SettingsSchema | undefined> = {}): readonly SettingsChange[] => unique([
  ...Object.keys(saved),
  ...Object.keys(draft)
]).flatMap(domain => {
  const out: SettingsChange[] = []
  diffAt({
    before: saved[domain],
    after: draft[domain],
    schema: schemas[domain],
    path: [],
    labels: [],
    group: null,
    key: undefined
  }, { domain, domainTitle: schemas[domain]?.title ?? domain, out })
  return out
})

const locate = (root: unknown, path: PathSegment[]): unknown => path.reduce<unknown>((node, segment) => {
  if (node === undefined || node === null) return undefined
  if (isPlainObject(segment)) return Array.isArray(node) ? (node as Entry[]).find(entry => entry?.id === segment.id) : undefined
  return (node as Plain)[segment]
}, root)

/*
 * The domain's values with one change undone: the value at the change's path goes back to its saved
 * state. The draft is not changed; a new object is returned. Other changes stay as they are, so the
 * caller can revert them one at a time, and the diff of the result shows what is left.
 */
export const revertChange = (draftDomain: Plain | undefined, savedDomain: Plain | undefined, change: SettingsChange): Plain | undefined => {
  if (change.path.length === 0) return clone(savedDomain)

  const next: Plain = clone(draftDomain) ?? {}
  const segment = change.path.at(-1) as PathSegment
  const parent = locate(next, change.path.slice(0, -1)) as Plain | unknown[] | undefined | null
  if (parent === undefined || parent === null) return next

  const position = Array.isArray(parent)
    ? (isPlainObject(segment) ? parent.findIndex(entry => (entry as Plain | undefined)?.id === segment.id) : segment as number)
    : undefined

  if (change.kind === 'changed') {
    if (Array.isArray(parent)) parent[position as number] = clone(locate(savedDomain, change.path))
    else parent[segment as string] = clone(locate(savedDomain, change.path))
  } else if (change.kind === 'added') {
    if (Array.isArray(parent)) {
      if ((position as number) >= 0) parent.splice(position as number, 1)
    } else {
      delete parent[segment as string]
    }
  } else if (change.kind === 'removed') {
    const value = clone(locate(savedDomain, change.path))
    if (Array.isArray(parent)) parent.splice(Math.min(change.index ?? (position as number), parent.length), 0, value)
    else parent[segment as string] = value
  }
  return next
}

/*
 * The changes as the review shows them: one section per domain, and inside it one group per entry
 * (or the domain's own fields, when the group is null). The order of the changes is kept.
 */
export const groupChanges = (changes: readonly SettingsChange[]): ReviewSection[] => {
  const sections: ReviewSection[] = []
  for (const change of changes) {
    let section = sections.find(entry => entry.domain === change.domain)
    if (!section) {
      section = { domain: change.domain, title: change.domainTitle, groups: [] }
      sections.push(section)
    }
    const id = change.group?.id ?? null
    const key = change.group ? `${change.group.key}/${change.group.id}` : 'domain'
    let group = section.groups.find(entry => entry.key === key)
    if (!group) {
      group = {
        key,
        id,
        title: change.group ? change.group.title : section.title,
        changes: []
      }
      section.groups.push(group)
    }
    group.changes.push(change)
  }
  return sections
}

/* A value as a short line of text for the review. Sensitive fields of an object are not shown. */
export const formatChangeValue = (value: unknown): string => {
  if (value === undefined) return 'not set'
  if (value === null) return 'null'
  if (typeof value === 'string') return JSON.stringify(value)
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  if (Array.isArray(value)) return `${value.length} ${value.length === 1 ? 'item' : 'items'}`
  const summary = Object.entries(value as Plain)
    .filter(([key, entry]) => !isSensitive(key) && (typeof entry === 'string' || typeof entry === 'number' || typeof entry === 'boolean'))
    .map(([key, entry]) => `${key}: ${formatChangeValue(entry)}`)
    .join(', ')
  return summary
}
