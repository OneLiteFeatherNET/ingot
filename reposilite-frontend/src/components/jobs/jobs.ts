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

// The background runs as the Jobs endpoint sends them (GET /api/jobs and GET /api/jobs/{id}).

export const JOB_PAGE_SIZE = 50

export const JOB_STATES = ['QUEUED', 'RUNNING', 'PAUSED', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED'] as const
export const JOB_TRIGGERS = ['MANUAL', 'SCHEDULED', 'SYSTEM'] as const

const STATE_LABELS: Record<string, string> = {
  QUEUED: 'Queued',
  RUNNING: 'Running',
  PAUSED: 'Paused',
  SUCCEEDED: 'Succeeded',
  PARTIAL: 'Partly failed',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled'
}

const TRIGGER_LABELS: Record<string, string> = {
  MANUAL: 'Manual',
  SCHEDULED: 'Scheduled',
  SYSTEM: 'System'
}

export interface JobCount {
  key: string
  label: string
  value: number
}

export interface JobFailure {
  message: string
}

export interface JobRun {
  id: string
  source: string
  kind: string
  label: string
  trigger: string
  triggerDetail: string | null
  state: string
  startedAt: string
  finishedAt: string | null
  durationMs: number | null
  counts: JobCount[]
  failures: JobFailure[]
  failuresTruncated: boolean
}

export interface JobPage {
  items: JobRun[]
  nextCursor: string | null
  sourceErrors: string[]
  kinds: string[]
}

// The filters of the list. They live in the address, so an empty value means no filter.
export interface JobFilters {
  kind: string
  state: string
  trigger: string
}

export const stateLabel = (state: string): string => STATE_LABELS[state] ?? state

export const triggerLabel = (trigger: string): string => TRIGGER_LABELS[trigger] ?? trigger

const firstText = (value: unknown): string => {
  const single: unknown = Array.isArray(value) ? value[0] : value
  return typeof single === 'string' ? single : ''
}

// The filters read from the query of the address. An unknown key is ignored, and a repeated key takes its first value.
export const filtersFromQuery = (query: Record<string, unknown>): JobFilters => ({
  kind: firstText(query.kind),
  state: firstText(query.state),
  trigger: firstText(query.trigger)
})

// The query of the address for the filters, without the empty ones, so a clear filter leaves no key behind.
export const addressQueryOf = (filters: JobFilters): Record<string, string> =>
  Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== ''))

// The parameters of one page: the filters, the page size and the cursor of the page, when it is not the first.
export const listParams = (filters: JobFilters, cursor: string | null = null): Record<string, string> => ({
  ...addressQueryOf(filters),
  size: String(JOB_PAGE_SIZE),
  ...(cursor ? { cursor } : {})
})

export const durationText = (durationMs: number | null): string => {
  if (durationMs === null || durationMs === undefined) return ''
  if (durationMs < 1000) return 'under a second'
  return `${Math.round(durationMs / 1000)} s`
}
