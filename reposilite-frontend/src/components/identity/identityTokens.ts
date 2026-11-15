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
import { artifactIdProblem, groupIdProblem } from '../access/nodes/artifactNodes'

/*
 * The rules of a token form that the self service page and the admin pages share: the expiry
 * presets, the access choice, the name check, the custom date rules, the request body and the
 * date text. Nothing here reads the system time; every date is counted from the injected clock.
 */

const DAY_MS = 24 * 60 * 60 * 1000

// Custom expiry dates run from tomorrow (UTC) up to this many years ahead.
const CUSTOM_MAX_YEARS = 5

// A preset with days counts from now. 'custom' takes a date, and 'never' sends no expiry.
const EXPIRY_PRESETS = [
  { key: '30', label: '30 days', days: 30 },
  { key: '90', label: '90 days', days: 90 },
  { key: '365', label: '1 year', days: 365 },
  { key: 'custom', label: 'Custom', days: null },
  { key: 'never', label: 'No expiry', days: null }
]

const DEFAULT_EXPIRY = '90'

// What the token may do. 'all' is the owner's rights through their teams and sends no narrowing
// (scopes null); 'read' narrows the token to READ.
const ACCESS_OPTIONS = [
  { key: 'all', label: 'Same as my teams', help: 'Everything your teams allow', scopes: [] },
  { key: 'read', label: 'Read only', help: 'Download artifacts, no deploy', scopes: ['READ'] }
]

const DEFAULT_ACCESS = 'all'

const MAX_NAME_LENGTH = 255

const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/

// The actions an artifact limit can grant, in the order the nodes are written.
const ARTIFACT_ACTIONS = ['read', 'deploy', 'delete']
const REPOSITORY_NAME = /^[A-Za-z0-9_-]+$/

/** The expiry date for a preset as an ISO string, or null for a token that never expires. */
const expiryFor = (key, at = now()) => {
  const days = EXPIRY_PRESETS.find(preset => preset.key === key)?.days ?? null
  return days === null ? null : new Date(at.getTime() + days * DAY_MS).toISOString()
}

const utcDay = (date) => date.toISOString().slice(0, 10)

/** The earliest and latest date a custom expiry may take, as YYYY-MM-DD in UTC. */
const customDateLimits = (at = now()) => {
  const latest = new Date(at.getTime())
  latest.setUTCFullYear(latest.getUTCFullYear() + CUSTOM_MAX_YEARS)
  return {
    min: utcDay(new Date(at.getTime() + DAY_MS)),
    max: utcDay(latest)
  }
}

/** The message for a custom expiry date that cannot be used, or an empty string. */
const validateCustomDate = (value, at = now()) => {
  const text = String(value ?? '')
  if (text === '') return 'Choose an expiry date'
  if (!ISO_DAY.test(text) || new Date(`${text}T00:00:00Z`).toISOString().slice(0, 10) !== text) {
    return 'Enter a valid date'
  }
  const { min, max } = customDateLimits(at)
  if (text < min) return 'Choose a date from tomorrow on'
  if (text > max) return `Choose a date up to ${CUSTOM_MAX_YEARS} years from today`
  return ''
}

/** A custom date expires at the end of that day in UTC. */
const expiryForCustomDate = (value) => `${value}T23:59:59.000Z`

/** The message for a name that cannot be used, or an empty string. */
const validateTokenName = (name) => {
  const value = String(name ?? '').trim()
  if (value === '') return 'Token name is required'
  if (value.includes(':')) return "Token name must not contain ':'"
  if (value.length > MAX_NAME_LENGTH) return `Token name must be at most ${MAX_NAME_LENGTH} characters`
  return ''
}

/**
 * The node texts of an artifact limit: one node per action, qualified by the repository when one is given. Returns
 * { nodes, problems } with the messages of the fields; nodes is empty while there is a problem, and the problems are
 * the ones the fields show.
 */
