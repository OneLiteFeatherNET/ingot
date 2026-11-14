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
import { ACCESS_NODES, canUse, requiredNodes, routeAllowed } from './nodeAccess'

describe('canUse', () => {
  it('allows every node for a lone wildcard', () => {
    expect(canUse(['*'], 'ingot.repository.releases.deploy')).toBe(true)
  })

  it('allows a node that an entry names exactly', () => {
    expect(canUse(['ingot.admin.maintenance'], 'ingot.admin.maintenance')).toBe(true)
  })

  it('allows a node under an entry with a trailing wildcard', () => {
    expect(canUse(['ingot.repository.releases.*'], 'ingot.repository.releases.read')).toBe(true)
  })

  it('allows a node under an entry with a wildcard in the middle', () => {
    expect(canUse(['ingot.repository.*.read'], 'ingot.repository.private.read')).toBe(true)
  })

  it('denies a node that no entry matches', () => {
    expect(canUse(['ingot.admin.users'], 'ingot.admin.maintenance')).toBe(false)
  })

  it('denies every node for an empty list', () => {
    expect(canUse([], 'ingot.admin.maintenance')).toBe(false)
  })

  it('lets ingot.admin.* cover ingot.admin.maintenance', () => {
    expect(canUse(['ingot.admin.*'], 'ingot.admin.maintenance')).toBe(true)
  })

  it('does not let ingot.admin.* cover a repository node', () => {
    expect(canUse(['ingot.admin.*'], 'ingot.repository.x.read')).toBe(false)
  })

  it('does not let a shorter entry cover a longer node', () => {
    expect(canUse(['ingot.repository.*'], 'ingot.repository.x.read')).toBe(false)
  })

  it('denies when the node list is missing', () => {
    expect(canUse(undefined, 'ingot.admin.maintenance')).toBe(false)
  })
})

describe('requiredNodes', () => {
  it('reads one node from a route', () => {
    expect(requiredNodes({ node: 'ingot.admin.audit' })).toEqual(['ingot.admin.audit'])
  })

  it('reads the alternatives of a route', () => {
    expect(requiredNodes({ anyNode: ACCESS_NODES })).toEqual(ACCESS_NODES)
  })

  it('reads nothing from a route without nodes', () => {
    expect(requiredNodes({ section: 'overview' })).toEqual([])
  })
})

describe('routeAllowed', () => {
  const canOnly = (...allowed) => (node) => allowed.includes(node)

  it('allows a route when its node is allowed', () => {
    expect(routeAllowed({ node: 'ingot.admin.audit', manager: true }, canOnly('ingot.admin.audit'), false)).toBe(true)
  })

  it('denies a route when its node is missing, whatever the manager flag says', () => {
    expect(routeAllowed({ node: 'ingot.admin.audit', manager: true }, canOnly(), true)).toBe(false)
  })

  it('allows a route when one of its alternatives is allowed', () => {
    expect(routeAllowed({ anyNode: ACCESS_NODES, manager: true }, canOnly('ingot.admin.teams'), false)).toBe(true)
  })

  it('falls back to the manager flag for a manager route without nodes', () => {
    expect(routeAllowed({ manager: true }, canOnly(), true)).toBe(true)
  })

  it('allows a route that needs nothing', () => {
    expect(routeAllowed({ section: 'overview' }, canOnly(), false)).toBe(true)
  })
})
