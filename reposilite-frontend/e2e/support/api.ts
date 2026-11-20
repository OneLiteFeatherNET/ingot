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
 * Seeding through the public REST API only, with the bootstrap token of this run. Nothing here reads
 * or writes the data directory or the database. The environment variables are set by global-setup.js.
 */

const siteUrl = (path = '/'): string => new URL(path, process.env.E2E_BASE_URL).toString()

const authorization = (): string =>
  `Basic ${Buffer.from(`${process.env.E2E_TOKEN_NAME}:${process.env.E2E_TOKEN_SECRET}`).toString('base64')}`

const MAVEN_DOMAIN = '/api/settings/domain/maven'

// Repository settings are one document, so a change reads it, changes the list and writes it back with the
// entity tag it was read with. A write that lost the race answers 412 and the change is read again.
const MAX_ATTEMPTS = 25

// A repository of the maven settings domain, with the fields this suite reads. The server keeps the rest as it is.
interface Repository {
  id: string
  description?: string
  [field: string]: unknown
}

interface MavenDomain {
  repositories?: Repository[]
  [field: string]: unknown
}

interface RepositoryOptions {
  visibility?: string
  description?: string
  proxied?: Array<{ reference: string }>
}

// A node a principal holds, allowed or denied (see IdentityDtos.kt NodeDto).
interface PrincipalNode {
  node: string
  allowed: boolean
}

interface NewPrincipal {
  kind: string
  name: string
  displayName?: string
}

// A background run as the Jobs endpoint answers it, with the field this suite reads.
interface Job {
  state: string
  [field: string]: unknown
}

const changeRepositories = async (change: (repositories: Repository[]) => Repository[] | null): Promise<void> => {
  for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
    const read = await fetch(siteUrl(MAVEN_DOMAIN), { headers: { Authorization: authorization() } })
    if (!read.ok) {
      throw new Error(`reading the repository settings answered ${read.status}`)
    }
    const etag = read.headers.get('etag')
    const domain = await read.json() as MavenDomain
    const repositories = change(domain.repositories ?? [])
    if (repositories === null) {
      return
    }
    const write = await fetch(siteUrl(MAVEN_DOMAIN), {
      method: 'PUT',
      headers: {
        Authorization: authorization(),
        'Content-Type': 'application/json',
        ...(etag ? { 'If-Match': etag } : {}),
      },
      body: JSON.stringify({ ...domain, repositories }),
    })
    if (write.status === 412) {
      continue
    }
    if (!write.ok) {
      throw new Error(`writing the repository settings answered ${write.status}: ${await write.text()}`)
    }
    return
  }
  throw new Error(`the repository settings kept changing, gave up after ${MAX_ATTEMPTS} attempts`)
}

const repositoryTemplate = (id: string, { visibility = 'PUBLIC', description = '', proxied = [] }: RepositoryOptions = {}): Repository => ({
  id,
  visibility,
  description,
  tags: [],
  redeployment: false,
  preserveSnapshots: false,
  storageProvider: { type: 'fs', quota: '100%', mount: '' },
  storagePolicy: 'PRIORITIZE_UPSTREAM_METADATA',
  metadataMaxAge: 0,
  resolutionCacheMaxEntries: 0,
  proxied,
  protectedGroupPrefixes: [],
})

const createRepository = (id: string, options?: RepositoryOptions): Promise<void> =>
  changeRepositories((repositories) => {
    if (repositories.some(repository => repository.id === id)) {
      throw new Error(`repository ${id} already exists`)
    }
    return [...repositories, repositoryTemplate(id, options)]
  })

// Deleting a repository that is already gone changes nothing, so a test may delete its own repository in the UI.
const deleteRepository = (id: string): Promise<void> =>
  changeRepositories((repositories) => {
    if (!repositories.some(repository => repository.id === id)) {
      return null
    }
    return repositories.filter(repository => repository.id !== id)
  })

const uploadFile = async (repository: string, path: string, body: string): Promise<void> => {
  const response = await fetch(siteUrl(`/${repository}/${path}`), {
    method: 'PUT',
    headers: { Authorization: authorization() },
    body,
  })
  if (!response.ok) {
    throw new Error(`uploading ${path} to ${repository} answered ${response.status}`)
  }
}

