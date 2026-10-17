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
import { isRepositorySegment, parseNode } from './nodeGrammar'

/*
 * The grammar is the one the server enforces: *, ingot.admin.<area|*> and
 * ingot.repository.<repo|*>.<action|*>, with wildcards only as whole segments.
 */

describe('parseNode', () => {
  it.each([
    ['*', 'WILDCARD'],
    ['ingot.admin.settings', 'STATIC'],
    ['ingot.admin.dashboard', 'STATIC'],
    ['ingot.admin.*', 'WILDCARD'],
    ['ingot.repository.releases.read', 'REPOSITORY'],
    ['ingot.repository.my_repo-2.manage', 'REPOSITORY'],
    ['ingot.repository.*.read', 'WILDCARD'],
    ['ingot.repository.releases.*', 'WILDCARD'],
    ['ingot.repository.*.*', 'WILDCARD']
  ])('accepts %s as %s', (text, kind) => {
    const result = parseNode(text)

    expect(result.valid, `${text} is valid`).toBe(true)
    expect(result.kind, `${text} has the kind ${kind}`).toBe(kind)
    expect(result.problems, `${text} has no problems`).toEqual([])
  })

  it.each([
    ['ingot.re*', 'a partial wildcard'],
    ['ingot.admin', 'an admin node without an area'],
    ['ingot.admin.settings.extra', 'an admin node with a fourth segment'],
    ['ingot.admin.unknown', 'an unknown admin area'],
    ['ingot.repository.releases', 'a repository node without an action'],
    ['ingot.repository.releases.write', 'an unknown action'],
    ['ingot.repository.releases.read.more', 'a repository node with five segments'],
    ['other.admin.settings', 'a node outside the ingot namespace'],
    ['ingot..read', 'an empty segment'],
    ['ingot.repository.re leases.read', 'a space inside a segment']
  ])('rejects %s (%s)', (text) => {
    const result = parseNode(text)

    expect(result.valid, `${text} is invalid`).toBe(false)
    expect(result.kind, 'an invalid node has no kind').toBeNull()
    expect(result.problems.length, 'at least one problem').toBeGreaterThan(0)
    expect(result.problems[0].node, 'the problem names the node').toBe(text)
  })

  it('tells the shape to use for a repository node with a missing action', () => {
    const result = parseNode('ingot.repository.releases')

    expect(result.problems[0].message).toBe('Use ingot.repository.<repository>.<read|deploy|delete|manage>')
  })

  it('requires a node for a blank text', () => {
    expect(parseNode('   ').problems).toEqual([{ node: '   ', message: 'Node is required' }])
  })

  it('rejects a node with outer spaces', () => {
    expect(parseNode(' ingot.admin.users').problems[0].message).toBe('Node must not start or end with a space')
  })

  it('treats a value that is not text as missing', () => {
    expect(parseNode(undefined).problems).toEqual([{ node: null, message: 'Node is required' }])
  })
})

describe('isRepositorySegment', () => {
  it('accepts a literal and a wildcard', () => {
    expect(isRepositorySegment('releases')).toBe(true)
    expect(isRepositorySegment('*')).toBe(true)
  })

  it('rejects a dotted, partial or empty name', () => {
    expect(isRepositorySegment('a.b')).toBe(false)
    expect(isRepositorySegment('re*')).toBe(false)
    expect(isRepositorySegment('')).toBe(false)
  })
})

describe('parseNode for artifact nodes', () => {
  it.each([
    'ingot.artifact.net.onelitefeather.read',
    'ingot.artifact.net.onelitefeather:mycelium-bom.deploy',
    'ingot.artifact.net.onelitefeather:mycelium.bom.delete',
    'ingot.artifact.*.read',
    'ingot.artifact.net.onelitefeather.*',
    'ingot.artifact.net:*.read',
    'ingot.repository.releases.artifact.net.onelitefeather.read',
    'ingot.repository.releases.artifact.net.onelitefeather:mycelium-bom.*'
  ])('accepts %s as an artifact node', (text) => {
    const result = parseNode(text)

    expect(result.valid, `${text} is valid`).toBe(true)
    expect(result.kind, `${text} is an artifact node`).toBe('ARTIFACT')
    expect(result.problems).toEqual([])
  })

  it.each([
    ['ingot.artifact.net.onelitefeather.write', "Unknown action 'write'"],
    ['ingot.artifact.net.onelitefeather.manage', "Unknown action 'manage'"],
    ['ingot.artifact.net.onelitefeather:a:b.read', "only one ':'"],
    ['ingot.artifact.*:mycelium-bom.read', "ambiguous"],
    ['ingot.artifact.net.*.read', "a wildcard must be the whole groupId"],
    ['ingot.artifact.net.onelitefeather:.read', 'The artifactId of'],
    ['ingot.artifact.net..onelitefeather.read', 'has an empty segment'],
    ['ingot.artifact.net/onelitefeather.read', "invalid character in 'net/onelitefeather'"],
    ['ingot.artifact.read', 'needs a groupId and an action'],
    ['ingot.artifact.net', 'needs a groupId and an action'],
    ['ingot.repository.*.artifact.net.read', 'The wildcard repository is not allowed for an artifact node'],
    ['ingot.repository.artifact.net.read', 'The repository name before .artifact. is missing'],
    ['ingot.repository.releases.artifact.net:mycelium-bom', 'needs a groupId and an action']
  ])('rejects %s (%s)', (text, message) => {
    const result = parseNode(text)

    expect(result.valid, `${text} is invalid`).toBe(false)
    expect(result.kind).toBeNull()
    expect(result.problems[0].node, 'the problem names the node').toBe(text)
    expect(result.problems[0].message).toContain(message)
  })

  it('tells the unqualified shape for an artifact node without an action', () => {
    expect(parseNode('ingot.artifact.net').problems[0].message)
      .toBe('The artifact node \'ingot.artifact.net\' needs a groupId and an action. Use ingot.artifact.<groupId>[:<artifactId>].<read|deploy|delete|*>')
  })

  it('tells the qualified shape for a qualified node without an action', () => {
    expect(parseNode('ingot.repository.releases.artifact.net').problems[0].message)
      .toContain('Use ingot.repository.<repository>.artifact.<groupId>[:<artifactId>].<read|deploy|delete|*>')
  })

  it('rejects a space inside an artifact node', () => {
    expect(parseNode('ingot.artifact.net. onelitefeather.read').valid).toBe(false)
  })

  it('still rejects a repository node with five segments as before', () => {
    expect(parseNode('ingot.repository.releases.read.more').valid).toBe(false)
  })
})
