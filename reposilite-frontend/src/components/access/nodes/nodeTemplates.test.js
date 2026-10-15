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
import { applyTemplate, fillTemplate, needsRepository } from './nodeTemplates'

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
