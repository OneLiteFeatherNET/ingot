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
 * The fake's copy of the repository type rules of the backend (RepositoryType.kt, MavenSettings.kt). The messages are the
 * backend's, so a dashboard that shows them sees what the real server answers. Only the type rules live here; the other
 * maven settings are not validated by the fake.
 */

// A reference names a host: trimmed, without one trailing slash. A blank reference names nothing.
const hostOf = (entry) => String(entry?.reference ?? '').trim().replace(/\/$/, '')

// A declared type wins. An untyped repository is derived from what it proxies: nothing is hosted, only local
// repositories is a group, only external URLs is a proxy, and both is mixed. Blank entries do not count, which keeps
// the production group with its empty entry a group.
const effectiveType = (repository, repositories) => {
  if (repository.type) return { type: repository.type.toLowerCase(), typeExplicit: true }
  const ids = new Set(repositories.map(candidate => candidate.id))
  const hosts = (repository.proxied ?? []).map(hostOf).filter(host => host !== '')
  const local = hosts.filter(host => ids.has(host)).length
  const external = hosts.length - local
  const type = hosts.length === 0 ? 'hosted' : external === 0 ? 'group' : local === 0 ? 'proxy' : 'mixed'
  return { type, typeExplicit: false }
}

// The shortest loop from start back to start along the edges, starting and ending with start, or null.
const loopThrough = (start, edges) => {
  const parent = new Map([[start, null]])
  const queue = [start]
  while (queue.length > 0) {
    const node = queue.shift()
    for (const next of edges.get(node) ?? []) {
      if (next === start) {
        const chain = []
        for (let cursor = node; cursor !== null; cursor = parent.get(cursor)) chain.unshift(cursor)
        return [...chain, start]
      }
      if (!parent.has(next)) {
        parent.set(next, node)
        queue.push(next)
      }
    }
  }
  return null
}

const typeViolations = (settings) => {
  const repositories = settings.repositories ?? []
  const localNames = new Set(repositories.map(repository => repository.id))
  const violations = []

  for (const repository of repositories) {
    const declared = repository.type
    if (!declared) continue
    const id = repository.id
    const proxied = repository.proxied ?? []
    const entries = proxied.map(entry => ({ store: entry?.store === true, host: hostOf(entry) }))

    if (declared === 'HOSTED') {
      if (proxied.length > 0) violations.push(`repository '${id}': type HOSTED takes no proxied repositories, but ${proxied.length} are listed`)
    } else if (declared === 'PROXY') {
      if (proxied.length === 0) violations.push(`repository '${id}': type PROXY needs at least one proxied external URL`)
      entries.filter(entry => entry.host === '').forEach(() => violations.push(`repository '${id}': type PROXY has an empty proxied reference`))
      entries.filter(entry => entry.host !== '' && localNames.has(entry.host)).forEach(entry => violations.push(
        `repository '${id}': type PROXY proxies external URLs only, but '${entry.host}' is a local repository (use type GROUP to proxy local repositories)`
      ))
    } else if (declared === 'GROUP') {
      if (proxied.length === 0) violations.push(`repository '${id}': type GROUP needs at least one proxied local repository`)
      for (const entry of entries) {
        if (entry.host === '') violations.push(`repository '${id}': type GROUP has an empty proxied reference`)
        else if (entry.host === id) violations.push(`repository '${id}': type GROUP cannot proxy itself`)
        else if (!localNames.has(entry.host)) violations.push(`repository '${id}': type GROUP proxies local repositories only, but '${entry.host}' is not a repository of this instance`)
        else if (entry.store) violations.push(`repository '${id}': type GROUP stores nothing, so proxied '${entry.host}' must not set store`)
      }
    }
  }

  const edges = new Map(repositories.map(repository => [
    repository.id,
    (repository.proxied ?? []).map(hostOf).filter(host => localNames.has(host) && host !== repository.id),
  ]))
  for (const group of repositories.filter(repository => repository.type === 'GROUP')) {
    const loop = loopThrough(group.id, edges)
    if (loop) violations.push(`repository '${group.id}': type GROUP is part of a cycle of proxied repositories (${loop.join(' -> ')})`)
  }

  return violations
}

// The message the backend answers with when an update is rejected, or null when the settings are accepted.
const rejectionOf = (settings) => {
  const violations = typeViolations(settings)
  return violations.length === 0 ? null : `Invalid maven settings: ${violations.join('; ')}`
}

module.exports = { effectiveType, typeViolations, rejectionOf }
