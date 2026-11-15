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

// Run records and dry-run previews as the cleanup endpoints send them.

type ApiCleanupRun = components['schemas']['CleanupRunResponse']
type ApiCleanupPreview = components['schemas']['CleanupPreviewResponse']
type ApiPlannedDeletion = components['schemas']['PlannedDeletion']
type CleanupRunStatus = components['schemas']['CleanupRunStatus']
type CleanupTrigger = components['schemas']['CleanupTrigger']

// A run as the endpoint sends it. The backend leaves out null fields, so only the identity is required here.
export type CleanupRunWire = Pick<ApiCleanupRun, 'id' | 'policyId'> & Partial<ApiCleanupRun>

export interface CleanupRun {
  id: string
  policyId: string
  trigger: CleanupTrigger
  startedAt: string | null
  finishedAt: string | null
  status: CleanupRunStatus
  deletedCount: number
  deletedBytes: number
  failures: string[]
}

export type CleanupPreviewWire = Pick<ApiCleanupPreview, 'policyId'> & Partial<ApiCleanupPreview>

export interface PlannedDeletionRow {
  repository: string
  path: string
  version: string | null
  sizeBytes: number
  reason: string
}

export interface CleanupPreview {
  policyId: string
  evaluatedAt: string | null
  count: number
  totalBytes: number
  deletions: PlannedDeletionRow[]
  warnings: string[]
}

const STATUS_LABELS: Partial<Record<string, string>> = {
  SUCCEEDED: 'Succeeded',
  PARTIAL: 'Partly failed',
  FAILED: 'Failed'
}

const TRIGGER_LABELS: Partial<Record<string, string>> = {
  MANUAL: 'Manual',
  SCHEDULED: 'Scheduled'
}

export const statusLabel = (status: string | null | undefined): string =>
  status == null ? 'Unknown' : (STATUS_LABELS[status] ?? status)

export const triggerLabel = (trigger: string | null | undefined): string =>
  trigger == null ? 'Unknown' : (TRIGGER_LABELS[trigger] ?? trigger)

export const normalizeRun = (wire: CleanupRunWire): CleanupRun => ({
  id: wire.id,
  policyId: wire.policyId,
  trigger: wire.trigger ?? 'MANUAL',
  startedAt: wire.startedAt ?? null,
  finishedAt: wire.finishedAt ?? null,
  status: wire.status ?? 'FAILED',
  deletedCount: wire.deletedCount ?? 0,
  deletedBytes: wire.deletedBytes ?? 0,
  failures: wire.failures ?? []
})

// The server sends the newest run first. The sort keeps the order right even when a list
// was merged or filtered on the way. It returns a copy and leaves the input alone.
export const sortNewestFirst = <T extends { startedAt: string | null }>(runs: readonly T[]): T[] =>
  [...runs].sort((a, b) => Date.parse(b.startedAt ?? '') - Date.parse(a.startedAt ?? ''))

export const lastRunOf = <T extends { policyId: string; startedAt: string | null }>(
  runs: readonly T[],
  policyId: string
): T | null =>
  sortNewestFirst(runs.filter(run => run.policyId === policyId))[0] ?? null

export const normalizeDryRun = (wire: CleanupPreviewWire): CleanupPreview => {
  const deletions = (wire.deletions ?? []).map((row: ApiPlannedDeletion): PlannedDeletionRow => ({
    repository: row.repository,
    path: row.path,
    version: row.version ?? null,
    sizeBytes: row.sizeBytes ?? 0,
    reason: row.reason ?? ''
  }))
  return {
    policyId: wire.policyId,
    evaluatedAt: wire.evaluatedAt ?? null,
    count: wire.count ?? deletions.length,
    totalBytes: wire.totalBytes ?? 0,
    deletions,
    warnings: wire.warnings ?? []
  }
}

export const isNothingToDelete = (preview: Pick<CleanupPreview, 'count'>): boolean => preview.count === 0
