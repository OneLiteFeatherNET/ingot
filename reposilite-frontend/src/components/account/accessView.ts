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

import type { components } from '../../types/api'

/*
 * The view model of the own access summary (GET /api/identity/me/access). It is pure: the server has
 * decided every right, and this only states them in plain words. A node or a grant it does not know is
 * kept as one line, never dropped, so a server that adds one first does not break the page.
 */

export type OwnAccessSummary = components['schemas']['OwnAccessSummaryDto']

export type PermissionGroup = 'Repository' | 'Administration' | 'Artifacts' | 'Other'

export type PermissionLine = {
  node: string
  meaning: string
  group: PermissionGroup
}

export type RepositoryScope = {
  type: 'repository'
  role: string
  repository: string
  pathPrefix: string | null
  via: string
  meaning: string
}

export type AccessView = {
  status: 'loading' | 'ready' | 'unavailable'
  instanceAdmin: boolean
  teams: { name: string; parents: string[] }[]
  permissions: { allowed: PermissionLine[]; denied: PermissionLine[] }
  scopes: RepositoryScope[]
  tokenNarrowed: boolean
  unknown: { type: string; text: string }[]
}

const ADMIN_AREAS: Record<string, string> = {
  users: 'Manage users',
  tokens: 'Manage tokens',
  teams: 'Manage teams',
  settings: 'Manage settings',
  maintenance: 'Run maintenance',
  audit: 'Read the audit log',
  console: 'Use the console',
  dashboard: 'Read the dashboard',
  idp: 'Manage identity provider links',
}

const ACTION_VERB: Record<string, string> = {
  read: 'Read from',
  deploy: 'Deploy to',
  delete: 'Delete from',
  manage: 'Manage',
}

const ARTIFACT_VERB: Record<string, string> = {
  read: 'Read artifacts under',
  deploy: 'Deploy artifacts under',
  delete: 'Delete artifacts under',
}

const ROLE_VERB: Record<string, string> = {
  READ: 'Read from',
  DEPLOY: 'Deploy to',
  MAINTAIN: 'Read, deploy and delete in',
  ADMIN: 'Manage',
}

const PROVIDER_LABEL: Record<string, string> = {
  local: 'Local',
  ldap: 'LDAP',
  oidc: 'OIDC',
}

const REPOSITORY_NODE = /^ingot\.repository\.([^.]+)\.(read|deploy|delete|manage|\*)$/
const REPOSITORY_ARTIFACT_NODE = /^ingot\.repository\.([^.]+)\.artifact\.(.+)\.([^.]+)$/
const ARTIFACT_NODE = /^ingot\.artifact\.(.+)\.([^.]+)$/
const ADMIN_NODE = /^ingot\.admin\.([^.]+)$/

// The plain sentence for one node, and the group it is listed under. A node of a form the table does
// not know is shown as its own text, under Other.
const describeNode = (node: string): Omit<PermissionLine, 'node'> => {
  if (node === 'ingot.admin.*') return { meaning: 'Full instance administration', group: 'Administration' }
  if (node === '*') return { meaning: 'Full access to everything', group: 'Other' }

  const admin = ADMIN_NODE.exec(node)
  if (admin && ADMIN_AREAS[admin[1]]) return { meaning: ADMIN_AREAS[admin[1]], group: 'Administration' }

  const repositoryArtifact = REPOSITORY_ARTIFACT_NODE.exec(node)
  if (repositoryArtifact && ARTIFACT_VERB[repositoryArtifact[3]]) {
    const [, repository, coordinate, action] = repositoryArtifact
    return { meaning: `${ARTIFACT_VERB[action]} ${coordinate} in ${repository}`, group: 'Artifacts' }
  }

  const artifact = ARTIFACT_NODE.exec(node)
  if (artifact && ARTIFACT_VERB[artifact[2]]) {
    return { meaning: `${ARTIFACT_VERB[artifact[2]]} ${artifact[1]}`, group: 'Artifacts' }
  }

  const repository = REPOSITORY_NODE.exec(node)
  if (repository) {
    const [, name, action] = repository
    const target = name === '*' ? 'every repository' : name
    if (action === '*') return { meaning: `Every action on ${target}`, group: 'Repository' }
    return { meaning: `${ACTION_VERB[action]} ${target}`, group: 'Repository' }
  }

  return { meaning: node, group: 'Other' }
}

const describeGrant = (grant: OwnAccessSummary['grants'][number], repository: string): RepositoryScope => {
  const verb = ROLE_VERB[grant.role] ?? `Role ${grant.role} on`
  const under = grant.pathPrefix ? ` under ${grant.pathPrefix}` : ''
  return {
    type: 'repository',
    role: grant.role,
    repository,
    pathPrefix: grant.pathPrefix ?? null,
    via: grant.team,
    meaning: `${verb} ${repository}${under}`,
  }
}

const EMPTY: Omit<AccessView, 'status'> = {
  instanceAdmin: false,
  teams: [],
  permissions: { allowed: [], denied: [] },
  scopes: [],
  tokenNarrowed: false,
  unknown: [],
}

/*
 * The page's view of the summary. `loading` and `unavailable` carry no rights at all: the page shows
 * its state line and nothing else is drawn from a summary that has not arrived.
 */
export const accessView = (summary: OwnAccessSummary | 'loading' | 'unavailable'): AccessView => {
  if (summary === 'loading' || summary === 'unavailable') {
    return { ...EMPTY, status: summary }
  }

  const scopes: RepositoryScope[] = []
  const unknown: AccessView['unknown'] = []
  for (const grant of summary.grants) {
    if (grant.repository) {
      scopes.push(describeGrant(grant, grant.repository))
    } else {
      unknown.push({ type: 'grant', text: `${grant.team} holds ${grant.role} without a repository` })
    }
  }

  return {
    status: 'ready',
    instanceAdmin: summary.instanceAdmin,
    teams: summary.teams.map(team => ({ name: team.name, parents: [...team.parents] })),
    permissions: {
      allowed: summary.nodes.map(node => ({ node, ...describeNode(node) })),
      denied: summary.denials.map(denial => ({ node: denial.node, ...describeNode(denial.node) })),
    },
    scopes,
    tokenNarrowed: summary.tokenNarrowed,
    unknown,
  }
}

/* The sign-in sources of a principal as the page names them: its linked providers, or the account's own kind of sign-in. */
export const signInSources = (providers: string[], kind: string): string[] => {
  if (providers.length === 0) return kind === 'MACHINE' ? ['Token'] : ['Local']
  return providers.map(id => PROVIDER_LABEL[id] ?? id)
}

export const kindLabel = (kind: string): string => {
  if (kind === 'USER') return 'User'
  if (kind === 'MACHINE') return 'Machine'
  return kind
}
