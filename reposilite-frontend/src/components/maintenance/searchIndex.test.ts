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

import { describe, it, expect } from 'vitest'
import {
  DEGRADED_ABOVE,
  INDEX_STATE_LABELS,
  isDegraded,
  isRunLive,
  normalizeIndexRun,
  normalizeIndexStatus,
  repositoryProgress,
  runKindLabel,
  runStatusLabel,
  runTriggerLabel
} from './searchIndex'

// The Instants of the API are epoch seconds with a fraction, as Jackson writes java.time.Instant.
const SECONDS = 1760931012.5

const counters = (overrides = {}) => ({
  processed: 0,
  upserted: 0,
  deleted: 0,
  unparsed: 0,
  skippedTooLong: 0,
  errors: 0,
  ...overrides
})

const run = (overrides = {}) => ({
  id: 7,
  kind: 'REBUILD',
  trigger: 'MANUAL',
  status: 'RUNNING',
  startedAt: SECONDS,
  finishedAt: null,
  leaseOwner: 'ingot-1#3',
  leaseUntil: SECONDS + 120,
  repositories: null,
  cursorRepository: null,
  cursorPath: null,
  counters: counters(),
  lastError: null,
  ...overrides
})

const status = (overrides = {}) => ({
  state: 'READY',
  lagSeconds: 0,
  lastFullRebuild: SECONDS,
  rows: 1834,
  outbox: { pending: 0, deadLetters: 0, droppedEnqueues: 0 },
  repositories: ['releases', 'snapshots'],
  engine: 'database',
  activeRun: null,
  runs: [],
  lastVerify: null,
  ...overrides
})

describe('normalizeIndexStatus', () => {
  it('reads the instants of the status as dates from epoch seconds', () => {
    const normalized = normalizeIndexStatus(status())

    expect(normalized.lastFullRebuild.getTime()).toBe(Math.round(SECONDS * 1000))
  })

  it('reads a status that has never completed a rebuild as no last rebuild', () => {
    const normalized = normalizeIndexStatus(status({ lastFullRebuild: null }))

    expect(normalized.lastFullRebuild).toBeNull()
  })

  it('keeps pending entries and dead letters apart', () => {
    const normalized = normalizeIndexStatus(status({ outbox: { pending: 4, deadLetters: 2, droppedEnqueues: 1 } }))

    expect(normalized.outbox).toEqual({ pending: 4, deadLetters: 2, droppedEnqueues: 1 })
  })

  it('lists the configured repositories in name order, the order a rebuild walks them in', () => {
    const normalized = normalizeIndexStatus(status({ repositories: ['snapshots', 'releases', 'mirror'] }))

    expect(normalized.repositories).toEqual(['mirror', 'releases', 'snapshots'])
  })

  it('reads an unknown index state as unknown instead of guessing one', () => {
    expect(normalizeIndexStatus(status({ state: 'SOMETHING_NEW' })).state).toBe('UNKNOWN')
  })

  it('reads the last verify report with its drift as a fraction', () => {
    const normalized = normalizeIndexStatus(status({
      lastVerify: { runId: 9, at: SECONDS, sampled: 2000, missing: 3, changed: 1, reorigined: 0, removedRepositoryRows: 0, drift: 0.002 }
    }))

    expect(normalized.lastVerify).toMatchObject({ runId: 9, sampled: 2000, missing: 3, changed: 1, drift: 0.002 })
    expect(normalized.lastVerify.at.getTime()).toBe(Math.round(SECONDS * 1000))
  })

  it('reads a status without a verify report as no report', () => {
    expect(normalizeIndexStatus(status({ lastVerify: null })).lastVerify).toBeNull()
  })
})

