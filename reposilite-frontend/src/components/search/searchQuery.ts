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
 * The query of the search page, as the address spells it. The parameters are the ones of
 * GET /api/maven/search (spec section 4.1), so the address of the page and the request carry
 * the same names, and the request is built from the address and not from component state.
 *
 * A parameter that holds its default is left out of the address, so a plain link stays plain.
 * Everything read from the address is checked here: a value that does not fit falls back to its
 * default, so a hand-edited link never reaches the server as a 400.
 */
import { textFromParam } from '../../router/url-state'

const SORTS = ['groupId', 'artifactId', 'version', 'modified', 'size'] as const
const ORDERS = ['asc', 'desc'] as const
const SNAPSHOTS = ['all', 'true', 'false'] as const
const ORIGINS = ['all', 'hosted', 'mirror'] as const

export type Sort = typeof SORTS[number]
export type Order = typeof ORDERS[number]
export type Snapshot = typeof SNAPSHOTS[number]
export type OriginFilter = typeof ORIGINS[number]

// The state of the page: every parameter the address can hold, read into the type it is used as.
export interface SearchQuery {
  q: string
  groupId: string
  artifactId: string
  version: string
  repository: string[]
  extension: string[]
  classifier: string[]
  snapshot: Snapshot
  origin: OriginFilter
  modifiedAfter: string
  modifiedBefore: string
  sizeMin: string
  sizeMax: string
  sort: Sort
  order: Order
  page: number
  size: number
}

// The address as the router holds it: a repeated parameter is a list, and a value is text.
export type RouteQuery = Record<string, string | string[]>

const DEFAULT_SIZE = 20
const MAX_SIZE = 100
// The result window the server allows (page * size + size).
const RESULT_WINDOW = 10000

const DEFAULT_QUERY: SearchQuery = Object.freeze({
  q: '',
  groupId: '',
  artifactId: '',
  version: '',
  repository: [],
  extension: [],
  classifier: [],
  snapshot: 'all',
  origin: 'all',
  modifiedAfter: '',
  modifiedBefore: '',
  sizeMin: '',
  sizeMax: '',
  sort: 'groupId',
  order: 'asc',
  page: 0,
  size: DEFAULT_SIZE
})

const TEXT_KEYS = ['q', 'groupId', 'artifactId', 'version'] as const
const LIST_KEYS = ['repository', 'extension', 'classifier'] as const
// The filters, as opposed to the text, the sort and the paging.
const FILTER_KEYS = [
  'groupId', 'artifactId', 'version', 'repository', 'extension', 'classifier', 'snapshot', 'origin',
  'modifiedAfter', 'modifiedBefore', 'sizeMin', 'sizeMax'
] as const

const listFromParam = (param: unknown, normalise: (value: string) => string = value => value): string[] => {
  const values = [param ?? []].flat()
    .filter((value): value is string => typeof value === 'string' && value.trim() !== '')
    .map(value => normalise(value.trim()))
  return [...new Set(values)]
}

const oneOf = <T extends string>(param: unknown, allowed: readonly T[], fallback: T): T =>
  (allowed.includes(param as T) ? (param as T) : fallback)

const wholeNumber = (param: unknown, fallback: number): number =>
  (typeof param === 'string' && /^\d+$/.test(param) ? Number(param) : fallback)

const bytesFromParam = (param: unknown): string => (typeof param === 'string' && /^\d+$/.test(param) ? param : '')

const instantFromParam = (param: unknown): string =>
  (typeof param === 'string' && !Number.isNaN(Date.parse(param)) ? param : '')

const parseSearchQuery = (query: Record<string, unknown> = {}): SearchQuery => ({
  q: textFromParam(query.q),
  groupId: textFromParam(query.groupId),
  artifactId: textFromParam(query.artifactId),
  version: textFromParam(query.version),
  repository: listFromParam(query.repository),
  extension: listFromParam(query.extension, value => value.toLowerCase()),
  classifier: listFromParam(query.classifier),
  snapshot: oneOf(query.snapshot, SNAPSHOTS, DEFAULT_QUERY.snapshot),
  origin: oneOf(query.origin, ORIGINS, DEFAULT_QUERY.origin),
  modifiedAfter: instantFromParam(query.modifiedAfter),
  modifiedBefore: instantFromParam(query.modifiedBefore),
  sizeMin: bytesFromParam(query.sizeMin),
  sizeMax: bytesFromParam(query.sizeMax),
  sort: oneOf(query.sort, SORTS, DEFAULT_QUERY.sort),
  order: oneOf(query.order, ORDERS, DEFAULT_QUERY.order),
  page: wholeNumber(query.page, DEFAULT_QUERY.page),
  size: Math.min(MAX_SIZE, Math.max(1, wholeNumber(query.size, DEFAULT_SIZE)))
})

