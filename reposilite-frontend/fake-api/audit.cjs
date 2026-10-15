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
 * A sample audit log for the stub API. It follows the rules of the backend: newest first, filters
 * combined with AND, a text search that is case-insensitive, a half-open time range, folded failed
 * logins, and cursor pages. The sample is built once, so the same run always shows the same events.
 */

const HOUR_MS = 60 * 60 * 1000
const DEFAULT_LIMIT = 50
const MAX_LIMIT = 200

const ACTIONS = [
  'ARTIFACT_DEPLOYED', 'ARTIFACT_DELETED', 'SETTINGS_CHANGED', 'TOKEN_CREATED', 'TOKEN_DELETED',
  'TOKEN_SECRET_REGENERATED', 'TOKEN_ROUTES_CHANGED', 'LOGIN_FAILED', 'CLEANUP_RUN',
  'PRINCIPAL_CREATED', 'PRINCIPAL_UPDATED', 'PRINCIPAL_DISABLED', 'PRINCIPAL_DELETED',
  'TEAM_CREATED', 'TEAM_UPDATED', 'TEAM_DELETED', 'TEAM_MEMBERSHIP_CHANGED', 'TEAM_GRANTS_CHANGED',
  'INSTANCE_ADMIN_GRANTED', 'INSTANCE_ADMIN_REVOKED',
  'LOGIN_SUCCEEDED', 'LOGOUT', 'SESSION_REFRESH_REUSE_DETECTED', 'SETUP_COMPLETED',
  'PASSWORD_CHANGED', 'PASSWORD_RESET', 'NODES_CHANGED',
]

// The actors of the sample are principals, so their rows carry the kind (USER or MACHINE) the UI
// links by. A name that is no principal (the system, a made-up login name) has no kind.
const ACTOR_KINDS = { admin: 'USER', alice: 'USER', bob: 'USER', carol: 'USER', 'ci-bot': 'MACHINE', 'ci-deployer': 'MACHINE' }
const withActorKind = (event) => ({ ...event, actorKind: event.actorKind !== undefined ? event.actorKind : (ACTOR_KINDS[event.actor] ?? null) })

// Each template is one kind of event, with the values a real event of that kind would carry.
const TEMPLATES = [
  (version) => ({ action: 'ARTIFACT_DEPLOYED', actor: 'ci-deployer', actorAddress: '203.0.113.10', repository: 'releases', path: `org/example/app/${version}/app-${version}.jar`, details: null, success: true, occurrences: 1 }),
  (version) => ({ action: 'ARTIFACT_DEPLOYED', actor: 'ci-deployer', actorAddress: '203.0.113.10', repository: 'snapshots', path: `org/example/lib/${version}-SNAPSHOT/lib-${version}-20261014.120000-1.jar`, details: null, success: true, occurrences: 1 }),
  (version) => ({ action: 'ARTIFACT_DELETED', actor: 'admin', actorAddress: '10.0.0.2', repository: 'snapshots', path: `org/example/lib/${version}-SNAPSHOT/lib-${version}-20261001.100000-1.jar`, details: null, success: true, occurrences: 1 }),
  () => ({ action: 'TOKEN_CREATED', actor: 'admin', actorAddress: '10.0.0.2', repository: null, path: null, details: { token: 'deploy-bot', type: 'PERSISTENT', replaced: false }, success: true, occurrences: 1 }),
  () => ({ action: 'TOKEN_ROUTES_CHANGED', actor: 'admin', actorAddress: '10.0.0.2', repository: null, path: null, details: { token: 'ci-deployer', permissionsAdded: [], permissionsRemoved: [], routesAdded: ['/snapshots (read)'], routesRemoved: [] }, success: true, occurrences: 1 }),
  () => ({ action: 'TOKEN_SECRET_REGENERATED', actor: 'admin', actorAddress: '10.0.0.2', repository: null, path: null, details: { token: 'ci-deployer' }, success: true, occurrences: 1 }),
  () => ({ action: 'TOKEN_DELETED', actor: 'admin', actorAddress: '10.0.0.2', repository: null, path: null, details: { token: 'old-bot' }, success: true, occurrences: 1 }),
  () => ({ action: 'SETTINGS_CHANGED', actor: 'admin', actorAddress: '10.0.0.2', repository: null, path: null, details: { domain: 'frontend', fields: ['appearance.seedColor', 'title'] }, success: true, occurrences: 1 }),
  () => ({ action: 'LOGIN_FAILED', actor: 'mallory', actorAddress: '198.51.100.23', repository: null, path: null, details: null, success: false, occurrences: 7 }),
  () => ({ action: 'CLEANUP_RUN', actor: 'system', actorAddress: null, repository: 'mirror-cache', path: null, details: { runId: 'sample-nightly', policy: 'mirror-cache-nightly', scope: 'MIRROR_CACHE', trigger: 'SCHEDULED', status: 'SUCCEEDED', deletedCount: 42, deletedBytes: 21504000 }, success: true, occurrences: 1 }),
]

