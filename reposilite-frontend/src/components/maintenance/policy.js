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

/*
 * Cleanup policies: the wire shape, the form draft the dialog edits, and the rules that keep a
 * policy from deleting more than it should. The server enforces the same rules; the checks here
 * only make the mistake visible before a save.
 */

export const POLICY_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/

export const SCOPES = [
  {
    value: 'SNAPSHOTS',
    label: 'Snapshots',
    help: 'Snapshot versions (those ending in -SNAPSHOT) in the selected repositories.'
  },
  {
    value: 'RELEASES',
    label: 'Releases',
    help: 'Release versions. A deleted release cannot be restored, so keep the limits tight.'
  },
  {
    value: 'MIRROR_CACHE',
    label: 'Mirror cache',
    help: 'Artifacts cached from a proxied remote. They are fetched again on the next request.'
  }
]

const RETENTION_MESSAGE = 'Set at least one of keep latest, older than or not downloaded for. Without one, the policy would delete everything.'
const NO_REPOSITORY_WARNING = 'This policy affects no repository. Select at least one, or it will do nothing.'

export const scopeLabel = (scope) => SCOPES.find(entry => entry.value === scope)?.label ?? scope

export const scopeHelp = (scope) => SCOPES.find(entry => entry.value === scope)?.help ?? ''

export const scheduleLabel = (schedule) =>
  !schedule || schedule === 'manual' ? 'Manual only' : `Daily at ${schedule}`

const days = (count) => `${count} ${count === 1 ? 'day' : 'days'}`

export const retentionSummary = (policy) => {
  const parts = []
  if (policy.keepLatest != null) parts.push(`keep latest ${policy.keepLatest}`)
  if (policy.olderThanDays != null) parts.push(`older than ${days(policy.olderThanDays)}`)
  if (policy.notDownloadedForDays != null) parts.push(`not downloaded for ${days(policy.notDownloadedForDays)}`)
  return parts.join(' · ') || 'no limit set'
}

/*
 * Reads a policy from the server. The backend leaves out null fields, so every missing value
 * is read as null (or the default the form needs). Fields the dashboard does not know are kept,
 * because the whole list is written back and a field must not disappear on the way.
 */
export const normalizePolicy = (wire) => ({
  ...wire,
  id: wire.id,
  enabled: wire.enabled ?? false,
  repositories: wire.repositories ?? [],
  scope: wire.scope ?? 'SNAPSHOTS',
  keepLatest: wire.keepLatest ?? null,
  olderThanDays: wire.olderThanDays ?? null,
  notDownloadedForDays: wire.notDownloadedForDays ?? null,
  groupFilter: wire.groupFilter ?? null,
  schedule: wire.schedule ?? 'manual'
})

/**
 * What a count field holds: null for blank, the number for a valid text or number, NaN for
 * text that is not a number. The check against the rules is countError.
 */
export const countValue = (value) => {
  if (value === null || value === undefined) return null
  if (typeof value === 'number') return value
  const text = String(value).trim()
  if (text === '') return null
  return /^\d+$/.test(text) ? Number(text) : NaN
}

export const countError = (value) => {
  const count = countValue(value)
  if (count === null) return null
  return Number.isInteger(count) && count >= 1 ? null : 'Enter a whole number of 1 or more.'
}

export const validatePolicyId = (id, { existingIds = [], editing = false } = {}) => {
  const trimmed = (id ?? '').trim()
  if (trimmed === '') return 'Enter an id.'
  if (!POLICY_ID_PATTERN.test(trimmed)) {
    return 'Use 1 to 64 letters, digits, dots, hyphens or underscores. Start with a letter or a digit.'
  }
  if (!editing && existingIds.includes(trimmed)) return 'Another policy already uses this id.'
  return null
}

/*
 * A policy needs at least one limit. Only a blank field counts as unset: a field that holds
 * something invalid is reported by its own count error, not as a missing limit.
 */
export const retentionError = (draft) => {
  const unset = [draft.keepLatest, draft.olderThanDays, draft.notDownloadedForDays]
    .every(value => countValue(value) === null)
  return unset ? RETENTION_MESSAGE : null
}

export const groupFilterError = (value) => {
  if (!value || value.trim() === '') return null
  try {
    new RegExp(value)
    return null
  } catch {
    return 'Not a valid regular expression.'
  }
}

export const scheduleError = (mode, time) => {
  if (mode !== 'daily') return null
  return TIME_PATTERN.test(time ?? '') ? null : 'Enter a time as HH:mm, in the server local time.'
}

// A policy with no repository is allowed to save, but it does nothing, so the dialog says so.
export const policyWarnings = (draft) =>
  draft.repositories.length === 0 ? [NO_REPOSITORY_WARNING] : []

/*
 * The errors of a draft, keyed by the field they belong to. An empty object means the draft can
 * be saved. `editing` is set for a policy that already exists: its id cannot change, so it is not
 * checked against the other ids.
 */
export const validateDraft = (draft, { existingIds = [], editing = false } = {}) => {
  const errors = {}
  const add = (key, message) => {
    if (message) errors[key] = message
  }
  add('id', validatePolicyId(draft.id, { existingIds, editing }))
  add('keepLatest', countError(draft.keepLatest))
  add('olderThanDays', countError(draft.olderThanDays))
  add('notDownloadedForDays', countError(draft.notDownloadedForDays))
  add('retention', retentionError(draft))
  add('groupFilter', groupFilterError(draft.groupFilter))
  add('schedule', scheduleError(draft.scheduleMode, draft.scheduleTime))
  return errors
}

export const emptyDraft = () => ({
  id: '',
  enabled: false,
  repositories: [],
  scope: 'SNAPSHOTS',
  keepLatest: '',
  olderThanDays: '',
  notDownloadedForDays: '',
  groupFilter: '',
  scheduleMode: 'manual',
  scheduleTime: ''
})

const text = (value) => (value === null || value === undefined ? '' : String(value))

export const draftFromPolicy = (wire) => {
  const policy = normalizePolicy(wire)
  const daily = policy.schedule !== 'manual'
  return {
    id: policy.id,
    enabled: policy.enabled,
    repositories: [...policy.repositories],
    scope: policy.scope,
    keepLatest: text(policy.keepLatest),
    olderThanDays: text(policy.olderThanDays),
    notDownloadedForDays: text(policy.notDownloadedForDays),
    groupFilter: text(policy.groupFilter),
    scheduleMode: daily ? 'daily' : 'manual',
    scheduleTime: daily ? policy.schedule : ''
  }
}

const countOrNull = (value) => {
  const count = countValue(value)
  return count === null || Number.isNaN(count) ? null : count
}

// The wire shape of a draft. Call it after validateDraft has passed.
export const policyFromDraft = (draft) => ({
  id: draft.id.trim(),
  enabled: draft.enabled,
  repositories: [...draft.repositories],
  scope: draft.scope,
  keepLatest: countOrNull(draft.keepLatest),
  olderThanDays: countOrNull(draft.olderThanDays),
  notDownloadedForDays: countOrNull(draft.notDownloadedForDays),
  groupFilter: draft.groupFilter.trim() === '' ? null : draft.groupFilter,
  schedule: draft.scheduleMode === 'daily' ? draft.scheduleTime : 'manual'
})
