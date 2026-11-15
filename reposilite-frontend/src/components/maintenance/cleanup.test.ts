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
  statusLabel,
  triggerLabel,
  sortNewestFirst,
  lastRunOf,
  normalizeRun,
  normalizeDryRun,
  isNothingToDelete
} from './cleanup'

const run = (id, policyId, startedAt, overrides = {}) => ({
  id,
  policyId,
  trigger: 'MANUAL',
  startedAt,
  finishedAt: startedAt,
  status: 'SUCCEEDED',
  deletedCount: 0,
  deletedBytes: 0,
  failures: [],
  ...overrides
})

describe('statusLabel', () => {
  it('names a successful run', () => {
    expect(statusLabel('SUCCEEDED')).toBe('Succeeded')
  })

  it('names a partly failed run', () => {
    expect(statusLabel('PARTIAL')).toBe('Partly failed')
  })

  it('names a failed run', () => {
    expect(statusLabel('FAILED')).toBe('Failed')
  })

  it('shows an unknown status as it was written', () => {
    expect(statusLabel('QUEUED')).toBe('QUEUED')
  })
})

describe('triggerLabel', () => {
  it('names a run started by hand', () => {
    expect(triggerLabel('MANUAL')).toBe('Manual')
  })

  it('names a run started by the schedule', () => {
    expect(triggerLabel('SCHEDULED')).toBe('Scheduled')
  })
})

describe('sortNewestFirst', () => {
  it('puts the run that started last first, whatever the order it arrived in', () => {
    const runs = [
      run(1, 'a', '2026-10-01T10:00:00Z'),
      run(3, 'a', '2026-10-03T10:00:00Z'),
      run(2, 'a', '2026-10-02T10:00:00Z')
    ]

    expect(sortNewestFirst(runs).map(entry => entry.id)).toEqual([3, 2, 1])
  })

  it('leaves the list it was given unchanged', () => {
    const runs = [run(1, 'a', '2026-10-01T10:00:00Z'), run(2, 'a', '2026-10-02T10:00:00Z')]

    sortNewestFirst(runs)

    expect(runs.map(entry => entry.id)).toEqual([1, 2])
  })
})

describe('lastRunOf', () => {
  it('returns the newest run of the policy', () => {
    const runs = [
      run(4, 'other', '2026-10-04T10:00:00Z'),
      run(3, 'a', '2026-10-03T10:00:00Z'),
      run(1, 'a', '2026-10-01T10:00:00Z')
    ]

    expect(lastRunOf(runs, 'a').id).toBe(3)
  })

  it('returns null when the policy has never run', () => {
    expect(lastRunOf([run(1, 'other', '2026-10-01T10:00:00Z')], 'a')).toBeNull()
  })
})

describe('normalizeRun', () => {
  it('reads a missing finish time as not finished', () => {
    expect(normalizeRun({ id: 1, policyId: 'a', startedAt: '2026-10-01T10:00:00Z' }).finishedAt).toBeNull()
  })

  it('reads missing failures as none', () => {
    expect(normalizeRun({ id: 1, policyId: 'a' }).failures).toEqual([])
  })

  it('reads missing counters as zero', () => {
    const normalized = normalizeRun({ id: 1, policyId: 'a' })

    expect(normalized.deletedCount).toBe(0)
    expect(normalized.deletedBytes).toBe(0)
  })
})

describe('normalizeDryRun', () => {
  it('reads missing deletions as none', () => {
    expect(normalizeDryRun({ policyId: 'a', count: 0 }).deletions).toEqual([])
  })

  it('reads missing warnings as none', () => {
    expect(normalizeDryRun({ policyId: 'a', count: 0 }).warnings).toEqual([])
  })

  it('keeps the count and totals the server computed', () => {
    const preview = normalizeDryRun({ policyId: 'a', count: 2, totalBytes: 2048, deletions: [] })

    expect(preview.count).toBe(2)
    expect(preview.totalBytes).toBe(2048)
  })
})

describe('isNothingToDelete', () => {
  it('is true for a preview that deletes nothing', () => {
    expect(isNothingToDelete(normalizeDryRun({ policyId: 'a', count: 0 }))).toBe(true)
  })

  it('is false for a preview with deletions', () => {
    expect(isNothingToDelete(normalizeDryRun({ policyId: 'a', count: 1, deletions: [{}] }))).toBe(false)
  })
})