// The identity events. They are kept apart from the templates above: the sample picks those by a
// stride over their count, and a longer list would make the stride skip most of them. A principal
// target carries principal and kind, a team target carries team, as the token events carry token.
const admin = (action, details) => ({ action, actor: 'admin', actorAddress: '10.0.0.2', repository: null, path: null, details, success: true, occurrences: 1 })
const by = (actor, action, details, extra = {}) => ({ action, actor, actorAddress: '10.0.0.2', repository: null, path: null, details, success: true, occurrences: 1, ...extra })
const IDENTITY_EVENTS = [
  { hoursAgo: 1, ...by('alice', 'LOGIN_SUCCEEDED', { provider: 'local' }, { actorAddress: '198.51.100.7' }) },
  { hoursAgo: 4, ...by('alice', 'LOGOUT', null, { actorAddress: '198.51.100.7' }) },
  { hoursAgo: 6, ...by('alice', 'LOGIN_FAILED', { provider: 'local' }, { actorAddress: '198.51.100.7', success: false, occurrences: 2 }) },
  { hoursAgo: 8, ...by('alice', 'SESSION_REFRESH_REUSE_DETECTED', { principal: 'alice', kind: 'USER', provider: 'local' }, { actorAddress: '203.0.113.99', success: false }) },
  { hoursAgo: 10, ...by('alice', 'PASSWORD_CHANGED', { principal: 'alice', kind: 'USER' }, { actorAddress: '198.51.100.7' }) },
  { hoursAgo: 12, ...by('admin', 'PASSWORD_RESET', { principal: 'bob', kind: 'USER' }) },
  { hoursAgo: 168, ...by('admin', 'SETUP_COMPLETED', { principal: 'admin', kind: 'USER' }) },
  { hoursAgo: 2, ...admin('PRINCIPAL_CREATED', { principal: 'carol', kind: 'USER' }) },
  { hoursAgo: 3, ...admin('PRINCIPAL_UPDATED', { principal: 'alice', kind: 'USER', fields: ['displayName'] }) },
  { hoursAgo: 5, ...admin('PRINCIPAL_DISABLED', { principal: 'bob', kind: 'USER' }) },
  { hoursAgo: 26, ...admin('PRINCIPAL_DELETED', { principal: 'old-ci', kind: 'MACHINE' }) },
  { hoursAgo: 30, ...admin('PRINCIPAL_CREATED', { principal: 'ci-bot', kind: 'MACHINE' }) },
  { hoursAgo: 48, ...admin('TEAM_CREATED', { team: 'deployers' }) },
  { hoursAgo: 49, ...admin('TEAM_GRANTS_CHANGED', { team: 'deployers', grantsAdded: ['releases (DEPLOY)', 'all repositories (READ)'], grantsRemoved: [] }) },
  { hoursAgo: 50, ...admin('TEAM_MEMBERSHIP_CHANGED', { team: 'deployers', added: ['alice', 'ci-bot'], removed: [] }) },
  { hoursAgo: 51, ...by('system:oidc:keycloak', 'TEAM_MEMBERSHIP_CHANGED', { team: 'oidc:keycloak:ingot-admins', added: ['alice'], removed: [] }) },
  { hoursAgo: 72, ...admin('TEAM_UPDATED', { team: 'docs-sync', fields: ['description', 'externalRef'] }) },
  { hoursAgo: 96, ...admin('TEAM_DELETED', { team: 'interns' }) },
  { hoursAgo: 120, ...admin('INSTANCE_ADMIN_GRANTED', { principal: 'admin', kind: 'USER' }) },
  { hoursAgo: 121, ...admin('INSTANCE_ADMIN_GRANTED', { team: 'platform' }) },
  { hoursAgo: 144, ...admin('INSTANCE_ADMIN_REVOKED', { principal: 'carol', kind: 'USER' }) },
]

