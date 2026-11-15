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

import type { components } from '../../types/api'
import { formatBytes } from './format'

type ApiStorageUsage = components['schemas']['StorageUsageResponse']
type ApiRepositoryUsage = components['schemas']['RepositoryStorageUsage']

// A repository row as the endpoint sends it. The backend leaves out null fields, so only the id is required.
export type RepositoryUsageWire = Pick<ApiRepositoryUsage, 'id'> & Partial<ApiRepositoryUsage>

export type StorageUsageWire = Partial<Omit<ApiStorageUsage, 'repositories'>> & {
  repositories?: RepositoryUsageWire[]
}

export interface RepositoryUsage {
  id: string
  usedBytes: number
  quota: string | null
  quotaBytes: number | null
  fileCount: number
  storageType: string | null
  group: boolean
}

export interface StorageUsage {
  computedAt: string | null
  computing: boolean
  totalBytes: number
  repositories: RepositoryUsage[]
}

// The fields that decide whether a repository has a quota at all.
export type RepositoryKind = Pick<RepositoryUsage, 'storageType' | 'group'>

// The backend leaves out null fields, so every nullable value is read with `?? null`.
export const normalizeStorage = (wire: StorageUsageWire | undefined): StorageUsage => ({
  computedAt: wire?.computedAt ?? null,
  computing: wire?.computing ?? false,
  totalBytes: wire?.totalBytes ?? 0,
  repositories: (wire?.repositories ?? []).map((row: RepositoryUsageWire): RepositoryUsage => ({
    id: row.id,
    usedBytes: row.usedBytes ?? 0,
    quota: row.quota ?? null,
    quotaBytes: row.quotaBytes ?? null,
    fileCount: row.fileCount ?? 0,
    storageType: row.storageType ?? null,
    group: row.group === true
  }))
})

/*
 * Only a file system repository has a quota. Object storage has none, and a loopback group stores nothing
 * itself, so neither gets a quota, a bar or a limit.
 */
export const hasQuota = (row: RepositoryKind): boolean => row.storageType === 'fs' && !row.group

/*
 * The share of the quota in use, between 0 and 1, or null when no bar can be drawn: the quota
 * is unknown in bytes, or it is zero. Usage above the quota is shown as a full bar.
 */
export const quotaFraction = (
  usedBytes: number | null | undefined,
  quotaBytes: number | null | undefined
): number | null => {
  if (quotaBytes == null || quotaBytes <= 0) return null
  const used = Math.max(0, usedBytes ?? 0)
  return Math.min(1, used / quotaBytes)
}

/*
 * The line under a repository's name: the used size, then what limits it in words. A group says it has no
 * own storage, object storage shows the size alone, and a file system repository shows its quota or "no limit".
 */
export const usageLine = (row: Pick<RepositoryUsage, 'usedBytes' | 'quota'> & RepositoryKind): string => {
  const used = `${formatBytes(row.usedBytes)} used`
  if (row.group) return `${used}, group, no own storage`
  if (!hasQuota(row)) return used
  return `${used}, ${row.quota ? `quota ${row.quota}` : 'no limit'}`
}

export const quotaNote = (row: Pick<RepositoryUsage, 'quota' | 'quotaBytes'> & RepositoryKind): string | null => {
  if (!hasQuota(row) || row.quotaBytes != null) return null
  return row.quota == null
    ? 'No limit is set, so there is no bar.'
    : 'The quota size is not known, so there is no bar.'
}
