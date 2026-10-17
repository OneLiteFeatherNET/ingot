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

import { describe, it, expect } from 'vitest'
import { applyTemplate, fillTemplate, needsArtifact, needsRepository } from './nodeTemplates'

const deploy = () => ({
  id: 'deploy',
  label: 'Deploy',
  nodes: ['ingot.repository.{repository}.read', 'ingot.repository.{repository}.deploy']
})

const instanceAdmin = () => ({ id: 'instance-admin', label: 'Instance admin', nodes: ['ingot.admin.*'] })

describe('fillTemplate', () => {
  it('replaces the placeholder in every node', () => {
    expect(fillTemplate(deploy(), 'releases')).toEqual([
      'ingot.repository.releases.read',
      'ingot.repository.releases.deploy'
    ])
  })

  it('leaves a template without a placeholder unchanged', () => {
    expect(fillTemplate(instanceAdmin(), 'releases')).toEqual(['ingot.admin.*'])
  })
})

describe('needsRepository', () => {
  it('is true when a node holds the placeholder', () => {
    expect(needsRepository(deploy())).toBe(true)
  })

  it('is false for the instance admin template', () => {
    expect(needsRepository(instanceAdmin())).toBe(false)
  })
})

describe('applyTemplate', () => {
  it('adds every node as allowed to an empty list', () => {
    expect(applyTemplate(deploy(), 'releases', []).added).toEqual([
      { node: 'ingot.repository.releases.read', allowed: true },
      { node: 'ingot.repository.releases.deploy', allowed: true }
    ])
  })

  it('skips nodes that are already listed and reports them', () => {
    const existing = [{ node: 'ingot.repository.releases.read', allowed: false }]

    const result = applyTemplate(deploy(), 'releases', existing)

    expect(result.added).toEqual([{ node: 'ingot.repository.releases.deploy', allowed: true }])
    expect(result.skipped).toEqual(['ingot.repository.releases.read'])
  })

  it('does not change the list it is given', () => {
    const existing = [{ node: 'ingot.admin.users', allowed: true }]

    applyTemplate(deploy(), 'releases', existing)

    expect(existing).toEqual([{ node: 'ingot.admin.users', allowed: true }])
  })
})

describe('artifact placeholders', () => {
  const artifactRead = () => ({ id: 'artifact-read', label: 'Read an artifact', nodes: ['ingot.artifact.{groupId}:{artifactId}.read'] })
  const repositoryArtifact = () => ({
    id: 'repository-artifact-deploy',
    label: 'Deploy an artifact in one repository',
    nodes: ['ingot.repository.{repository}.artifact.{groupId}:{artifactId}.read', 'ingot.repository.{repository}.artifact.{groupId}:{artifactId}.deploy']
  })

  it('fills the groupId and the artifactId', () => {
    expect(fillTemplate(artifactRead(), '', { groupId: 'net.onelitefeather', artifactId: 'mycelium-bom' })).toEqual([
      'ingot.artifact.net.onelitefeather:mycelium-bom.read'
    ])
  })

  it('leaves out the artifactId and its colon when it is empty', () => {
    expect(fillTemplate(artifactRead(), '', { groupId: 'net.onelitefeather', artifactId: '' })).toEqual([
      'ingot.artifact.net.onelitefeather.read'
    ])
  })

  it('fills the repository and the coordinates of the qualified artifact template', () => {
    expect(fillTemplate(repositoryArtifact(), 'releases', { groupId: 'net', artifactId: '' })).toEqual([
      'ingot.repository.releases.artifact.net.read',
      'ingot.repository.releases.artifact.net.deploy'
    ])
  })

  it('needs an artifact for a template with a groupId, and not for the others', () => {
    expect(needsArtifact(artifactRead())).toBe(true)
    expect(needsArtifact(deploy())).toBe(false)
    expect(needsArtifact({ nodes: ['ingot.admin.*'] })).toBe(false)
  })

  it('adds the filled artifact nodes as Allow and skips the ones listed', () => {
    const existing = [{ node: 'ingot.artifact.net.onelitefeather.read', allowed: false }]

    const result = applyTemplate(artifactRead(), '', existing, { groupId: 'net.onelitefeather', artifactId: '' })

    expect(result.added).toEqual([])
    expect(result.skipped).toEqual(['ingot.artifact.net.onelitefeather.read'])
  })
})
