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
 * The audit log's filters, as the address holds them and as the API receives them. The address
 * is the source of truth: the view reads the filters from the route query and writes every change
 * back to it, so a filtered view can be bookmarked and shared. Everything here is a pure function
 * of its arguments, and the reference time is passed in, so the presets can be tested on any day.
 */

import { browseLocation } from '../../router/paths'

const HOUR_MS = 60 * 60 * 1000

export const ACTION_LABELS = {
  ARTIFACT_DEPLOYED: 'Artifact deployed',
  ARTIFACT_DELETED: 'Artifact deleted',
  SETTINGS_CHANGED: 'Settings changed',
  TOKEN_CREATED: 'Token created',
  TOKEN_DELETED: 'Token deleted',
  TOKEN_SECRET_REGENERATED: 'Secret regenerated',
  TOKEN_ROUTES_CHANGED: 'Token rights changed',
  LOGIN_SUCCEEDED: 'Signed in',
  LOGIN_FAILED: 'Sign-in failed',
  LOGOUT: 'Signed out',
  SESSION_REFRESH_REUSE_DETECTED: 'Session reuse detected',
  SETUP_COMPLETED: 'Setup completed',
  PASSWORD_CHANGED: 'Password changed',
  PASSWORD_RESET: 'Password reset by an admin',
  CLEANUP_RUN: 'Cleanup run',
  PRINCIPAL_CREATED: 'Account created',
  PRINCIPAL_UPDATED: 'Account updated',
  PRINCIPAL_DISABLED: 'Account disabled',
  PRINCIPAL_DELETED: 'Account deleted',
  TEAM_CREATED: 'Team created',
  TEAM_UPDATED: 'Team updated',
  TEAM_DELETED: 'Team deleted',
  TEAM_MEMBERSHIP_CHANGED: 'Team members changed',
  TEAM_GRANTS_CHANGED: 'Team grants changed',
  INSTANCE_ADMIN_GRANTED: 'Instance admin granted',
  INSTANCE_ADMIN_REVOKED: 'Instance admin revoked',
}

/**
 * The identity chips. Each one stands for several actions, which travel as one comma separated
 * `action` filter, so the address of a chip is the list of its actions.
 */
export const ACTION_GROUPS = [
  { key: 'accounts', label: 'Accounts', actions: ['PRINCIPAL_CREATED', 'PRINCIPAL_UPDATED', 'PRINCIPAL_DISABLED', 'PRINCIPAL_DELETED'] },
  { key: 'teams', label: 'Teams', actions: ['TEAM_CREATED', 'TEAM_UPDATED', 'TEAM_DELETED', 'TEAM_MEMBERSHIP_CHANGED', 'TEAM_GRANTS_CHANGED'] },
  { key: 'instance-admin', label: 'Instance admin', actions: ['INSTANCE_ADMIN_GRANTED', 'INSTANCE_ADMIN_REVOKED'] },
  {
    key: 'sessions',
    label: 'Sessions and passwords',
    actions: [
      'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'LOGOUT', 'SESSION_REFRESH_REUSE_DETECTED',
      'SETUP_COMPLETED', 'PASSWORD_CHANGED', 'PASSWORD_RESET',
    ],
  },
]

export const ACTION_OPTIONS = [
  { value: '', label: 'All actions' },
  ...Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label })),
]

/** The time-range chips. `hours` is the look-back of a preset; `custom` reads the From and To fields. */
export const RANGE_OPTIONS = [
  { key: 'all', label: 'All time', hours: null },
  { key: '24h', label: '24 hours', hours: 24 },
  { key: '7d', label: '7 days', hours: 7 * 24 },
  { key: '30d', label: '30 days', hours: 30 * 24 },
  { key: 'custom', label: 'Custom', hours: null },
]

const RANGE_KEYS = RANGE_OPTIONS.map(option => option.key)

const text = (value) => (typeof value === 'string' ? value.trim() : '')

/** The known actions of an action filter, which holds one action or a comma separated list of them. */
const knownActions = (value) =>
  value.split(',').map(part => part.trim()).filter(part => Object.hasOwn(ACTION_LABELS, part)).join(',')

/**
 * Reads the filters from a route query. An unknown action or range is dropped rather than sent,
 * so a hand-edited address shows the unfiltered view instead of an error.
 */
export const readAuditFilters = (query = {}) => {
  const action = knownActions(text(query.action))
  const range = text(query.range)
  return {
    action,
    actor: text(query.actor),
    repository: text(query.repository),
    text: text(query.q),
    range: RANGE_KEYS.includes(range) ? range : 'all',
    from: text(query.from),
    to: text(query.to),
  }
}

/**
 * The route query for a set of filters. Empty filters and the default range are left out, so the
 * address of an unfiltered view stays `/audit`.
 */
