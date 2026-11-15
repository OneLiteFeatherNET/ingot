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
 * Schema helpers of the settings view. They know nothing about Vue, so the rules that decide
 * what becomes a list-detail collection and how an entry is laid out can be tested directly.
 */

import { matchesSearch, parseSearch } from '../../store/maven/repositories'
import type { Searchable } from '../../store/maven/repositories'
import type { ControlElement, GroupLayout, UISchemaElement, VerticalLayout } from '@jsonforms/core'

/*
 * A JSON schema of a settings domain as the backend writes it. The keywords the helpers and the forms read are named.
 * There is no open index signature: a value of type {} (for instance a narrowed unknown) must still be a schema.
 */
export interface SettingsSchema {
  $schema?: string
  type?: string | string[]
  title?: string
  description?: string
  format?: string
  additionalProperties?: boolean
  const?: unknown
  default?: unknown
  required?: string[]
  properties?: Record<string, SettingsSchema>
  items?: SettingsSchema
  enum?: unknown[]
  oneOf?: SettingsSchema[]
  anyOf?: SettingsSchema[]
  allOf?: SettingsSchema[]
}

/* An entry of a collection. Its `id` addresses it, and the other fields belong to the entry. */
export interface CollectionEntry {
  id?: unknown
  [field: string]: unknown
}

const ID_PATTERN = /^[A-Za-z0-9._-]+$/

const isObjectSchema = (schema: SettingsSchema | undefined): schema is SettingsSchema & { properties: Record<string, SettingsSchema> } => schema?.type === 'object' && schema.properties !== undefined

/*
 * The backend generates most settings schemas with draft 2020-12 and declares it in `$schema`. The validator of the forms
 * compiles draft 07 and throws on a declared dialect it does not know, so the form of the domain was never drawn. The
 * keyword is left out instead: without it the schema is read as draft 07, which every keyword used here belongs to.
 */
export const formSchemaOf = <T>(schema: T): T => {
  if (!schema || typeof schema !== 'object' || !('$schema' in schema)) return schema
  const copy: Record<string, unknown> = { ...schema }
  delete copy.$schema
  return copy as T
}

/* A collection is an array of objects that carry an `id`: the entries are addressed by it. */
export const collectionsOf = (schema: SettingsSchema | undefined) => Object.entries(schema?.properties ?? {})
  .filter(([, property]) => property?.type === 'array' && isObjectSchema(property.items) && 'id' in property.items.properties)
  .map(([key, property]) => {
    const title = property.title ?? key
    // The filter above keeps only arrays whose items are objects, so the items are present here.
    const itemSchema = property.items as SettingsSchema
    return {
      key,
      title,
      description: property.description ?? '',
      itemSchema,
      /* The lower-case name of one entry, as it reads in "New repository" or "Delete repository". */
      noun: (itemSchema.title ?? singularOf(title)).toLowerCase()
    }
  })

/* "Repositories" -> "Repository". The plural is only a guess, so the caller can prefer a title on the item schema. */
export const singularOf = (title: string): string => {
  if (/ies$/i.test(title)) return title.replace(/ies$/i, 'y')
  if (/s$/i.test(title) && !/ss$/i.test(title)) return title.replace(/s$/i, '')
  return title
}

/* A list of plain strings, such as the tags of a repository, is a value a single control edits. */
const isStringList = (property: SettingsSchema | undefined): boolean => property?.type === 'array' && property.items?.type === 'string'

/* Scalars are the values a single control can edit. Objects, arrays and combinators get a section of their own. */
const isStructural = (property: SettingsSchema): boolean => {
  if (isStringList(property)) return false
  const types = Array.isArray(property.type) ? property.type : [property.type]
  return types.includes('object') || types.includes('array') || property.oneOf !== undefined || property.anyOf !== undefined || property.allOf !== undefined
}

const control = (key: string): ControlElement => ({ type: 'Control', scope: `#/properties/${key}` })

/*
 * The JSON Forms layout of one entry. Scalar properties share one "General" group at the top,
 * and every nested object or array follows as its own control, which renders as its own
 * titled section. The id is left out by default: it is the heading of the entry, and renaming
 * it would move the entry to another address.
 */
export const entryLayout = (schema: SettingsSchema, { exclude = ['id'] }: { exclude?: string[] } = {}): VerticalLayout => {
  const keys = Object.keys(schema.properties ?? {}).filter(key => !exclude.includes(key))
  const scalars = keys.filter(key => !isStructural(schema.properties?.[key] ?? {}))
  const structural = keys.filter(key => isStructural(schema.properties?.[key] ?? {}))

  const elements: UISchemaElement[] = []
  if (scalars.length > 0) {
    const general: GroupLayout = { type: 'Group', label: 'General', elements: scalars.map(control) }
    elements.push(general)
  }
  elements.push(...structural.map(control))
  return { type: 'VerticalLayout', elements }
}

/* The supporting line of a list item: the first two text or enum values, in schema order. */
export const summaryOf = (itemSchema: SettingsSchema | undefined, entry: CollectionEntry | undefined): string => Object.entries(itemSchema?.properties ?? {})
  .filter(([key, property]) => key !== 'id' && (property.enum !== undefined || property.type === 'string'))
  .map(([key]) => entry?.[key])
  .filter(value => typeof value === 'string' && value.length > 0)
  .slice(0, 2)
  .join(' · ')

/*
 * The entries the search text finds: the id, the description and the tags, as the overview searches
 * them, with `tag:name` tokens for a tag. Keeps the position of each entry in the full list, because
 * the edit targets that position.
 */
export const filterEntries = <T extends Searchable | null | undefined>(entries: T[], query: string) => {
  const criteria = parseSearch(query)
  return entries
    .map((entry, index) => ({ index, id: String(entry?.id ?? ''), entry }))
    .filter(item => matchesSearch(item.entry, criteria))
}

/*
 * The message for a new id, or an empty string when it is usable. Ids are compared without
 * regard to case, because two repositories that differ only in case clash on case-insensitive
 * file systems.
 */
export const validateNewId = (id: unknown, existingIds: unknown[], noun = 'entry'): string => {
  // A missing id (undefined, null, a number) is no id at all, and must not pass the pattern as "undefined".
  const text = typeof id === 'string' ? id : ''
  if (text === '') return 'Enter an id.'
  if (!ID_PATTERN.test(text)) return 'Use only letters, digits, dots, underscores and hyphens.'
  const lower = text.toLowerCase()
  if (existingIds.some(existing => idText({ id: existing }).toLowerCase() === lower)) {
    return `A ${noun} with this id already exists.`
  }
  return ''
}

/* The id of an entry as text, or '' when the entry has none. */
export const idText = (entry: unknown): string => {
  const id = (entry as CollectionEntry | null | undefined)?.id
  return typeof id === 'string' ? id : ''
}

/*
 * The address of each entry: its id when the id is usable and unique, otherwise "~" and the
 * entry's key. No valid id starts with "~", so the two cannot clash, and an entry that has no
 * usable id can still be opened and fixed.
 */
export const entryAddresses = (entries: unknown[], keys: string[]): string[] => entries.map((entry, index) => {
  const others = entries.filter((_, other) => other !== index).map(idText)
  return validateNewId(idText(entry), others) === '' ? idText(entry) : `~${keys[index]}`
})
