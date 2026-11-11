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
  STEPS,
  stepStates,
  stepTarget,
  stepFromJobs,
  planFingerprint,
  acceptanceIsCurrent,
  mappingWarnings,
  syncBlockers,
} from './gating'

const row = (nexusName, targetName = nexusName, included = true) => ({ nexusName, targetName, included, supported: true })

const plan = (rows, conflictPolicy = 'refuse') => ({ repositories: rows, conflictPolicy })

const job = (id, kind, status, extra = {}) => ({ id, kind, status, pauseReason: null, repositories: [], results: {}, summary: null, ...extra })

const freshState = (overrides = {}) => ({
  connectionTested: false,
  discovered: false,
  mapping: null,
  ...overrides,
})

const openIds = (states) => Object.entries(states).filter(([, state]) => state.open).map(([id]) => id)

describe('the wizard steps', () => {
  it('lists the seven steps in their order', () => {
    expect(STEPS).toEqual(['connect', 'discover', 'mapping', 'dry-run', 'sync', 'verify', 'cut-over'])
  })

  it('keeps Connect open on a fresh page', () => {
    expect(openIds(stepStates(freshState(), []))).toEqual(['connect'])
  })

  it('opens Discover only after a connection test succeeded', () => {
    const states = stepStates(freshState({ connectionTested: true }), [])

    expect(states.discover.open).toBe(true)
    expect(states.mapping.open).toBe(false)
  })

  it('gives a reason for every closed step', () => {
    const states = stepStates(freshState(), [])

    expect(states.discover.reason, 'the reason is text the step list can show').toMatch(/connection/i)
    expect(states.sync.reason).toMatch(/dry run/i)
  })

  it('opens Mapping after a discovery and Dry run only with an included repository', () => {
    const empty = stepStates(freshState({ connectionTested: true, discovered: true, mapping: plan([row('releases', 'releases', false)]) }), [])
    const included = stepStates(freshState({ connectionTested: true, discovered: true, mapping: plan([row('releases')]) }), [])

    expect(empty.mapping.open).toBe(true)
    expect(empty['dry-run'].open, 'no included repository keeps the dry run closed').toBe(false)
    expect(included['dry-run'].open).toBe(true)
  })

  it('closes Dry run when two included repositories share a target name', () => {
    const duplicate = plan([row('a', 'shared'), row('b', 'shared')])

    const states = stepStates(freshState({ connectionTested: true, discovered: true, mapping: duplicate }), [])

    expect(states['dry-run'].open).toBe(false)
  })

  it('opens Sync only with a finished dry run, and Verify only with a started import', () => {
    const dryRun = job(1, 'DRY_RUN', 'COMPLETED')
    const queuedImport = job(2, 'IMPORT', 'QUEUED')

    expect(openIds(stepStates(freshState(), [dryRun]))).toContain('sync')
    expect(openIds(stepStates(freshState(), [dryRun]))).not.toContain('verify')
    expect(openIds(stepStates(freshState(), [dryRun, queuedImport]))).toContain('verify')
  })

  it('opens Cut-over only with a finished verify job', () => {
    expect(openIds(stepStates(freshState(), [job(3, 'VERIFY', 'RUNNING')]))).not.toContain('cut-over')
    expect(openIds(stepStates(freshState(), [job(3, 'VERIFY', 'COMPLETED')]))).toContain('cut-over')
  })
})

describe('the step a reload opens', () => {
  it('opens Sync while an import is unfinished', () => {
    expect(stepFromJobs([job(1, 'DRY_RUN', 'COMPLETED'), job(2, 'IMPORT', 'RUNNING')])).toBe('sync')
  })

  it('opens Sync while an import is paused', () => {
    expect(stepFromJobs([job(2, 'IMPORT', 'PAUSED', { pauseReason: 'READ_ONLY' })])).toBe('sync')
  })

  it('opens Verify after a finished import without a finished verify', () => {
    expect(stepFromJobs([job(2, 'IMPORT', 'COMPLETED')])).toBe('verify')
  })

  it('opens Cut-over after a finished verify', () => {
    expect(stepFromJobs([job(2, 'IMPORT', 'COMPLETED'), job(3, 'VERIFY', 'COMPLETED')])).toBe('cut-over')
  })

  it('opens nothing from the jobs when there is no import, so the page starts at Connect', () => {
    expect(stepFromJobs([])).toBeNull()
    expect(stepFromJobs([job(1, 'DRY_RUN', 'COMPLETED')])).toBeNull()
  })

  it('uses the newest job of a kind, not the first one listed', () => {
    const jobs = [job(9, 'IMPORT', 'RUNNING'), job(2, 'IMPORT', 'COMPLETED')]

    expect(stepFromJobs(jobs)).toBe('sync')
  })
})