export const auditFiltersToQuery = (filters) => {
  const query = {}
  if (filters.action) query.action = filters.action
  if (filters.actor) query.actor = filters.actor
  if (filters.repository) query.repository = filters.repository
  if (filters.text) query.q = filters.text
  if (filters.range && filters.range !== 'all') query.range = filters.range
  if (filters.range === 'custom') {
    if (filters.from) query.from = filters.from
    if (filters.to) query.to = filters.to
  }
  return query
}

/**
 * The bounds a filter sends to the API, as ISO-8601 instants. A preset counts back from `now`, so the
 * same address shows the last 24 hours whenever it is opened.
 */
export const auditRangeBounds = (filters, now) => {
  if (filters.range === 'custom') {
    return {
      from: filters.from ? new Date(filters.from).toISOString() : undefined,
      to: filters.to ? new Date(filters.to).toISOString() : undefined,
    }
  }
  const preset = RANGE_OPTIONS.find(option => option.key === filters.range)
  if (!preset || preset.hours === null) {
    return {}
  }
  return { from: new Date(now - preset.hours * HOUR_MS).toISOString() }
}

/** The parameters of one API call. Empty values are left out, and the cursor is added for later pages. */
export const auditParams = (filters, now, cursor) => {
  const params = {
    action: filters.action || undefined,
    actor: filters.actor || undefined,
    repository: filters.repository || undefined,
    q: filters.text || undefined,
    ...auditRangeBounds(filters, now),
    cursor: cursor || undefined,
  }
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined))
}

/** The value a datetime-local input shows for an ISO instant, in the reader's time zone. */
export const isoToLocalInput = (iso) => {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** The ISO instant for a datetime-local value, or an empty string when the input is blank or invalid. */
export const localInputToIso = (value) => {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : date.toISOString()
}

export const actionLabel = (action) => ACTION_LABELS[action] ?? action

const NON_TOKEN_ACTORS = new Set(['anonymous', 'system'])

/**
 * The token that acted, as a router location. Only an authenticated token is linked: `anonymous` and
 * `system` are not tokens, and a failed login names whatever the caller typed, so it is not linked either.
 */
export const actorLocationOf = (event) =>
  NON_TOKEN_ACTORS.has(event.actor) || event.action === 'LOGIN_FAILED'
    ? null
    : { name: 'token', params: { name: event.actor } }

const MACHINE_TEAM_PREFIX = 'machine:'
const PRINCIPAL_PAGES = { USER: 'users', MACHINE: 'machines' }
const KIND_LABELS = { USER: 'User', MACHINE: 'Machine' }
const GONE_ACTIONS = new Set(['PRINCIPAL_DELETED', 'TEAM_DELETED'])

/**
 * The account or team an identity event acts on. A team event names `team`; a principal event names
 * `principal` and, when it knows, `kind`. An event that does not say which kind a principal is counts
 * as a user. A deleted target and the private team of a machine (`machine:*`) have no page to open,
 * so they get a label and no link.
 */
const identityTargetOf = (event) => {
  const details = event.details
  if (typeof details?.team === 'string' && details.team) {
    const privateTeam = details.team.startsWith(MACHINE_TEAM_PREFIX)
    return {
      label: `Team ${details.team}`,
      path: privateTeam || GONE_ACTIONS.has(event.action) ? null : `/access/teams/${encodeURIComponent(details.team)}`,
    }
  }
  if (typeof details?.principal === 'string' && details.principal) {
    const kind = Object.hasOwn(PRINCIPAL_PAGES, details.kind) ? details.kind : 'USER'
    return {
      label: `${KIND_LABELS[kind]} ${details.principal}`,
      path: GONE_ACTIONS.has(event.action) ? null : `/access/${PRINCIPAL_PAGES[kind]}/${encodeURIComponent(details.principal)}`,
    }
  }
  return null
}

const isIdentityEvent = (event) => /^(PRINCIPAL|TEAM|INSTANCE_ADMIN)_/.test(event.action)

/** What a row acts on, as a label. */
export const targetLabelOf = (event) => {
  if (isIdentityEvent(event)) {
    return identityTargetOf(event)?.label ?? ''
  }
  if (event.repository) {
    return [event.repository, event.path].filter(Boolean).join('/')
  }
  if (typeof event.details?.token === 'string') {
    return `Token ${event.details.token}`
  }
  if (typeof event.details?.domain === 'string') {
    return `Settings ${event.details.domain}`
  }
  return ''
}

/**
 * The router location a target links to: a repository path opens in the browser, a token opens its
 * editor, and an account or team opens its admin page (a path string, because the routes belong to
 * the access pages). A row with none of these has no link.
 */
export const targetLocationOf = (event) => {
  if (isIdentityEvent(event)) {
    return identityTargetOf(event)?.path ?? null
  }
  if (event.repository) {
    return browseLocation([event.repository, event.path].filter(Boolean).join('/'))
  }
  if (typeof event.details?.token === 'string' && event.action.startsWith('TOKEN_')) {
    return { name: 'token', params: { name: event.details.token } }
  }
  return null
}
