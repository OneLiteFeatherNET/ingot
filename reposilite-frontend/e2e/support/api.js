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

const siteUrl = (path = '/') => new URL(path, process.env.E2E_BASE_URL).toString()

const authorization = () =>
  `Basic ${Buffer.from(`${process.env.E2E_TOKEN_NAME}:${process.env.E2E_TOKEN_SECRET}`).toString('base64')}`

const MAVEN_DOMAIN = '/api/settings/domain/maven'

// Repository settings are one document, so a change reads it, changes the list and writes it back with the
// entity tag it was read with. A write that lost the race answers 412 and the change is read again.
const MAX_ATTEMPTS = 25

const changeRepositories = async (change) => {
  for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
    const read = await fetch(siteUrl(MAVEN_DOMAIN), { headers: { Authorization: authorization() } })
    if (!read.ok) {
      throw new Error(`reading the repository settings answered ${read.status}`)
    }
    const etag = read.headers.get('etag')
    const domain = await read.json()
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

const repositoryTemplate = (id, { visibility = 'PUBLIC', description = '' } = {}) => ({
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
  proxied: [],
  protectedGroupPrefixes: [],
})

const createRepository = (id, options) =>
  changeRepositories((repositories) => {
    if (repositories.some(repository => repository.id === id)) {
      throw new Error(`repository ${id} already exists`)
    }
    return [...repositories, repositoryTemplate(id, options)]
  })

// Deleting a repository that is already gone changes nothing, so a test may delete its own repository in the UI.
const deleteRepository = (id) =>
  changeRepositories((repositories) => {
    if (!repositories.some(repository => repository.id === id)) {
      return null
    }
    return repositories.filter(repository => repository.id !== id)
  })

const uploadFile = async (repository, path, body) => {
  const response = await fetch(siteUrl(`/${repository}/${path}`), {
    method: 'PUT',
    headers: { Authorization: authorization() },
    body,
  })
  if (!response.ok) {
    throw new Error(`uploading ${path} to ${repository} answered ${response.status}`)
  }
}

const listRepositoryIds = async () => {
  const response = await fetch(siteUrl('/api/maven/repositories'), { headers: { Authorization: authorization() } })
  if (!response.ok) {
    throw new Error(`listing the repositories answered ${response.status}`)
  }
  const { repositories } = await response.json()
  return repositories.map(repository => repository.id)
}

const readRepository = async (id) => {
  const response = await fetch(siteUrl('/api/maven/repositories'), { headers: { Authorization: authorization() } })
  if (!response.ok) {
    throw new Error(`listing the repositories answered ${response.status}`)
  }
  const { repositories } = await response.json()
  return repositories.find(repository => repository.id === id) ?? null
}

// A user or machine account of the identity API, created for one test. Its name is removed again by deletePrincipal.
const createPrincipal = async (body) => {
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
const deletePrincipal = async (name) => {
  const response = await fetch(siteUrl(`/api/identity/principals/${encodeURIComponent(name)}`), {
    method: 'DELETE',
    headers: { Authorization: authorization() },
  })
  if (!response.ok && response.status !== 404) {
    throw new Error(`deleting the principal ${name} answered ${response.status}`)
  }
}

const principalStatus = async (name) => {
  const response = await fetch(siteUrl(`/api/identity/principals/${encodeURIComponent(name)}`), {
    headers: { Authorization: authorization() },
  })
  return response.status
}

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
}