describe('the step a closed address redirects to', () => {
  it('keeps an open step where it is', () => {
    expect(stepTarget('discover', freshState({ connectionTested: true }), [])).toEqual({ step: 'discover', redirected: false })
  })

  it('sends a skipped step to the last open step before it, with the reason', () => {
    const state = freshState({ connectionTested: true })

    const target = stepTarget('sync', state, [])

    expect(target.step).toBe('discover')
    expect(target.redirected).toBe(true)
    expect(target.reason).toMatch(/dry run/i)
  })

  it('sends a reload on Mapping with no job to Connect, because the mapping is held in the page only', () => {
    const target = stepTarget('mapping', freshState(), [])

    expect(target.step).toBe('connect')
    expect(target.redirected).toBe(true)
  })

  it('lets the jobs decide the step of a reload during a sync', () => {
    const target = stepTarget('dry-run', freshState(), [job(2, 'IMPORT', 'RUNNING')])

    expect(target.step).toBe('sync')
    expect(target.redirected).toBe(true)
  })
})

describe('the fingerprint of the mapping', () => {
  it('does not change when the rows are given in another order', () => {
    const a = plan([row('a', 'a-ingot'), row('b', 'b-ingot')])
    const b = plan([row('b', 'b-ingot'), row('a', 'a-ingot')])

    expect(planFingerprint(a)).toBe(planFingerprint(b))
  })

  it('changes with a target name, with the selection and with the conflict policy', () => {
    const base = planFingerprint(plan([row('a', 'a')]))

    expect(planFingerprint(plan([row('a', 'a-renamed')]))).not.toBe(base)
    expect(planFingerprint(plan([row('a', 'a', false)]))).not.toBe(base)
    expect(planFingerprint(plan([row('a', 'a')], 'overwrite'))).not.toBe(base)
  })

  it('makes an acceptance stale when the mapping changes after it was given', () => {
    const accepted = { dryRunJobId: 4, fingerprint: planFingerprint(plan([row('a')])) }

    expect(acceptanceIsCurrent(accepted, plan([row('a')]), 4)).toBe(true)
    expect(acceptanceIsCurrent(accepted, plan([row('a', 'a-nexus')]), 4)).toBe(false)
    expect(acceptanceIsCurrent(accepted, plan([row('a')]), 5), 'another dry run needs its own acceptance').toBe(false)
    expect(acceptanceIsCurrent(null, plan([row('a')]), 4)).toBe(false)
  })
})

describe('the warnings of a mapping', () => {
  it('warns about an empty target name on the row it belongs to', () => {
    const warnings = mappingWarnings([row('a', '  ')])

    expect(warnings).toEqual([{ nexusName: 'a', kind: 'empty' }])
  })

  it('warns about every row that shares a target name with another row', () => {
    const warnings = mappingWarnings([row('a', 'shared'), row('b', 'shared'), row('c', 'own')])

    expect(warnings).toEqual([
      { nexusName: 'a', kind: 'duplicate' },
      { nexusName: 'b', kind: 'duplicate' },
    ])
  })

  it('ignores an excluded row, because it will not be written', () => {
    expect(mappingWarnings([row('a', 'shared'), row('b', 'shared', false)])).toEqual([])
  })
})

describe('the blockers of a sync start', () => {
  const accepted = (rows) => ({ dryRunJobId: 4, fingerprint: planFingerprint(plan(rows)) })
  const finishedDryRun = (blockers = []) => job(4, 'DRY_RUN', 'COMPLETED', { summary: { transferBytes: 10, formats: {}, blockers } })

  it('names every reason that keeps Sync disabled', () => {
    const rows = [row('a')]

    expect(syncBlockers({ dryRun: null, acceptance: null, rows }).reasons).toEqual(
      expect.arrayContaining([expect.stringMatching(/dry run/i)]),
    )
    expect(syncBlockers({ dryRun: finishedDryRun(), acceptance: null, rows }).reasons).toEqual(
      expect.arrayContaining([expect.stringMatching(/accept/i)]),
    )
  })

  it('is empty when the report is accepted for the current mapping and has no blocker', () => {
    const rows = [row('a')]

    const result = syncBlockers({ dryRun: finishedDryRun(), acceptance: accepted(rows), rows, conflictPolicy: 'refuse' })

    expect(result).toEqual({ ok: true, reasons: [] })
  })

  it('lists the blockers of the dry run and does not allow Sync', () => {
    const rows = [row('a')]

    const result = syncBlockers({ dryRun: finishedDryRun(['npm is not supported']), acceptance: accepted(rows), rows })

    expect(result.ok).toBe(false)
    expect(result.reasons).toContain('npm is not supported')
  })

  it('says that the acceptance is stale after the mapping changed', () => {
    const rows = [row('a')]
    const changed = [row('a', 'a-nexus')]

    const result = syncBlockers({ dryRun: finishedDryRun(), acceptance: accepted(rows), rows: changed })

    expect(result.ok).toBe(false)
    expect(result.reasons).toEqual(expect.arrayContaining([expect.stringMatching(/changed|stale/i)]))
  })

  it('says that no repository is included', () => {
    const rows = [row('a', 'a', false)]

    const result = syncBlockers({ dryRun: finishedDryRun(), acceptance: accepted(rows), rows })

    expect(result.reasons).toEqual(expect.arrayContaining([expect.stringMatching(/include at least one/i)]))
  })
})
