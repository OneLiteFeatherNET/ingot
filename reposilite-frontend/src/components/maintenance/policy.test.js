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
  validatePolicyId,
  countValue,
  countError,
  retentionError,
  groupFilterError,
  scheduleError,
  policyWarnings,
  validateDraft,
  emptyDraft,
  draftFromPolicy,
  policyFromDraft,
  normalizePolicy,
  scopeLabel,
  scheduleLabel,
  retentionSummary
} from './policy'

const draft = (overrides = {}) => ({ ...emptyDraft(), id: 'snapshots-weekly', keepLatest: '3', repositories: ['snapshots'], ...overrides })

describe('validatePolicyId', () => {
  it('requires an id', () => {
    expect(validatePolicyId('')).toBe('Enter an id.')
  })

  it('accepts a plain id', () => {
    expect(validatePolicyId('snapshots-weekly')).toBeNull()
  })

  it('accepts dots and underscores after the first character', () => {
    expect(validatePolicyId('a.b_c-1')).toBeNull()
  })

  it('rejects an id that starts with a hyphen', () => {
    expect(validatePolicyId('-weekly')).not.toBeNull()
  })

  it('rejects an id with a space', () => {
    expect(validatePolicyId('my policy')).not.toBeNull()
  })

  it('rejects an id with a slash', () => {
    expect(validatePolicyId('net/x')).not.toBeNull()
  })

  it('accepts an id of 64 characters', () => {
    expect(validatePolicyId('a'.repeat(64))).toBeNull()
  })

  it('rejects an id of 65 characters', () => {
    expect(validatePolicyId('a'.repeat(65))).not.toBeNull()
  })

  it('rejects an id that another policy already uses', () => {
    expect(validatePolicyId('snapshots-weekly', { existingIds: ['snapshots-weekly'] })).toBe('Another policy already uses this id.')
  })

  it('does not count the policy being edited as a duplicate of itself', () => {
    expect(validatePolicyId('snapshots-weekly', { existingIds: ['snapshots-weekly'], editing: true })).toBeNull()
  })

  it('ignores surrounding spaces when it checks the format', () => {
    expect(validatePolicyId('  snapshots-weekly  ')).toBeNull()
  })
})

describe('countValue and countError', () => {
  it('reads a blank count as no limit', () => {
    expect(countValue('')).toBeNull()
  })

  it('reads a blank count as no error', () => {
    expect(countError('')).toBeNull()
  })

  it('reads a numeric text as a number', () => {
    expect(countValue('3')).toBe(3)
  })

  it('reads a number as itself', () => {
    expect(countValue(30)).toBe(30)
  })

  it('rejects zero, which would match every version', () => {
    expect(countError('0')).not.toBeNull()
  })

  it('rejects a negative count', () => {
    expect(countError('-2')).not.toBeNull()
  })

  it('rejects a fraction', () => {
    expect(countError('2.5')).not.toBeNull()
  })

  it('rejects text that is not a number', () => {
    expect(countError('three')).not.toBeNull()
  })
})

describe('retentionError', () => {
  it('rejects a policy with no limit, because it would delete everything', () => {
    expect(retentionError(draft({ keepLatest: '', olderThanDays: '', notDownloadedForDays: '' })))
      .toBe('Set at least one of keep latest, older than or not downloaded for. Without one, the policy would delete everything.')
  })

  it('accepts keep latest on its own', () => {
    expect(retentionError(draft({ keepLatest: '3', olderThanDays: '', notDownloadedForDays: '' }))).toBeNull()
  })

  it('accepts older than on its own', () => {
    expect(retentionError(draft({ keepLatest: '', olderThanDays: '30', notDownloadedForDays: '' }))).toBeNull()
  })

  it('accepts not downloaded for on its own', () => {
    expect(retentionError(draft({ keepLatest: '', olderThanDays: '', notDownloadedForDays: '60' }))).toBeNull()
  })
})

describe('groupFilterError', () => {
  it('accepts a blank filter, which matches every path', () => {
    expect(groupFilterError('')).toBeNull()
  })

  it('accepts a regular expression over the artifact path', () => {
    expect(groupFilterError('net/onelitefeather/.*')).toBeNull()
  })

  it('rejects a regular expression that does not compile', () => {
    expect(groupFilterError('net/(')).not.toBeNull()
  })
})

describe('scheduleError', () => {
  it('needs no time for a manual policy', () => {
    expect(scheduleError('manual', '')).toBeNull()
  })

  it('accepts a daily time in HH:mm', () => {
    expect(scheduleError('daily', '03:30')).toBeNull()
  })

  it('rejects an hour past 23', () => {
    expect(scheduleError('daily', '24:00')).not.toBeNull()
  })

  it('rejects a time without a leading zero', () => {
    expect(scheduleError('daily', '3:30')).not.toBeNull()
  })

  it('rejects a daily policy without a time', () => {
    expect(scheduleError('daily', '')).not.toBeNull()
  })
})

