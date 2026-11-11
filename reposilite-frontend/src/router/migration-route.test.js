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
import { createMemoryHistory } from 'vue-router'
import { createAppRouter } from './create'
import { INSTANCE_ADMIN_NODE } from '../store/nodeAccess'

const shell = { render: () => null }

// A router per test on memory history. The visitor is an instance admin unless a test says otherwise.
const makeRouter = ({ isManager = true } = {}) =>
  createAppRouter({
    history: createMemoryHistory(),
    component: shell,
    siteTitle: 'Ingot Repository',
    whenSessionKnown: () => Promise.resolve(),
    isManager: () => isManager,
    can: () => isManager,
  })

describe('the Migration addresses', () => {
  it('opens every step address on the settings-migration route', async () => {
    const router = makeRouter()

    await router.push('/settings/migration/sync')

    expect(router.currentRoute.value.name).toBe('settings-migration')
    expect(router.currentRoute.value.params.step).toBe('sync')
  })

  it('opens the bare address without a step, which the page resolves to a step', async () => {
    const router = makeRouter()

    await router.push('/settings/migration')

    expect(router.currentRoute.value.name).toBe('settings-migration')
    expect(router.currentRoute.value.params.step).toBeUndefined()
  })

  it('keeps a settings domain on the settings route', async () => {
    const router = makeRouter()

    await router.push('/settings/maven')

    expect(router.currentRoute.value.name).toBe('settings')
    expect(router.currentRoute.value.params.domain).toBe('maven')
  })

  it('marks the page as a manager page that needs the instance admin node', async () => {
    const router = makeRouter()

    const resolved = router.resolve('/settings/migration/connect')

    expect(resolved.meta.section).toBe('migration')
    expect(resolved.meta.manager).toBe(true)
    expect(resolved.meta.node).toBe(INSTANCE_ADMIN_NODE)
  })

  it('keeps an anonymous visitor at the address, where the page asks for a sign in', async () => {
    const router = makeRouter({ isManager: false })

    await router.push('/settings/migration/connect')

    expect(router.currentRoute.value.path).toBe('/settings/migration/connect')
  })
})
