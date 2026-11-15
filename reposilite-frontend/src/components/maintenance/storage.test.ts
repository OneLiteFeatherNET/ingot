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
import { quotaFraction, quotaNote, normalizeStorage, usageLine, hasQuota } from './storage'

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

describe('usageLine', () => {
  it('shows the used size and the configured quota of a file system repository', () => {
    expect(usageLine({ usedBytes: 2048, storageType: 'fs', group: false, quota: '10GB' })).toBe('2.05 kB used, quota 10GB')
  })

  it('says there is no limit when a file system repository has no quota', () => {
    expect(usageLine({ usedBytes: 0, storageType: 'fs', group: false, quota: null })).toBe('0 B used, no limit')
  })

  it('shows only the used size for object storage, which has no quota', () => {
    expect(usageLine({ usedBytes: 2048, storageType: 's3', group: false, quota: null })).toBe('2.05 kB used')
  })

  it('says a group has no own storage instead of showing a quota', () => {
    expect(usageLine({ usedBytes: 0, storageType: 'fs', group: true, quota: '100%' })).toBe('0 B used, group, no own storage')
  })
})

describe('hasQuota', () => {
  it('is true for a file system repository that stores its own content', () => {
    expect(hasQuota({ storageType: 'fs', group: false })).toBe(true)
  })

  it('is false for object storage, which has no quota to draw', () => {
    expect(hasQuota({ storageType: 's3', group: false })).toBe(false)
  })

  it('is false for a group, which has no quota of its own', () => {
    expect(hasQuota({ storageType: 'fs', group: true })).toBe(false)
  })
})

describe('quotaNote', () => {
  it('explains that no bar is drawn when a file system repository has no limit at all', () => {
    expect(quotaNote({ storageType: 'fs', group: false, quota: null, quotaBytes: null })).toBe('No limit is set, so there is no bar.')
  })

  it('explains that the quota size is not known when only the label is there', () => {
    expect(quotaNote({ storageType: 'fs', group: false, quota: '10GB', quotaBytes: null })).toBe('The quota size is not known, so there is no bar.')
  })

  it('has nothing to say about object storage, where no quota applies', () => {
    expect(quotaNote({ storageType: 's3', group: false, quota: null, quotaBytes: null })).toBeNull()
  })

  it('has nothing to say about a group, which has no quota', () => {
    expect(quotaNote({ storageType: 'fs', group: true, quota: null, quotaBytes: null })).toBeNull()
  })

  it('has nothing to add when the bar can be drawn', () => {
    expect(quotaNote({ storageType: 'fs', group: false, quota: '100%', quotaBytes: 5000 })).toBeNull()
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

    expect(row).toEqual({ id: 'releases', usedBytes: 10, quota: null, quotaBytes: null, fileCount: 1, storageType: null, group: false })
  })

  it('keeps the storage type and the group flag the server reports', () => {
    const [row] = normalizeStorage({ repositories: [{ id: 'mirror', usedBytes: 0, fileCount: 0, storageType: 's3', group: true }] }).repositories

    expect(row.storageType).toBe('s3')
    expect(row.group).toBe(true)
  })
})
