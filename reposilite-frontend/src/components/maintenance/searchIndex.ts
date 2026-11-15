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
 * The artifact search index as the admin endpoints of GET /api/maven/search/index send it (spec
 * section 6.3), read into the shapes the Maintenance panel works with. Instants are epoch seconds
 * and go through the shared apiTime helper. Counts default to zero, so an answer that leaves one out
 * never shows "undefined".
 */

import { instantToDate } from '../../helpers/apiTime'
import type { components } from '../../types/api'

type ApiIndexStatus = components['schemas']['SearchIndexResponse']
type ApiIndexRun = components['schemas']['SearchRunResponse']
type ApiLastVerify = components['schemas']['LastVerifyReport']
type ApiRunCounters = components['schemas']['RunCounters']

export type IndexState = components['schemas']['IndexState']
export type IndexStateOrUnknown = IndexState | 'UNKNOWN'
export type RunKind = components['schemas']['RunKind']
export type RunStatus = components['schemas']['RunStatus']
export type RunTrigger = components['schemas']['RunTrigger']

/*
 * Contract gap: the OpenAPI document types the Instants of the index as date-time strings, but the backend
 * sends them as epoch seconds (see apiTime.ts). They are declared as numbers here until the document is fixed.
 */
export type InstantWire = number | null | undefined

export type IndexRunWire = Pick<ApiIndexRun, 'id'> & Partial<Omit<ApiIndexRun, 'id' | 'startedAt' | 'finishedAt' | 'leaseUntil'>> & {
  startedAt?: InstantWire
  finishedAt?: InstantWire
  leaseUntil?: InstantWire
}

/*
 * Contract gap: runId and sampled are read by the dashboard, but LastVerifyReport does not declare them.
 * The Instant `at` has the same problem as the run fields above.
 */
export type LastVerifyWire = Partial<Omit<ApiLastVerify, 'at'>> & {
  at?: InstantWire
  runId?: number | null
  sampled?: number
}

/*
 * The state is read as text: a newer server may send an index state this dashboard does not know, and
 * normalizeIndexStatus reads such a state as UNKNOWN.
 */
export type IndexStatusWire = Partial<Omit<ApiIndexStatus, 'state' | 'lastFullRebuild' | 'activeRun' | 'runs' | 'lastVerify'>> & {
  state?: string
  lastFullRebuild?: InstantWire
  activeRun?: IndexRunWire | null
  runs?: IndexRunWire[]
  lastVerify?: LastVerifyWire | null
}

export interface IndexCounters {
  processed: number
  upserted: number
  deleted: number
  unparsed: number
  skippedTooLong: number
  errors: number
}

export interface IndexRun {
  id: number
  kind: RunKind
  trigger: RunTrigger
  status: RunStatus
  startedAt: Date | null
  finishedAt: Date | null
  leaseOwner: string | null
  leaseUntil: Date | null
  repositories: string[] | null
  cursorRepository: string | null
  cursorPath: string | null
  counters: IndexCounters
  lastError: string | null
}

export interface LastVerify {
  runId: number | null
  at: Date | null
  sampled: number
  missing: number
  changed: number
  reorigined: number
  removedRepositoryRows: number
  drift: number
}

export interface IndexOutbox {
  pending: number
  deadLetters: number
  droppedEnqueues: number
}

export interface IndexStatus {
  state: IndexStateOrUnknown
  lagSeconds: number
  lastFullRebuild: Date | null
  rows: number
  outbox: IndexOutbox
  repositories: string[]
  engine: string | null
  activeRun: IndexRun | null
  runs: IndexRun[]
  lastVerify: LastVerify | null
}

export interface RepositoryProgress {
  name: string
  state: 'done' | 'current' | 'pending'
}

const INDEX_STATES: readonly IndexState[] = ['READY', 'BUILDING', 'MISSING', 'DEGRADED']

const isIndexState = (value: unknown): value is IndexState => INDEX_STATES.some(state => state === value)

// Above this share of the sampled rows that disagree with storage, the index is degraded (spec 3.8).
export const DEGRADED_ABOVE = 0.05

// The DONE marker of a rebuild cursor: the cursor repository is complete, including its cleanup.
const CURSOR_DONE = '//done'

export const INDEX_STATE_LABELS: Readonly<Record<IndexStateOrUnknown, string>> = {
  READY: 'Ready',
  BUILDING: 'Building',
  MISSING: 'Empty',
  DEGRADED: 'Degraded',
  UNKNOWN: 'Unknown'
}

const RUN_STATUS_LABELS: Partial<Record<string, string>> = {
  RUNNING: 'Running',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled'
}

const RUN_KIND_LABELS: Partial<Record<string, string>> = {
  REBUILD: 'Rebuild',
  VERIFY: 'Verify'
}

