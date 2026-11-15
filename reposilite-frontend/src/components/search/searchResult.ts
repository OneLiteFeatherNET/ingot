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
 * The answers of GET /api/maven/search and GET /api/maven/search/files, read into the shapes the
 * page works with. Every field that the spec calls optional has a default, so an answer from a
 * backend that leaves one out never breaks a row. Times arrive as an ISO-8601 instant (spec
 * section 6) or as epoch seconds, which is how the backend writes a java.time.Instant.
 */
import prettyBytes from 'pretty-bytes'
import { instantToDate } from '../../helpers/apiTime'
import type { components } from '../../types/api'

type Origin = components['schemas']['Origin']
type IndexState = components['schemas']['IndexState']
type ApiSearchItem = components['schemas']['ArtifactSearchItem']
type ApiSearchResponse = components['schemas']['ArtifactSearchResponse']
type ApiSearchFacets = components['schemas']['SearchFacets']
type ApiFileItem = components['schemas']['ArtifactFileItem']
type ApiFilesResponse = components['schemas']['ArtifactFilesResponse']

// The progress of a build is read from the index status too. The generated types do not declare
// these two fields, so they are added here as optional values, as the fake API sends them.
type ApiIndexStatus = Partial<components['schemas']['IndexStatus']> & {
  repositoriesDone?: number | null
  repositoriesTotal?: number | null
}

// What the normalizers accept: an answer may leave out any field, so every field is optional.
type LooseItem = Partial<Omit<ApiSearchItem, 'repositories'>> & {
  repositories?: Partial<components['schemas']['RepositoryPresence']>[]
}
type LoosePage = Partial<Omit<ApiSearchResponse, 'items' | 'facets' | 'indexStatus'>> & {
  items?: LooseItem[]
  facets?: Partial<ApiSearchFacets>
  indexStatus?: ApiIndexStatus
}
type LooseFile = Partial<ApiFileItem>
type LooseFiles = Partial<Omit<ApiFilesResponse, 'items'>> & { items?: LooseFile[] }

export interface FacetBucket {
  value: string
  count: number
}

export interface RepositoryRef {
  name: string
  origin: Origin
}

export interface SearchItem {
  groupId: string
  artifactId: string
  version: string
  latestVersion: string | null
  snapshot: boolean
  repositories: RepositoryRef[]
  fileCount: number
  lastModified: Date | null
  browseLink: string
}

export interface IndexStatus {
  state: IndexState
  lagSeconds: number
  lastFullRebuild: Date | null
  repositoriesDone: number | null
  repositoriesTotal: number | null
}

export type Facets = Record<'repository' | 'extension' | 'classifier' | 'origin' | 'snapshot', FacetBucket[]>

export interface SearchPage {
  items: SearchItem[]
  page: number
  size: number
  total: number
  totalCapped: boolean
  facets: Facets
  indexStatus: IndexStatus
}

export interface SearchFile {
  repository: string
  path: string
  name: string
  classifier: string
  extension: string
  size: number
  lastModified: Date | null
  origin: Origin
  sourceHost: string
  sha1: string
  browseLink: string
}

export interface SearchFiles {
  items: SearchFile[]
  page: number
  size: number
  total: number
}

export interface Notice {
  kind: 'building' | 'missing' | 'degraded'
  text: string
}

// The files of one open row: what has been loaded so far, and whether the last request is still on its way.
export interface FilesState {
  status: 'loading' | 'ready' | 'error'
  items: SearchFile[]
  total: number
  page: number
  message?: string
}

const toDate = (value: unknown): Date | null => {
  if (typeof value === 'number') return instantToDate(value)
  if (typeof value === 'string') {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? null : date
  }
  return null
}

const text = (value: unknown): string => (typeof value === 'string' ? value : '')
const count = (value: unknown): number => (typeof value === 'number' && Number.isFinite(value) ? value : 0)
const orNull = (value: unknown): number | null => (typeof value === 'number' ? value : null)

const normalizeFacet = (buckets: FacetBucket[] | undefined): FacetBucket[] =>
  (Array.isArray(buckets) ? buckets : []).map(bucket => ({ value: String(bucket.value ?? ''), count: count(bucket.count) }))

