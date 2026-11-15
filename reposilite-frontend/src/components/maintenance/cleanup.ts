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

// Run records and dry-run previews as the cleanup endpoints send them.

const STATUS_LABELS = {
  SUCCEEDED: 'Succeeded',
  PARTIAL: 'Partly failed',
  FAILED: 'Failed'
}

const TRIGGER_LABELS = {
  MANUAL: 'Manual',
  SCHEDULED: 'Scheduled'
}

export const statusLabel = (status) => STATUS_LABELS[status] ?? status ?? 'Unknown'

export const triggerLabel = (trigger) => TRIGGER_LABELS[trigger] ?? trigger ?? 'Unknown'

export const normalizeRun = (wire) => ({
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
export const sortNewestFirst = (runs) =>
  [...runs].sort((a, b) => Date.parse(b.startedAt) - Date.parse(a.startedAt))

export const lastRunOf = (runs, policyId) =>
  sortNewestFirst(runs.filter(run => run.policyId === policyId))[0] ?? null

export const normalizeDryRun = (wire) => {
  const deletions = (wire.deletions ?? []).map(row => ({
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

export const isNothingToDelete = (preview) => preview.count === 0
