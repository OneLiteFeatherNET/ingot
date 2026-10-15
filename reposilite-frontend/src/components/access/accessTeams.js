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
 * The rules of the team pages that need no component: the search filter, the role table, the
 * path prefix normalization, the bodies of the create, members and grants requests and the
 * principal picker's candidates.
 */

const normalize = (text) => String(text ?? '').trim().toLowerCase()

/** The four roles of the identity model, with what each one lets a team do (spec 2.1). */
const ROLES = [
  { value: 'READ', label: 'Read', explanation: 'Read and download artifacts.' },
  { value: 'DEPLOY', label: 'Deploy', explanation: 'Read, and deploy new artifacts.' },
  { value: 'MAINTAIN', label: 'Maintain', explanation: 'Read, deploy and delete artifacts.' },
  { value: 'ADMIN', label: 'Admin', explanation: 'Everything above, plus the repository settings and the team grants on it.' }
]

const roleExplanation = (role) => ROLES.find(entry => entry.value === role)?.explanation ?? ''

/** The teams whose name or description contains the query, ignoring case and outer spaces. */
const filterTeams = (teams, query) => {
  const needle = normalize(query)
  if (needle === '') return teams
  return teams.filter(team =>
    normalize(team.name).includes(needle) || (team.description != null && normalize(team.description).includes(needle)))
}

/** A path prefix without leading and trailing slashes; null when nothing is left (the whole repository). */
const normalizePathPrefix = (value) => {
  const text = String(value ?? '').trim().replace(/^\/+/, '').replace(/\/+$/, '')
  return text === '' ? null : text
}

const grantScopeLabel = (repository) => (repository ? repository : 'Every repository')

const pathPrefixLabel = (pathPrefix) => normalizePathPrefix(pathPrefix) ?? 'Whole repository'

/** One grant as the server takes it: an unset repository and an unset path prefix are null. */
const grantPayload = ({ repository, pathPrefix, role }) => ({
  repository: repository ? repository : null,
  pathPrefix: normalizePathPrefix(pathPrefix),
  role
})

/** The names of the MANUAL members: the only ones a members request may carry. */
const manualMembers = (detail) =>
  (detail?.members ?? []).filter(member => member.source === 'MANUAL').map(member => member.principal)

/** The principals that can still be added: everyone who is not a member, filtered by the query. */
const candidatePrincipals = (principals, members, query) => {
  const taken = new Set((members ?? []).map(member => member.principal))
  const needle = normalize(query)
  return (principals ?? []).filter(principal =>
    !taken.has(principal.name) &&
    (needle === '' || normalize(principal.name).includes(needle) || normalize(principal.displayName).includes(needle)))
}

/** The body of a create request. A blank description and a blank external reference are left out. */
const buildTeamCreateBody = ({ name, description = '', externalRef = '' }) => {
  const body = { name: String(name).trim() }
  const text = String(description ?? '').trim()
  const reference = String(externalRef ?? '').trim()
  if (text !== '') body.description = text
  if (reference !== '') body.externalRef = reference
  return body
}

/** The message for a name that cannot be used, or an empty string. */
const validateTeamName = (name) => {
  const value = String(name ?? '').trim()
  if (value === '') return 'A name is required'
  if (value.includes('/')) return "A name must not contain '/'"
  if (value.startsWith('machine:')) return "Names starting with 'machine:' are reserved"
  return ''
}

/** The detail of a team as the views read it: the lists are always present. */
const teamDetailOf = (data) => ({
  ...(data ?? {}),
  members: data?.members ?? [],
  grants: data?.grants ?? [],
  parents: data?.parents ?? [],
  children: data?.children ?? [],
  instanceRole: data?.instanceRole === true
})

export {
  ROLES,
  buildTeamCreateBody,
  candidatePrincipals,
  filterTeams,
  grantPayload,
  grantScopeLabel,
  manualMembers,
  normalizePathPrefix,
  pathPrefixLabel,
  roleExplanation,
  teamDetailOf,
  validateTeamName
}