describe('policyWarnings', () => {
  it('warns that a policy without repositories affects nothing', () => {
    expect(policyWarnings(draft({ repositories: [] }))).toEqual(['This policy affects no repository. Select at least one, or it will do nothing.'])
  })

  it('has no warning once a repository is selected', () => {
    expect(policyWarnings(draft({ repositories: ['snapshots'] }))).toEqual([])
  })
})

describe('validateDraft', () => {
  it('reports no error for a complete draft', () => {
    expect(validateDraft(draft(), { existingIds: [] })).toEqual({})
  })

  it('reports the id error under the id field', () => {
    expect(validateDraft(draft({ id: '' }), { existingIds: [] }).id).toBe('Enter an id.')
  })

  it('reports the count error under the field that holds it', () => {
    expect(validateDraft(draft({ olderThanDays: '0' }), { existingIds: [] }).olderThanDays).not.toBeUndefined()
  })

  it('reports the missing limit under the retention key', () => {
    expect(validateDraft(draft({ keepLatest: '' }), { existingIds: [] }).retention).not.toBeUndefined()
  })

  it('reports a bad schedule under the schedule key', () => {
    expect(validateDraft(draft({ scheduleMode: 'daily', scheduleTime: '' }), { existingIds: [] }).schedule).not.toBeUndefined()
  })
})

describe('draft and policy conversion', () => {
  it('turns a stored policy into the text the form shows', () => {
    const form = draftFromPolicy(normalizePolicy({ id: 'a', keepLatest: 3, olderThanDays: null, schedule: '03:30' }))

    expect(form.keepLatest).toBe('3')
    expect(form.olderThanDays).toBe('')
    expect(form.scheduleMode).toBe('daily')
    expect(form.scheduleTime).toBe('03:30')
  })

  it('turns a manual schedule into the manual mode', () => {
    expect(draftFromPolicy(normalizePolicy({ id: 'a', schedule: 'manual' })).scheduleMode).toBe('manual')
  })

  it('writes counts as numbers and blanks as null', () => {
    const policy = policyFromDraft(draft({ keepLatest: '3', olderThanDays: '', notDownloadedForDays: '' }))

    expect(policy.keepLatest).toBe(3)
    expect(policy.olderThanDays).toBeNull()
    expect(policy.notDownloadedForDays).toBeNull()
  })

  it('writes a blank group filter as null', () => {
    expect(policyFromDraft(draft({ groupFilter: '   ' })).groupFilter).toBeNull()
  })

  it('writes the schedule as manual or as the daily time', () => {
    expect(policyFromDraft(draft({ scheduleMode: 'daily', scheduleTime: '03:30' })).schedule).toBe('03:30')
    expect(policyFromDraft(draft({ scheduleMode: 'manual' })).schedule).toBe('manual')
  })

  it('trims the id it writes', () => {
    expect(policyFromDraft(draft({ id: ' snapshots-weekly ' })).id).toBe('snapshots-weekly')
  })
})

describe('normalizePolicy', () => {
  it('reads every missing limit as null', () => {
    const policy = normalizePolicy({ id: 'a' })

    expect(policy.keepLatest).toBeNull()
    expect(policy.olderThanDays).toBeNull()
    expect(policy.notDownloadedForDays).toBeNull()
    expect(policy.groupFilter).toBeNull()
  })

  it('reads a missing enabled flag as off', () => {
    expect(normalizePolicy({ id: 'a' }).enabled).toBe(false)
  })

  it('reads a missing schedule as manual', () => {
    expect(normalizePolicy({ id: 'a' }).schedule).toBe('manual')
  })

  it('reads missing repositories as none', () => {
    expect(normalizePolicy({ id: 'a' }).repositories).toEqual([])
  })
})

describe('labels', () => {
  it('names each scope for people', () => {
    expect(scopeLabel('MIRROR_CACHE')).toBe('Mirror cache')
  })

  it('shows the scope text as it was written when it is unknown', () => {
    expect(scopeLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW')
  })

  it('describes a manual schedule as manual only', () => {
    expect(scheduleLabel('manual')).toBe('Manual only')
  })

  it('describes a daily schedule with its time', () => {
    expect(scheduleLabel('03:30')).toBe('Daily at 03:30')
  })

  it('summarises the limits of a policy in one line', () => {
    expect(retentionSummary(normalizePolicy({ id: 'a', keepLatest: 3, olderThanDays: 30 })))
      .toBe('keep latest 3 · older than 30 days')
  })
})
