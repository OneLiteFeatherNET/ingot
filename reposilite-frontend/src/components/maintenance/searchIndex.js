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

const INDEX_STATES = ['READY', 'BUILDING', 'MISSING', 'DEGRADED']

// Above this share of the sampled rows that disagree with storage, the index is degraded (spec 3.8).
export const DEGRADED_ABOVE = 0.05

// The DONE marker of a rebuild cursor: the cursor repository is complete, including its cleanup.
const CURSOR_DONE = '//done'

export const INDEX_STATE_LABELS = {
  READY: 'Ready',
  BUILDING: 'Building',
  MISSING: 'Empty',
  DEGRADED: 'Degraded',
  UNKNOWN: 'Unknown'
}

const RUN_STATUS_LABELS = {
  RUNNING: 'Running',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled'
}

const RUN_KIND_LABELS = {
  REBUILD: 'Rebuild',
  VERIFY: 'Verify'
}

const RUN_TRIGGER_LABELS = {
  MANUAL: 'Started by hand',
  SCHEDULED: 'Scheduled',
  STARTUP: 'First start',
  GAP: 'Gap repair'
}

export const runStatusLabel = (status) => RUN_STATUS_LABELS[status] ?? 'Unknown'
export const runKindLabel = (kind) => RUN_KIND_LABELS[kind] ?? 'Unknown'
export const runTriggerLabel = (trigger) => RUN_TRIGGER_LABELS[trigger] ?? 'Unknown'

const count = (value) => (Number.isFinite(value) ? value : 0)
const text = (value) => (typeof value === 'string' && value !== '' ? value : null)

const normalizeCounters = (counters = {}) => ({
  processed: count(counters.processed),
  upserted: count(counters.upserted),
  deleted: count(counters.deleted),
  unparsed: count(counters.unparsed),
  skippedTooLong: count(counters.skippedTooLong),
  errors: count(counters.errors)
})

/** A run as the Maintenance panel reads it. `repositories` is null when the run covers every repository. */
export const normalizeIndexRun = (wire) => ({
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

const normalizeVerify = (wire) => ({
  runId: wire.runId ?? null,
  at: instantToDate(wire.at),
  sampled: count(wire.sampled),
  missing: count(wire.missing),
  changed: count(wire.changed),
  reorigined: count(wire.reorigined),
  removedRepositoryRows: count(wire.removedRepositoryRows),
  drift: Number.isFinite(wire.drift) ? wire.drift : 0
})

/** The whole index status. Unknown states read as UNKNOWN rather than as a guess. */
export const normalizeIndexStatus = (wire = {}) => ({
  state: INDEX_STATES.includes(wire.state) ? wire.state : 'UNKNOWN',
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

export const isRunLive = (run) => run?.status === 'RUNNING'

/** True when the drift of a verify report is above the degraded threshold. */
export const isDegraded = (report) => report.drift > DEGRADED_ABOVE

/**
 * The repositories of a rebuild run in the order it walks them (by name), each as done, current or
 * pending. A verify run walks the sample, not the repositories, so it has no per-repository progress.
 */
export const repositoryProgress = (run, repositories) => {
  if (run.kind !== 'REBUILD') return []
  const names = [...(run.repositories ?? repositories)].sort()
  const cursor = run.cursorRepository ? names.indexOf(run.cursorRepository) : -1
  const doneThrough = run.cursorPath === CURSOR_DONE ? cursor : cursor - 1
  return names.map((name, index) => {
    if (index <= doneThrough) return { name, state: 'done' }
    if (index === cursor) return { name, state: 'current' }
    return { name, state: 'pending' }
  })
}
