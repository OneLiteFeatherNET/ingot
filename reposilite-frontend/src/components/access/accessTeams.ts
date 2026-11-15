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

import type { AxiosResponse } from 'axios'
import type { NodeEntry, PrincipalSummary } from './accessPrincipals'

/*
 * The rules of the team pages that need no component: the search filter, the role table, the
 * path prefix normalization, the bodies of the create, members and grants requests and the
 * principal picker's candidates.
 */

const normalize = (text: string | null | undefined): string => String(text ?? '').trim().toLowerCase()

/*
 * The shapes below are what the identity endpoints send for teams. The OpenAPI document declares no
 * response body for them (see the contract gaps of the pull request), so they are written here from
 * the server's answers.
 */

/** A role of a team grant: READ, DEPLOY, MAINTAIN or ADMIN. */
export type TeamRole = 'READ' | 'DEPLOY' | 'MAINTAIN' | 'ADMIN'

/** One member of a team. MANUAL members are set by the admin, SYNC members come from the directory. */
export interface TeamMember {
  principal: string
  kind?: string
  source: string
}

/** One grant of a team: a repository (or every one) and a path prefix (or the whole repository). */
export interface TeamGrant {
  repository?: string | null
  pathPrefix?: string | null
  role: string
}

/** One row of the team list: the facts a row shows. */
export interface TeamSummary {
  name: string
  description?: string | null
  externalRef?: string | null
  memberCount?: number
  grantCount?: number
  instanceAdmin?: boolean
  instanceRole?: boolean
  nodeCount?: number
  isAdmin?: boolean
}

/** The detail of a team as the views read it: the lists are always present. */
export interface TeamDetail extends TeamSummary {
  members: TeamMember[]
  grants: TeamGrant[]
  parents: string[]
  instanceRole: boolean
  nodes?: NodeEntry[]
}

/** The detail answer of the server, as it is sent. */
export type TeamDetailAnswer = Partial<TeamDetail>

/** The body of a grant as the server takes it: an unset repository and an unset path prefix are null. */
export interface TeamGrantBody {
  repository: string | null
  pathPrefix: string | null
  role: string
}

/** The body of a create request. */
export interface TeamCreateBody {
  name: string
  description?: string
  externalRef?: string
}

/** The identity client's team endpoints, as the views call them. */
export interface TeamsApi {
  list(): Promise<AxiosResponse<TeamSummary[]>>
  get(name: string): Promise<AxiosResponse<TeamDetailAnswer>>
  create(body: unknown): Promise<AxiosResponse>
  update(name: string, body: unknown, etag?: string | null): Promise<AxiosResponse<TeamDetailAnswer>>
  remove(name: string): Promise<AxiosResponse>
  setMembers(name: string, members: unknown, etag?: string | null): Promise<AxiosResponse<TeamDetailAnswer>>
  setGrants(name: string, grants: unknown, etag?: string | null): Promise<AxiosResponse<TeamDetailAnswer>>
  grantInstanceAdmin(name: string, etag?: string | null): Promise<AxiosResponse<TeamDetailAnswer>>
  revokeInstanceAdmin(name: string, etag?: string | null): Promise<AxiosResponse<TeamDetailAnswer>>
  parents(name: string): Promise<AxiosResponse<{ parents?: string[] | null }>>
  setParents(name: string, parents: unknown, etag?: string | null): Promise<AxiosResponse<{ parents?: string[] | null }>>
}

/** One role with the explanation of what it lets a team do. */
export interface RoleEntry {
  value: TeamRole
  label: string
  explanation: string
}

const ROLES: RoleEntry[] = [
  { value: 'READ', label: 'Read', explanation: 'Read and download artifacts.' },
  { value: 'DEPLOY', label: 'Deploy', explanation: 'Read, and deploy new artifacts.' },
  { value: 'MAINTAIN', label: 'Maintain', explanation: 'Read, deploy and delete artifacts.' },
  { value: 'ADMIN', label: 'Admin', explanation: 'Everything above, plus the repository settings and the team grants on it.' }
]

