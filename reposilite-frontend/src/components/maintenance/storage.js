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

// The backend leaves out null fields, so every nullable value is read with `?? null`.
export const normalizeStorage = (wire) => ({
  computedAt: wire?.computedAt ?? null,
  computing: wire?.computing ?? false,
  totalBytes: wire?.totalBytes ?? 0,
  repositories: (wire?.repositories ?? []).map(row => ({
    id: row.id,
    usedBytes: row.usedBytes ?? 0,
    quota: row.quota ?? null,
    quotaBytes: row.quotaBytes ?? null,
    fileCount: row.fileCount ?? 0
  }))
})

/*
 * The share of the quota in use, between 0 and 1, or null when no bar can be drawn: the quota
 * is unknown in bytes, or it is zero. Usage above the quota is shown as a full bar.
 */
export const quotaFraction = (usedBytes, quotaBytes) => {
  if (quotaBytes == null || quotaBytes <= 0) return null
  const used = Math.max(0, usedBytes ?? 0)
  return Math.min(1, used / quotaBytes)
}

export const quotaLabel = (quota) => quota ?? 'Unlimited'

export const quotaNote = (row) => {
  if (row.quotaBytes != null) return null
  return row.quota == null
    ? 'No quota is set, so there is no bar.'
    : 'The quota size is not known, so there is no bar.'
}