describe('normalizeIndexRun', () => {
  it('reads the counters, the lease and the cursor of a run', () => {
    const normalized = normalizeIndexRun(run({
      cursorRepository: 'releases',
      cursorPath: 'org/x/1.0/x-1.0.jar',
      counters: counters({ processed: 500, upserted: 480, unparsed: 12, errors: 1 })
    }))

    expect(normalized).toMatchObject({
      id: 7,
      kind: 'REBUILD',
      status: 'RUNNING',
      cursorRepository: 'releases',
      cursorPath: 'org/x/1.0/x-1.0.jar',
      leaseOwner: 'ingot-1#3'
    })
    expect(normalized.counters).toEqual({ processed: 500, upserted: 480, deleted: 0, unparsed: 12, skippedTooLong: 0, errors: 1 })
    expect(normalized.leaseUntil.getTime()).toBe(Math.round((SECONDS + 120) * 1000))
  })

  it('reads a run that covers every repository as null repositories', () => {
    expect(normalizeIndexRun(run({ repositories: null })).repositories).toBeNull()
  })

  it('keeps the names of the repositories a run was started for', () => {
    expect(normalizeIndexRun(run({ repositories: ['releases'] })).repositories).toEqual(['releases'])
  })

  it('reads a finished run with a finish time and its last error', () => {
    const normalized = normalizeIndexRun(run({ status: 'FAILED', finishedAt: SECONDS + 30, lastError: 'could not list releases' }))

    expect(normalized.finishedAt).toBeInstanceOf(Date)
    expect(normalized.lastError).toBe('could not list releases')
  })
})

describe('repositoryProgress', () => {
  const repositories = ['mirror', 'releases', 'snapshots']

  it('shows every repository as pending before the run has a cursor', () => {
    const progress = repositoryProgress(normalizeIndexRun(run({ cursorRepository: null })), repositories)

    expect(progress.map(entry => entry.state)).toEqual(['pending', 'pending', 'pending'])
  })

  it('marks the repositories before the cursor as done, the cursor one as current and the rest as pending', () => {
    const progress = repositoryProgress(normalizeIndexRun(run({ cursorRepository: 'releases', cursorPath: 'a/b' })), repositories)

    expect(progress).toEqual([
      { name: 'mirror', state: 'done' },
      { name: 'releases', state: 'current' },
      { name: 'snapshots', state: 'pending' }
    ])
  })

  it('marks the cursor repository as done once its cursor says the repository is complete', () => {
    const progress = repositoryProgress(normalizeIndexRun(run({ cursorRepository: 'releases', cursorPath: '//done' })), repositories)

    expect(progress.map(entry => entry.state)).toEqual(['done', 'done', 'pending'])
  })

  it('shows only the repositories a run was started for', () => {
    const progress = repositoryProgress(normalizeIndexRun(run({ repositories: ['snapshots'], cursorRepository: 'snapshots' })), repositories)

    expect(progress).toEqual([{ name: 'snapshots', state: 'current' }])
  })

  it('has no repository progress for a verify run, which walks the sample and not the repositories', () => {
    const progress = repositoryProgress(normalizeIndexRun(run({ kind: 'VERIFY', cursorRepository: 'releases' })), repositories)

    expect(progress).toEqual([])
  })
})

describe('drift', () => {
  it('calls a drift above five percent degraded', () => {
    expect(isDegraded({ drift: DEGRADED_ABOVE + 0.001 })).toBe(true)
  })

  it('does not call exactly five percent degraded, as the threshold is above five percent', () => {
    expect(isDegraded({ drift: DEGRADED_ABOVE })).toBe(false)
  })
})

describe('run state', () => {
  it('is live only while its status is RUNNING', () => {
    expect(isRunLive(normalizeIndexRun(run({ status: 'RUNNING' })))).toBe(true)
    expect(isRunLive(normalizeIndexRun(run({ status: 'CANCELLED', finishedAt: SECONDS })))).toBe(false)
    expect(isRunLive(null)).toBe(false)
  })

  it('names every index state, the run status, kind and trigger in words', () => {
    for (const state of ['READY', 'BUILDING', 'MISSING', 'DEGRADED', 'UNKNOWN']) {
      expect(INDEX_STATE_LABELS[state], `label of ${state}`).toBeTruthy()
    }
    expect(runStatusLabel('CANCELLED')).toBe('Cancelled')
    expect(runKindLabel('VERIFY')).toBe('Verify')
    expect(runTriggerLabel('GAP')).toBe('Gap repair')
  })
})