const buildSample = (now) => {
  const events = []
  for (let index = 0; index < 96; index++) {
    const template = TEMPLATES[(index * 7) % TEMPLATES.length]
    const version = `${1 + (index % 4)}.${index % 9}`
    // Spread the events over about six weeks, with more of them in the recent days.
    const hoursAgo = Math.floor((index * index * 0.7 + index * 3) % (42 * 24))
    events.push({
      id: index + 1,
      timestamp: new Date(now - hoursAgo * HOUR_MS - (index % 5) * 60 * 1000).toISOString(),
      ...withActorKind(template(version)),
    })
  }
  IDENTITY_EVENTS.forEach(({ hoursAgo, ...event }, offset) => {
    events.push({ id: events.length + 1, timestamp: new Date(now - hoursAgo * HOUR_MS - offset * 60 * 1000).toISOString(), ...withActorKind(event) })
  })
  return events.sort((left, right) => Date.parse(right.timestamp) - Date.parse(left.timestamp) || right.id - left.id)
}

const SAMPLE = buildSample(Date.now())

const includesText = (event, needle) =>
  [event.actor, event.repository, event.path, event.details ? JSON.stringify(event.details) : '']
    .some(value => (value || '').toLowerCase().includes(needle))

const matches = (event, filters) => {
  if (filters.actions.length > 0 && !filters.actions.includes(event.action)) return false
  if (filters.actor && event.actor !== filters.actor) return false
  if (filters.repository && event.repository !== filters.repository) return false
  const time = Date.parse(event.timestamp)
  if (filters.from !== undefined && time < filters.from) return false
  if (filters.to !== undefined && time >= filters.to) return false
  if (filters.text && !includesText(event, filters.text)) return false
  return true
}

const parseInstant = (value) => {
  if (!value) return undefined
  const time = Date.parse(value)
  return Number.isNaN(time) ? undefined : time
}

/** Reads the filters the way the backend does. Unknown actions and malformed times are ignored here. */
const readFilters = (params) => ({
  actions: (params.action || '').split(',').map(name => name.trim()).filter(name => ACTIONS.includes(name)),
  actor: (params.actor || '').trim(),
  repository: (params.repository || '').trim(),
  text: (params.q || '').trim().toLowerCase(),
  from: parseInstant(params.from),
  to: parseInstant(params.to),
})

/** One page, newest first. The cursor is the offset of the next row, which is enough for a sample. */
const queryEvents = (params) => {
  const filters = readFilters(params)
  const limit = Math.min(Math.max(Number(params.limit) || DEFAULT_LIMIT, 1), MAX_LIMIT)
  const offset = Number(params.cursor) || 0
  const matching = SAMPLE.filter(event => matches(event, filters))
  const page = matching.slice(offset, offset + limit)
  const nextCursor = offset + limit < matching.length ? String(offset + limit) : null
  return { events: page, nextCursor }
}

const CSV_HEADER = 'id,timestamp,actor,actorAddress,action,repository,path,success,occurrences,details'

// Formula characters get a leading apostrophe, and cells with separators or quotes are quoted.
const csvCell = (value) => {
  const text = value === null || value === undefined ? '' : String(value)
  const guarded = /^[=+\-@\t\r]/.test(text) ? `'${text}` : text
  return /[",\n\r]/.test(guarded) ? `"${guarded.replace(/"/g, '""')}"` : guarded
}

const exportCsv = (params) => {
  const { events } = queryEvents({ ...params, limit: MAX_LIMIT })
  const rows = events.map(event => [
    event.id,
    event.timestamp,
    event.actor,
    event.actorAddress,
    event.action,
    event.repository,
    event.path,
    event.success,
    event.occurrences,
    event.details ? JSON.stringify(event.details) : '',
  ].map(csvCell).join(','))
  return [CSV_HEADER, ...rows].join('\n') + '\n'
}

/** Adds an event the stub API has just produced, such as a manual cleanup run, as the newest row. */
const recordEvent = (event) => {
  const id = Math.max(0, ...SAMPLE.map(entry => entry.id)) + 1
  SAMPLE.unshift({ actorAddress: null, repository: null, path: null, details: null, success: true, occurrences: 1, ...withActorKind(event), id, timestamp: new Date().toISOString() })
}

module.exports = { queryEvents, exportCsv, recordEvent, ACTIONS }
