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
import { quotaFraction, quotaLabel, quotaNote, normalizeStorage } from './storage'

describe('quotaFraction', () => {
  it('is the share of the quota that is used', () => {
    expect(quotaFraction(250, 1000)).toBe(0.25)
  })

  it('is zero for a repository with nothing stored', () => {
    expect(quotaFraction(0, 1000)).toBe(0)
  })

  it('is capped at one when usage is above the quota', () => {
    expect(quotaFraction(2000, 1000)).toBe(1)
  })

  it('is null when the quota is not known in bytes, so no bar is drawn', () => {
    expect(quotaFraction(250, null)).toBeNull()
  })

  it('is null for a zero quota, which cannot be a share', () => {
    expect(quotaFraction(250, 0)).toBeNull()
  })

  it('treats a missing usage figure as nothing used', () => {
    expect(quotaFraction(undefined, 1000)).toBe(0)
  })
})

describe('quotaLabel', () => {
  it('shows the quota as the server wrote it', () => {
    expect(quotaLabel('10GB')).toBe('10GB')
  })

  it('shows Unlimited when the repository has no quota', () => {
    expect(quotaLabel(null)).toBe('Unlimited')
  })

  it('shows Unlimited when the server left the quota out', () => {
    expect(quotaLabel(undefined)).toBe('Unlimited')
  })
})

describe('quotaNote', () => {
  it('explains that no bar is drawn when no quota is set at all', () => {
    expect(quotaNote({ quota: null, quotaBytes: null })).toBe('No quota is set, so there is no bar.')
  })

  it('explains that the quota size is not known when only the label is there', () => {
    expect(quotaNote({ quota: '10GB', quotaBytes: null })).toBe('The quota size is not known, so there is no bar.')
  })

  it('has nothing to add when the bar can be drawn', () => {
    expect(quotaNote({ quota: '100%', quotaBytes: 5000 })).toBeNull()
  })
})

describe('normalizeStorage', () => {
  it('reads a missing computedAt as not computed yet', () => {
    expect(normalizeStorage({ repositories: [] }).computedAt).toBeNull()
  })

  it('reads a missing computing flag as idle', () => {
    expect(normalizeStorage({ repositories: [] }).computing).toBe(false)
  })

  it('reads a missing repository list as empty', () => {
    expect(normalizeStorage({ computedAt: null }).repositories).toEqual([])
  })

  it('reads missing quota fields of a repository as null', () => {
    const [row] = normalizeStorage({ repositories: [{ id: 'releases', usedBytes: 10, fileCount: 1 }] }).repositories

    expect(row).toEqual({ id: 'releases', usedBytes: 10, quota: null, quotaBytes: null, fileCount: 1 })
  })
})
