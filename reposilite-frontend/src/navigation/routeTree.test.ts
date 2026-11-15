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
import { unlistedRoutes } from './registry'
import { ROUTES } from '../router/routes'

describe('unlistedRoutes', () => {
  it('names a route that has neither a registry entry, a child nor an exempt name', () => {
    expect(unlistedRoutes(['overview', 'new-page'])).toEqual(['new-page'])
  })

  it('accepts the route of a page, of a child and of a detail address', () => {
    expect(unlistedRoutes(['search', 'access-teams', 'access-team'])).toEqual([])
  })

  it('accepts the redirect route of the old Maven page address as exempt', () => {
    expect(unlistedRoutes(['settings-maven-repositories'])).toEqual([])
  })

  it('accepts a legacy redirect name that the registry lists as exempt', () => {
    expect(unlistedRoutes(['tokens'])).toEqual([])
  })

  it('finds no unlisted route in the real route table, and names each one if it does', () => {
    const missing = unlistedRoutes(ROUTES.map(route => route.name))

    expect(missing, `routes without a registry entry, child or exempt name: ${missing.join(', ')}`).toEqual([])
  })
})