const normalizeItem = (item: LooseItem): SearchItem => ({
  groupId: text(item.groupId),
  artifactId: text(item.artifactId),
  version: text(item.version),
  latestVersion: typeof item.latestVersion === 'string' ? item.latestVersion : null,
  snapshot: item.snapshot === true,
  repositories: (Array.isArray(item.repositories) ? item.repositories : [])
    .map(repository => ({ name: text(repository.name), origin: repository.origin === 'MIRROR' ? 'MIRROR' : 'HOSTED' })),
  fileCount: count(item.fileCount),
  lastModified: toDate(item.lastModified),
  browseLink: text(item.browseLink)
})

const INDEX_STATES: readonly IndexState[] = ['READY', 'BUILDING', 'MISSING', 'DEGRADED']

const normalizeStatus = (status: ApiIndexStatus = {}): IndexStatus => ({
  state: INDEX_STATES.includes(status.state as IndexState) ? status.state as IndexState : 'READY',
  lagSeconds: count(status.lagSeconds),
  lastFullRebuild: toDate(status.lastFullRebuild),
  repositoriesDone: orNull(status.repositoriesDone),
  repositoriesTotal: orNull(status.repositoriesTotal)
})

const normalizePage = (data: LoosePage = {}): SearchPage => ({
  items: (Array.isArray(data.items) ? data.items : []).map(normalizeItem),
  page: count(data.page),
  size: Number.isFinite(data.size) ? data.size as number : 20,
  total: count(data.total),
  totalCapped: data.totalCapped === true,
  facets: Object.fromEntries(
    (['repository', 'extension', 'classifier', 'origin', 'snapshot'] as const)
      .map(name => [name, normalizeFacet(data.facets?.[name])])
  ) as Facets,
  indexStatus: normalizeStatus(data.indexStatus)
})

const normalizeFile = (file: LooseFile): SearchFile => ({
  repository: text(file.repository),
  path: text(file.path),
  name: text(file.name),
  classifier: text(file.classifier),
  extension: text(file.extension),
  size: count(file.size),
  lastModified: toDate(file.lastModified),
  origin: file.origin === 'MIRROR' ? 'MIRROR' : 'HOSTED',
  sourceHost: text(file.sourceHost),
  sha1: text(file.sha1),
  browseLink: text(file.browseLink)
})

const normalizeFiles = (data: LooseFiles = {}): SearchFiles => ({
  items: (Array.isArray(data.items) ? data.items : []).map(normalizeFile),
  page: count(data.page),
  size: Number.isFinite(data.size) ? data.size as number : 50,
  total: count(data.total)
})

// A mirror entry is a copy of a proxy repository, and the label says so (spec section 8.1).
const repositoryLabel = (repository: RepositoryRef): string =>
  (repository.origin === 'MIRROR' ? `cached from ${repository.name}` : repository.name)

const rowKey = (item: Pick<SearchItem, 'groupId' | 'artifactId' | 'version'>): string =>
  `${item.groupId}:${item.artifactId}:${item.version}`

// UTC on purpose: the same instant reads the same day on every machine.
const formatDay = (date: Date | null): string => (date ? date.toISOString().slice(0, 10) : '')

const formatSize = (bytes: number): string => prettyBytes(bytes, { binary: true })

// The text of the live region after a search (spec section 8.5).
const announcement = ({ total, totalCapped }: Pick<SearchPage, 'total' | 'totalCapped'>): string => {
  if (totalCapped) return 'More than 10 000 results'
  if (total === 0) return 'No results'
  return total === 1 ? '1 result' : `${total} results`
}

// What the index state asks the page to say, or null while the index is ready (spec section 8.3).
const indexNotice = (status: IndexStatus): Notice | null => {
  if (status.state === 'BUILDING') {
    const progress = status.repositoriesTotal === null
      ? 'Indexing.'
      : `Indexing, ${status.repositoriesDone ?? 0} of ${status.repositoriesTotal} repositories.`
    return { kind: 'building', text: `${progress} Results may be partial.` }
  }
  if (status.state === 'MISSING') {
    return { kind: 'missing', text: 'Nothing is indexed yet, so a search finds nothing.' }
  }
  if (status.state === 'DEGRADED') {
    const minutes = Math.floor(status.lagSeconds / 60)
    if (minutes < 1) return { kind: 'degraded', text: 'Results may be out of date.' }
    return { kind: 'degraded', text: `Results may lag by ${minutes} ${minutes === 1 ? 'minute' : 'minutes'}.` }
  }
  return null
}

export {
  toDate,
  normalizePage,
  normalizeFiles,
  repositoryLabel,
  rowKey,
  formatDay,
  formatSize,
  announcement,
  indexNotice
}
