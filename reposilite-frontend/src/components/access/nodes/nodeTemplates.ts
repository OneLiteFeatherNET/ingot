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
 * Templates are lists of nodes with a {repository} placeholder. The server hands the lists out;
 * the client fills the placeholder. Pure.
 */

const PLACEHOLDER = '{repository}'
const GROUP_PLACEHOLDER = '{groupId}'
const ARTIFACT_PLACEHOLDER = '{artifactId}'

/** True when the template has a {repository} placeholder and so needs a repository. */
const needsRepository = (template) => (template?.nodes ?? []).some(node => node.includes(PLACEHOLDER))

/** True when the template has a {groupId} or {artifactId} placeholder and so needs an artifact. */
const needsArtifact = (template) =>
  (template?.nodes ?? []).some(node => node.includes(GROUP_PLACEHOLDER) || node.includes(ARTIFACT_PLACEHOLDER))

/**
 * The nodes of a template with the placeholders replaced: {repository} by the repository, {groupId}
 * by the groupId and {artifactId} by the artifactId. An empty artifactId takes its colon with it, so
 * `ingot.artifact.{groupId}:{artifactId}.read` becomes `ingot.artifact.net.read`.
 */
const fillTemplate = (template, repository, artifact = {}) => {
  const groupId = artifact.groupId ?? ''
  const artifactId = artifact.artifactId ?? ''
  return (template?.nodes ?? []).map(node => node
    .split(PLACEHOLDER).join(repository ?? '')
    .split(`:${ARTIFACT_PLACEHOLDER}`).join(artifactId === '' ? '' : `:${artifactId}`)
    .split(ARTIFACT_PLACEHOLDER).join(artifactId)
    .split(GROUP_PLACEHOLDER).join(groupId))
}

/**
 * What applying a template does to a list: { added, skipped }. `added` holds the entries that are
 * new (allowed), `skipped` the node texts that were already listed. A node already listed keeps
 * its own allowed value.
 */
const applyTemplate = (template, repository, existing, artifact = {}) => {
  const listed = new Set((existing ?? []).map(entry => entry.node))
  const added = []
  const skipped = []
  for (const node of fillTemplate(template, repository, artifact)) {
    if (listed.has(node)) {
      skipped.push(node)
    } else {
      listed.add(node)
      added.push({ node, allowed: true })
    }
  }
  return { added, skipped }
}

// The actions a repository role grants, as the server's migration and its node templates write them.
const ROLE_ACTIONS = {
  READ: ['read'],
  DEPLOY: ['read', 'deploy'],
  MAINTAIN: ['read', 'deploy', 'delete'],
  ADMIN: ['read', 'deploy', 'delete', 'manage']
}

/** The nodes a repository role grants on one repository. The server refuses a repository-wide grant, so this is the write. */
const repositoryRoleNodes = (role, repository) =>
  (ROLE_ACTIONS[role] ?? []).map(action => `ingot.repository.${repository}.${action}`)

export { PLACEHOLDER, ROLE_ACTIONS, applyTemplate, fillTemplate, needsArtifact, needsRepository, repositoryRoleNodes }
