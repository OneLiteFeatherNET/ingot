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

const toDate = (value) => {
  if (typeof value === 'number') return instantToDate(value)
  if (typeof value === 'string') {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? null : date
  }
  return null
}

const text = (value) => (typeof value === 'string' ? value : '')
const count = (value) => (Number.isFinite(value) ? value : 0)
const orNull = (value) => (typeof value === 'number' ? value : null)

const normalizeFacet = (buckets) =>
  (Array.isArray(buckets) ? buckets : []).map(bucket => ({ value: String(bucket.value ?? ''), count: count(bucket.count) }))

const normalizeItem = (item) => ({
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

const normalizeStatus = (status = {}) => ({
  state: ['READY', 'BUILDING', 'MISSING', 'DEGRADED'].includes(status.state) ? status.state : 'READY',
  lagSeconds: count(status.lagSeconds),
  lastFullRebuild: toDate(status.lastFullRebuild),
  repositoriesDone: orNull(status.repositoriesDone),
  repositoriesTotal: orNull(status.repositoriesTotal)
})

const normalizePage = (data = {}) => ({
  items: (Array.isArray(data.items) ? data.items : []).map(normalizeItem),
  page: count(data.page),
  size: Number.isFinite(data.size) ? data.size : 20,
  total: count(data.total),
  totalCapped: data.totalCapped === true,
  facets: Object.fromEntries(
    ['repository', 'extension', 'classifier', 'origin', 'snapshot']
      .map(name => [name, normalizeFacet(data.facets?.[name])])
  ),
  indexStatus: normalizeStatus(data.indexStatus)
})

const normalizeFile = (file) => ({
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

const normalizeFiles = (data = {}) => ({
  items: (Array.isArray(data.items) ? data.items : []).map(normalizeFile),
  page: count(data.page),
  size: Number.isFinite(data.size) ? data.size : 50,
  total: count(data.total)
})

// A mirror entry is a copy of a proxy repository, and the label says so (spec section 8.1).
const repositoryLabel = (repository) =>
  (repository.origin === 'MIRROR' ? `cached from ${repository.name}` : repository.name)

const rowKey = (item) => `${item.groupId}:${item.artifactId}:${item.version}`

// UTC on purpose: the same instant reads the same day on every machine.
const formatDay = (date) => (date ? date.toISOString().slice(0, 10) : '')

const formatSize = (bytes) => prettyBytes(bytes, { binary: true })

// The text of the live region after a search (spec section 8.5).
const announcement = ({ total, totalCapped }) => {
  if (totalCapped) return 'More than 10 000 results'
  if (total === 0) return 'No results'
  return total === 1 ? '1 result' : `${total} results`
}

// What the index state asks the page to say, or null while the index is ready (spec section 8.3).
const indexNotice = (status) => {
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