const listRepositoryIds = async (): Promise<string[]> => {
  const response = await fetch(siteUrl('/api/maven/repositories'), { headers: { Authorization: authorization() } })
  if (!response.ok) {
    throw new Error(`listing the repositories answered ${response.status}`)
  }
  const { repositories } = await response.json() as { repositories: Repository[] }
  return repositories.map(repository => repository.id)
}

const readRepository = async (id: string): Promise<Repository | null> => {
  const response = await fetch(siteUrl('/api/maven/repositories'), { headers: { Authorization: authorization() } })
  if (!response.ok) {
    throw new Error(`listing the repositories answered ${response.status}`)
  }
  const { repositories } = await response.json() as { repositories: Repository[] }
  return repositories.find(repository => repository.id === id) ?? null
}

// A user or machine account of the identity API, created for one test. Its name is removed again by deletePrincipal.
const createPrincipal = async (body: NewPrincipal): Promise<void> => {
  const response = await fetch(siteUrl('/api/identity/principals'), {
    method: 'POST',
    headers: { Authorization: authorization(), 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw new Error(`creating the principal answered ${response.status}: ${await response.text()}`)
  }
}

// Removing a principal that is already gone changes nothing, so a test may remove its own principal in the UI.
const deletePrincipal = async (name: string): Promise<void> => {
  const response = await fetch(siteUrl(`/api/identity/principals/${encodeURIComponent(name)}`), {
    method: 'DELETE',
    headers: { Authorization: authorization() },
  })
  if (!response.ok && response.status !== 404) {
    throw new Error(`deleting the principal ${name} answered ${response.status}`)
  }
}

// Replaces the nodes a principal holds, each allowed or denied (see IdentityDtos.kt NodeDto).
const setPrincipalNodes = async (name: string, nodes: PrincipalNode[]): Promise<void> => {
  const response = await fetch(siteUrl(`/api/identity/principals/${encodeURIComponent(name)}/nodes`), {
    method: 'PUT',
    headers: { Authorization: authorization(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ nodes }),
  })
  if (!response.ok) {
    throw new Error(`setting the nodes of ${name} answered ${response.status}: ${await response.text()}`)
  }
}

// Creates an access token for a principal and returns its secret, which the server shows once.
const createPrincipalToken = async (name: string, tokenName: string): Promise<string> => {
  const response = await fetch(siteUrl(`/api/identity/principals/${encodeURIComponent(name)}/tokens`), {
    method: 'POST',
    headers: { Authorization: authorization(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: tokenName }),
  })
  if (!response.ok) {
    throw new Error(`creating a token for ${name} answered ${response.status}: ${await response.text()}`)
  }
  return (await response.json() as { secret: string }).secret
}

// Starts a rebuild of the search index for the named repositories and returns the id of its run.
const startIndexRebuild = async (repositories: string[]): Promise<string> => {
  const response = await fetch(siteUrl('/api/maven/search/index/rebuild'), {
    method: 'POST',
    headers: { Authorization: authorization(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ repositories }),
  })
  if (!response.ok) {
    throw new Error(`starting the index rebuild answered ${response.status}: ${await response.text()}`)
  }
  return (await response.json() as { runId: string }).runId
}

// One background run as the Jobs endpoint answers it, by its `<source>:<local id>` id.
const readJob = async (id: string): Promise<Job> => {
  const response = await fetch(siteUrl(`/api/jobs/${encodeURIComponent(id)}`), { headers: { Authorization: authorization() } })
  if (!response.ok) {
    throw new Error(`reading the run ${id} answered ${response.status}`)
  }
  return await response.json() as Job
}

const principalStatus = async (name: string): Promise<number> => {
  const response = await fetch(siteUrl(`/api/identity/principals/${encodeURIComponent(name)}`), {
    headers: { Authorization: authorization() },
  })
  return response.status
}

export type { Repository, RepositoryOptions }

export {
  siteUrl,
  createRepository,
  deleteRepository,
  uploadFile,
  listRepositoryIds,
  readRepository,
  createPrincipal,
  deletePrincipal,
  principalStatus,
  setPrincipalNodes,
  createPrincipalToken,
  startIndexRebuild,
  readJob,
}
