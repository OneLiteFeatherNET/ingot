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

// The shapes the migration components read from the server. The migration endpoints are not part of the OpenAPI
// document behind src/types/api.ts, so these follow the server's JSON field names and are written by hand, like the
// store's own types (store/migration/types.ts). A field a component reads that the server does not declare is listed
// in the pull request of this change.
import type { MigrationJob } from '../../store/migration/types'

// A failed call: the RFC 9457 title and detail when the server sent them, and the status (0 without an answer).
export interface Problem {
  title?: string
  detail?: string
  status?: number
}

export interface ConnectionReport {
  reachable: boolean
  statusCode?: number | null
  version?: string | null
  edition?: string | null
  credentialAccepted?: boolean
  canListRepositories?: boolean
  canReadSettings?: boolean
  warnings?: string[]
  problem?: string | null
}

export interface DiscoveredRepository {
  name: string
  format: string
  type: string
  supported: boolean
  components: number
  bytes: number
  settingsReadable: boolean
}

export interface FormatTotals {
  repositories: number
  components: number
  bytes: number
}

// A type alias, not an interface: the store keeps the discovery in a record, and only an alias takes that.
export type DiscoveredNexus = {
  repositories: DiscoveredRepository[]
  formats: Record<string, FormatTotals>
}

export interface JobSummary {
  transferBytes?: number
  blockers?: string[]
}

// The body of a job request: the fields the server reads, as the store builds them.
export type JobRequest = Record<string, unknown>

// A repository inside a job, with the result of its work.
export interface JobRepository {
  nexusName: string
  status: string
  targetName?: string | null
}

// A job as the views read it: the store's job with the fields the progress and the steps show.
export interface JobView extends MigrationJob {
  pauseReason?: string | null
  repositories?: JobRepository[]
  summary?: JobSummary | null
}

// One object of the loss report of a dry run, as the report route answers it.
export interface LossRow {
  objectType: string
  nexusId: string
  target?: string | null
  status: string
  reason?: string | null
  consequence?: string | null
  action?: string | null
}

export interface LossReport {
  rows: LossRow[]
  summary: JobSummary | null
}

// A conflict of an import or a verify job: the repository and the path that differs.
export interface ConflictRow {
  repository: string
  path: string
  detail?: string | null
}
