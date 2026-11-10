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
 * The rows of the Repositories table, built from the draft of the maven domain. Nothing here touches Vue, the
 * router or the network. The filter reuses the rules of the repository search (parseSearch, matchesSearch).
 */

import { effectiveTypeOf, TYPE_LABELS } from '../../store/maven/repositoryTypes'
import { matchesSearch, parseSearch } from '../../store/maven/repositories'
import { writePolicyOf } from './writePolicy'

export const SORT_KEYS = ['name', 'type', 'visibility', 'storage']

const VISIBILITY_LABELS = { PUBLIC: 'Public', HIDDEN: 'Hidden', PRIVATE: 'Private' }
const STORAGE_LABELS = { fs: 'File system', s3: 'S3' }
const NO_VALUE = '-'
// The name of an entry without an id, as the generic editor showed it. The row keeps its id empty.
export const UNNAMED = 'Unnamed repository'

// An id that is missing or not text is the empty id, which the search and the sort read as no value.
const idOf = (entry) => (typeof entry?.id === 'string' ? entry.id : '')

const visibilityLabel = (visibility) => VISIBILITY_LABELS[visibility] ?? String(visibility ?? NO_VALUE)

// A group has no storage of its own, and an entry without one shows the same hyphen.
const storageLabel = (entry, type) => {
  if (type === 'GROUP') return NO_VALUE
  const kind = entry.storageProvider?.type
  return STORAGE_LABELS[kind] ?? (kind ? String(kind) : NO_VALUE)
}

/* The address a build tool uses for a repository: the origin, the base path and the id. */
export const addressOf = (origin, basePath, id) => {
  const base = basePath.endsWith('/') ? basePath : `${basePath}/`
  return `${origin}${base}${id}`
}

/* The caption of the table, with the shown and the total count. */
export const captionOf = (shown, total) => `Repositories, ${shown} of ${total} shown`

/*
 * One row per entry of the draft. `savedIds`, when given, are the repositories the server holds, and every other
 * row is marked unsaved. `writePolicy` is set for hosted repositories only. `addresses`, when given, are the
 * addresses of the detail pages, one per entry (an entry without a usable id has a key address, see collections.js).
 * An entry without an id keeps its place in `index`, has no address of its own and is named UNNAMED.
 */
export const rowsOf = (entries, { origin = '', basePath = '/', savedIds, addresses } = {}) => entries.map((entry, index) => {
  const { type, declared } = effectiveTypeOf(entry)
  const id = idOf(entry)
  const unnamed = id === ''
  return {
    index,
    id,
    name: unnamed ? UNNAMED : id,
    unnamed,
    route: addresses?.[index] ?? (unnamed ? `~${index}` : id),
    description: entry.description ?? '',
    tags: Array.isArray(entry.tags) ? entry.tags : [],
    type: { key: type, label: TYPE_LABELS[type.toLowerCase()], derived: !declared },
    visibility: visibilityLabel(entry.visibility),
    storage: storageLabel(entry, type),
    writePolicy: type === 'HOSTED' ? writePolicyOf(entry) : null,
    versionPolicy: entry.versionPolicy ?? null,
    address: unnamed ? NO_VALUE : addressOf(origin, basePath, id),
    unsaved: savedIds !== undefined && !savedIds.includes(id),
  }
})

/* The rows the search text and the tag tokens keep, in their order. */
export const filterRows = (rows, query = '') => {
  const criteria = parseSearch(query)
  return rows.filter(row => matchesSearch(row, criteria))
}

const SORT_VALUE = {
  name: (row) => row.name,
  type: (row) => row.type.label,
  visibility: (row) => row.visibility,
  storage: (row) => (row.storage === NO_VALUE ? '' : row.storage),
}

/* The rows in the order of one column, ascending or descending. Equal values keep the order the rows came in. */
export const sortRows = (rows, key, direction = 'ascending') => {
  const value = SORT_VALUE[key] ?? SORT_VALUE.name
  const sign = direction === 'descending' ? -1 : 1
  return rows
    .map((row, index) => ({ row, index }))
    .sort((left, right) => {
      const a = value(left.row)
      const b = value(right.row)
      if (a !== b) return (a < b ? -1 : 1) * sign
      return left.index - right.index
    })
    .map(({ row }) => row)
}