const roleExplanation = (role: string): string => ROLES.find(entry => entry.value === role)?.explanation ?? ''

/** The teams whose name or description contains the query, ignoring case and outer spaces. */
const filterTeams = <T extends Pick<TeamSummary, 'name' | 'description'>>(teams: T[], query: string): T[] => {
  const needle = normalize(query)
  if (needle === '') return teams
  return teams.filter(team =>
    normalize(team.name).includes(needle) || (team.description != null && normalize(team.description).includes(needle)))
}

/** A path prefix without leading and trailing slashes; null when nothing is left (the whole repository). */
const normalizePathPrefix = (value: string | null | undefined): string | null => {
  const text = String(value ?? '').trim().replace(/^\/+/, '').replace(/\/+$/, '')
  return text === '' ? null : text
}

const grantScopeLabel = (repository: string | null | undefined): string => (repository ? repository : 'Every repository')

const pathPrefixLabel = (pathPrefix: string | null | undefined): string => normalizePathPrefix(pathPrefix) ?? 'Whole repository'

/** One grant as the server takes it: an unset repository and an unset path prefix are null. */
const grantPayload = ({ repository, pathPrefix, role }: {
  repository?: string | null
  pathPrefix?: string | null
  role: string
}): TeamGrantBody => ({
  repository: repository ? repository : null,
  pathPrefix: normalizePathPrefix(pathPrefix),
  role
})

/** The names of the MANUAL members: the only ones a members request may carry. */
const manualMembers = (detail: { members?: TeamMember[] | null } | null | undefined): string[] =>
  (detail?.members ?? []).filter(member => member.source === 'MANUAL').map(member => member.principal)

/** The principals that can still be added: everyone who is not a member, filtered by the query. */
const candidatePrincipals = (
  principals: PrincipalSummary[] | null | undefined,
  members: TeamMember[] | null | undefined,
  query: string
): PrincipalSummary[] => {
  const taken = new Set((members ?? []).map(member => member.principal))
  const needle = normalize(query)
  return (principals ?? []).filter(principal =>
    !taken.has(principal.name) &&
    (needle === '' || normalize(principal.name).includes(needle) || normalize(principal.displayName).includes(needle)))
}

/** The members request of a team: it takes the names of the MANUAL members and answers with the team. */
export type CommitMembers = (names: string[]) => Promise<MembersOutcome>

/** The outcome of a members request: ok, or the refusal with the server's message. A 412 is a conflict. */
export type MembersOutcome = { ok: boolean; conflict?: boolean; message?: string }

/** The body of a create request. A blank description and a blank external reference are left out. */
const buildTeamCreateBody = ({ name, description = '', externalRef = '' }: {
  name: string | null | undefined
  description?: string | null
  externalRef?: string | null
}): TeamCreateBody => {
  const body: TeamCreateBody = { name: String(name).trim() }
  const text = String(description ?? '').trim()
  const reference = String(externalRef ?? '').trim()
  if (text !== '') body.description = text
  if (reference !== '') body.externalRef = reference
  return body
}

/** The message for a name that cannot be used, or an empty string. */
const validateTeamName = (name: string | null | undefined): string => {
  const value = String(name ?? '').trim()
  if (value === '') return 'A name is required'
  if (value.includes('/')) return "A name must not contain '/'"
  if (value.startsWith('machine:')) return "Names starting with 'machine:' are reserved"
  return ''
}

/** The detail of a team as the views read it: the lists are always present. */
const teamDetailOf = (data: TeamDetailAnswer | null | undefined): TeamDetail => ({
  ...(data ?? {}),
  members: data?.members ?? [],
  grants: data?.grants ?? [],
  parents: data?.parents ?? [],
  instanceRole: data?.instanceRole === true
} as TeamDetail)

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