const RUN_TRIGGER_LABELS: Partial<Record<string, string>> = {
  MANUAL: 'Started by hand',
  SCHEDULED: 'Scheduled',
  STARTUP: 'First start',
  GAP: 'Gap repair'
}

export const runStatusLabel = (status: string): string => RUN_STATUS_LABELS[status] ?? 'Unknown'
export const runKindLabel = (kind: string): string => RUN_KIND_LABELS[kind] ?? 'Unknown'
export const runTriggerLabel = (trigger: string): string => RUN_TRIGGER_LABELS[trigger] ?? 'Unknown'

const count = (value: unknown): number => (typeof value === 'number' && Number.isFinite(value) ? value : 0)
const text = (value: unknown): string | null => (typeof value === 'string' && value !== '' ? value : null)

const normalizeCounters = (counters: Partial<ApiRunCounters> = {}): IndexCounters => ({
  processed: count(counters.processed),
  upserted: count(counters.upserted),
  deleted: count(counters.deleted),
  unparsed: count(counters.unparsed),
  skippedTooLong: count(counters.skippedTooLong),
  errors: count(counters.errors)
})

/** A run as the Maintenance panel reads it. `repositories` is null when the run covers every repository. */
export const normalizeIndexRun = (wire: IndexRunWire): IndexRun => ({
  id: wire.id,
  kind: wire.kind ?? 'REBUILD',
  trigger: wire.trigger ?? 'MANUAL',
  status: wire.status ?? 'FAILED',
  startedAt: instantToDate(wire.startedAt),
  finishedAt: instantToDate(wire.finishedAt),
  leaseOwner: text(wire.leaseOwner),
  leaseUntil: instantToDate(wire.leaseUntil),
  repositories: Array.isArray(wire.repositories) ? [...wire.repositories] : null,
  cursorRepository: text(wire.cursorRepository),
  cursorPath: text(wire.cursorPath),
  counters: normalizeCounters(wire.counters),
  lastError: text(wire.lastError)
})

const normalizeVerify = (wire: LastVerifyWire): LastVerify => ({
  runId: wire.runId ?? null,
  at: instantToDate(wire.at),
  sampled: count(wire.sampled),
  missing: count(wire.missing),
  changed: count(wire.changed),
  reorigined: count(wire.reorigined),
  removedRepositoryRows: count(wire.removedRepositoryRows),
  drift: typeof wire.drift === 'number' && Number.isFinite(wire.drift) ? wire.drift : 0
})

/** The whole index status. Unknown states read as UNKNOWN rather than as a guess. */
export const normalizeIndexStatus = (wire: IndexStatusWire = {}): IndexStatus => ({
  state: isIndexState(wire.state) ? wire.state : 'UNKNOWN',
  lagSeconds: count(wire.lagSeconds),
  lastFullRebuild: instantToDate(wire.lastFullRebuild),
  rows: count(wire.rows),
  outbox: {
    pending: count(wire.outbox?.pending),
    deadLetters: count(wire.outbox?.deadLetters),
    droppedEnqueues: count(wire.outbox?.droppedEnqueues)
  },
  repositories: (Array.isArray(wire.repositories) ? wire.repositories : []).slice().sort(),
  engine: text(wire.engine),
  activeRun: wire.activeRun ? normalizeIndexRun(wire.activeRun) : null,
  runs: (Array.isArray(wire.runs) ? wire.runs : []).map(normalizeIndexRun),
  lastVerify: wire.lastVerify ? normalizeVerify(wire.lastVerify) : null
})

export const isRunLive = (run: Pick<IndexRun, 'status'> | null | undefined): boolean => run?.status === 'RUNNING'

/** True when the drift of a verify report is above the degraded threshold. */
export const isDegraded = (report: Pick<LastVerify, 'drift'>): boolean => report.drift > DEGRADED_ABOVE

/**
 * The repositories of a rebuild run in the order it walks them (by name), each as done, current or
 * pending. A verify run walks the sample, not the repositories, so it has no per-repository progress.
 */
export const repositoryProgress = (run: IndexRun, repositories: readonly string[]): RepositoryProgress[] => {
  if (run.kind !== 'REBUILD') return []
  const names = [...(run.repositories ?? repositories)].sort()
  const cursor = run.cursorRepository ? names.indexOf(run.cursorRepository) : -1
  const doneThrough = run.cursorPath === CURSOR_DONE ? cursor : cursor - 1
  return names.map((name, index): RepositoryProgress => {
    if (index <= doneThrough) return { name, state: 'done' }
    if (index === cursor) return { name, state: 'current' }
    return { name, state: 'pending' }
  })
}
