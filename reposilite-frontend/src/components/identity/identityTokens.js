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

import { now } from '../../helpers/clock'

/*
 * The rules of a token form that the self service page and the admin pages share: the expiry
 * presets, the scopes a user may pick, the name check, the request body and the date text.
 * Nothing here reads the system time; the expiry is counted from the injected clock.
 */

const DAY_MS = 24 * 60 * 60 * 1000

const EXPIRY_PRESETS = [
  { key: '30', label: '30 days', days: 30 },
  { key: '90', label: '90 days', days: 90 },
  { key: '365', label: '365 days', days: 365 },
  { key: 'never', label: 'Never', days: null }
]

const DEFAULT_EXPIRY = '90'

// The backend actions a token may be narrowed to. DEPLOY is a team role, not a scope.
const SCOPE_OPTIONS = [
  { action: 'READ', label: 'Read' },
  { action: 'WRITE', label: 'Write' }
]

const MAX_NAME_LENGTH = 255

/** The expiry date for a preset as an ISO string, or null for a token that never expires. */
const expiryFor = (key, at = now()) => {
  const days = EXPIRY_PRESETS.find(preset => preset.key === key)?.days ?? null
  return days === null ? null : new Date(at.getTime() + days * DAY_MS).toISOString()
}

/** The message for a name that cannot be used, or an empty string. */
const validateTokenName = (name) => {
  const value = String(name ?? '').trim()
  if (value === '') return 'Token name is required'
  if (value.includes(':')) return "Token name must not contain ':'"
  if (value.length > MAX_NAME_LENGTH) return `Token name must be at most ${MAX_NAME_LENGTH} characters`
  return ''
}

/**
 * The body of a create request. No checked scope means no narrowing, which the server reads
 * as null; a token that never expires carries expiresAt null.
 */
const buildCreateBody = ({ name, description = '', preset, scopes = [] }) => {
  const body = { name: String(name).trim() }
  const text = String(description ?? '').trim()
  if (text !== '') body.description = text
  body.expiresAt = expiryFor(preset)
  body.scopes = scopes.length > 0 ? [...scopes] : null
  return body
}

const toDate = (value) => {
  if (Array.isArray(value)) {
    const [year, month, day] = value
    return new Date(Date.UTC(year, month - 1, day))
  }
  return new Date(value)
}

const pad = (number) => String(number).padStart(2, '0')

/** A UTC calendar date such as 2026-10-15, whatever shape the server sent. */
const formatDate = (value) => {
  const date = toDate(value)
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
}

/** A UTC date and time such as 2026-10-15 09:05 UTC. */
const formatDateTime = (value) => {
  const date = toDate(value)
  return `${formatDate(value)} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())} UTC`
}

const RELATIVE_UNITS = [
  { unit: 'year', ms: 365 * DAY_MS },
  { unit: 'month', ms: 30 * DAY_MS },
  { unit: 'day', ms: DAY_MS },
  { unit: 'hour', ms: 60 * 60 * 1000 },
  { unit: 'minute', ms: 60 * 1000 }
]

/** How long ago a moment was, such as "3 days ago", counted from the injected clock. */
const formatRelative = (value, at = now()) => {
  const elapsed = at.getTime() - toDate(value).getTime()
  const match = RELATIVE_UNITS.find(entry => elapsed >= entry.ms)
  if (!match) return 'just now'
  const count = Math.floor(elapsed / match.ms)
  return `${count} ${match.unit}${count === 1 ? '' : 's'} ago`
}

export {
  DEFAULT_EXPIRY,
  EXPIRY_PRESETS,
  MAX_NAME_LENGTH,
  SCOPE_OPTIONS,
  buildCreateBody,
  expiryFor,
  formatDate,
  formatDateTime,
  formatRelative,
  validateTokenName
}