const artifactNodeTexts = ({ repository = '', groupId = '', artifactId = '', actions = [] }) => {
  const repo = String(repository ?? '').trim()
  const group = String(groupId ?? '').trim()
  const artifact = String(artifactId ?? '').trim()
  const chosen = ARTIFACT_ACTIONS.filter(action => actions.includes(action))
  const problems = []
  if (repo !== '' && !REPOSITORY_NAME.test(repo)) problems.push("Use letters, digits, '_' and '-' in a repository name")
  for (const problem of [groupIdProblem(group), artifactIdProblem(artifact, group)]) {
    if (problem !== '') problems.push(problem)
  }
  if (chosen.length === 0) problems.push('Choose at least one action')
  if (problems.length > 0) return { nodes: [], problems }
  const coordinates = artifact === '' ? group : `${group}:${artifact}`
  const prefix = repo === '' ? 'ingot.artifact.' : `ingot.repository.${repo}.artifact.`
  return { nodes: chosen.map(action => `${prefix}${coordinates}.${action}`), problems: [] }
}

/** The scopes an access choice sends. An empty list means no narrowing. */
const scopesFor = (key) => [...(ACCESS_OPTIONS.find(option => option.key === key)?.scopes ?? [])]

/** The expiry as an ISO string, or null for no expiry. */
const expiresAtFor = ({ preset, customDate = '', at = now() }) => {
  if (preset === 'custom') return expiryForCustomDate(customDate)
  return expiryFor(preset, at)
}

/**
 * The body of a create request. A token that never expires carries expiresAt null, and no
 * access narrowing (no scopes chosen) is sent as scopes null. A custom preset without a valid
 * date is refused, so a missing date never turns into a token without expiry. Artifact nodes
 * (`nodes`) are sent as Allow entries; without them the body has no nodes field.
 */
const buildCreateBody = ({ name, description = '', preset, customDate = '', scopes = [], nodes = [], at = now() }) => {
  if (preset === 'custom' && validateCustomDate(customDate, at) !== '') {
    throw new Error('A custom expiry needs a valid date')
  }
  const body = { name: String(name).trim() }
  const text = String(description ?? '').trim()
  if (text !== '') body.description = text
  body.expiresAt = expiresAtFor({ preset, customDate, at })
  body.scopes = scopes.length > 0 ? [...scopes] : null
  if (nodes.length > 0) body.nodes = nodes.map(node => ({ node, allowed: true }))
  return body
}

const pad = (number) => String(number).padStart(2, '0')

/** A UTC calendar date such as 2026-10-15. Takes a Date: the wire value is read by helpers/apiTime first. */
const formatDate = (date) =>
  `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`

/** A UTC date and time such as 2026-10-15 09:05 UTC. */
const formatDateTime = (date) =>
  `${formatDate(date)} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())} UTC`

/**
 * A localised day such as "14 Jan 2027" (en-GB). The day is taken in UTC, the zone the token's
 * expiry is compared in, so every viewer sees the same date. An undefined locale uses the
 * browser default.
 */
const formatExpiryDate = (date, locale) => new Intl.DateTimeFormat(locale, {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC'
}).format(date)

const RELATIVE_UNITS = [
  { unit: 'year', ms: 365 * DAY_MS },
  { unit: 'month', ms: 30 * DAY_MS },
  { unit: 'day', ms: DAY_MS },
  { unit: 'hour', ms: 60 * 60 * 1000 },
  { unit: 'minute', ms: 60 * 1000 }
]

/** How long ago a moment was, such as "3 days ago", counted from the injected clock. */
const formatRelative = (date, at = now()) => {
  const elapsed = at.getTime() - date.getTime()
  const match = RELATIVE_UNITS.find(entry => elapsed >= entry.ms)
  if (!match) return 'just now'
  const count = Math.floor(elapsed / match.ms)
  return `${count} ${match.unit}${count === 1 ? '' : 's'} ago`
}

export {
  ACCESS_OPTIONS,
  ARTIFACT_ACTIONS,
  CUSTOM_MAX_YEARS,
  artifactNodeTexts,
  DEFAULT_ACCESS,
  DEFAULT_EXPIRY,
  EXPIRY_PRESETS,
  MAX_NAME_LENGTH,
  buildCreateBody,
  customDateLimits,
  expiresAtFor,
  expiryFor,
  expiryForCustomDate,
  formatDate,
  formatDateTime,
  formatExpiryDate,
  formatRelative,
  scopesFor,
  validateCustomDate,
  validateTokenName
}