// The address for a state: only what differs from the default, numbers as text.
const toRouteQuery = (state: SearchQuery): RouteQuery => {
  const query: RouteQuery = {}
  for (const key of [...TEXT_KEYS, 'snapshot', 'origin', 'modifiedAfter', 'modifiedBefore', 'sizeMin', 'sizeMax', 'sort', 'order'] as const) {
    if (state[key] !== DEFAULT_QUERY[key]) query[key] = state[key]
  }
  for (const key of LIST_KEYS) {
    if (state[key].length > 0) query[key] = [...state[key]]
  }
  if (state.page !== DEFAULT_QUERY.page) query.page = String(state.page)
  if (state.size !== DEFAULT_QUERY.size) query.size = String(state.size)
  return query
}

// The query string of the request: the same parameters, a repeated one once per value.
const toRequestQuery = (state: SearchQuery): string => {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(toRouteQuery(state))) {
    for (const entry of [value].flat()) params.append(key, entry)
  }
  return params.toString()
}

// Any change to the query but the page itself starts again at the first page.
const changeQuery = (state: SearchQuery, changes: Partial<SearchQuery>): SearchQuery => ({ ...state, page: 0, ...changes })

// Clears the filters. The text and the sort stay, because they are not filters.
const resetFilters = (state: SearchQuery): SearchQuery => {
  const cleared: SearchQuery = { ...state, page: 0 }
  for (const key of FILTER_KEYS) {
    Object.assign(cleared, { [key]: Array.isArray(DEFAULT_QUERY[key]) ? [] : DEFAULT_QUERY[key] })
  }
  return cleared
}

// The active filters in words, for the empty state to say what was asked.
const activeFilters = (state: SearchQuery): string[] => {
  const words: string[] = []
  if (state.groupId) words.push(`group starts with ${state.groupId}`)
  if (state.artifactId) words.push(`artifact starts with ${state.artifactId}`)
  if (state.version) words.push(`version ${state.version}`)
  for (const key of LIST_KEYS) {
    if (state[key].length > 0) {
      words.push(`${key} ${state[key].map(value => (value === '-' ? 'none' : value)).join(' or ')}`)
    }
  }
  if (state.snapshot === 'false') words.push('releases only')
  if (state.snapshot === 'true') words.push('snapshots only')
  if (state.origin === 'hosted') words.push('hosted files only')
  if (state.origin === 'mirror') words.push('cached copies only')
  if (state.modifiedAfter) words.push(`modified after ${dateInputValue(state.modifiedAfter)}`)
  if (state.modifiedBefore) words.push(`modified before ${dateInputValue(state.modifiedBefore)}`)
  if (state.sizeMin) words.push(`size at least ${state.sizeMin} bytes`)
  if (state.sizeMax) words.push(`size at most ${state.sizeMax} bytes`)
  return words
}

// A date input holds a day, the request an instant: a picked day is the UTC midnight that starts it.
const dateInputValue = (instant: string): string => (instant ? instant.slice(0, 10) : '')

const instantFromDateInput = (day: string): string => (/^\d{4}-\d{2}-\d{2}$/.test(day) ? `${day}T00:00:00Z` : '')

// The last page the server will answer for: the pages of the total, within the result window.
const lastPage = (total: number, size: number): number => {
  const pages = Math.max(1, Math.ceil(total / size))
  const windowPages = Math.max(1, Math.floor(RESULT_WINDOW / size))
  return Math.min(pages, windowPages) - 1
}

export {
  DEFAULT_QUERY,
  SORTS,
  RESULT_WINDOW,
  parseSearchQuery,
  toRouteQuery,
  toRequestQuery,
  changeQuery,
  resetFilters,
  activeFilters,
  dateInputValue,
  instantFromDateInput,
  lastPage
}
