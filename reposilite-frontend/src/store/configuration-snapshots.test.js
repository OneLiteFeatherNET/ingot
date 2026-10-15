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

import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { createConfigurationStore } from './configuration'

vi.mock('../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error) => error?.message ?? `${error}`
}))

const SCHEMA = {
  type: 'object',
  title: 'Maven',
  properties: {
    repositories: {
      type: 'array',
      items: {
        type: 'object',
        properties: { id: { type: 'string' }, visibility: { type: 'string' } },
        additionalProperties: false
      }
    }
  }
}

const fakeClient = (server) => ({
  settings: {
    domains: async () => ({ data: ['maven'] }),
    schema: async () => ({ data: SCHEMA }),
    fetch: async () => ({ data: JSON.parse(JSON.stringify(server.maven)) }),
    update: async (domain, content) => {
      server[domain] = JSON.parse(JSON.stringify(content))
      return { data: {} }
    }
  }
})

describe('configuration store snapshots', () => {
  it('keeps the server state as the snapshot while the draft is edited', async () => {
    const server = { maven: { repositories: [{ id: 'releases', visibility: 'PUBLIC' }] } }
    const store = createConfigurationStore(ref(fakeClient(server)))
    await store.fetchConfiguration()

    store.setEntry('maven', 'repositories', 0, { id: 'releases', visibility: 'PRIVATE' })

    expect(store.snapshots.value.maven.repositories[0].visibility, 'the saved state is not the draft').toBe('PUBLIC')
    expect(store.configurations.value.maven.repositories[0].visibility).toBe('PRIVATE')
  })

  it('moves the snapshot to the written state once the save is confirmed', async () => {
    const server = { maven: { repositories: [{ id: 'releases', visibility: 'PUBLIC' }] } }
    const store = createConfigurationStore(ref(fakeClient(server)))
    await store.fetchConfiguration()
    store.setEntry('maven', 'repositories', 0, { id: 'releases', visibility: 'PRIVATE' })

    await store.saveConfiguration()

    expect(store.snapshots.value.maven.repositories[0].visibility).toBe('PRIVATE')
  })

  it('reports that the server hides secrets, as the injected flag says', async () => {
    const server = { maven: { repositories: [] } }
    const store = createConfigurationStore(ref(fakeClient(server)), { secretsHidden: () => true })

    await store.fetchConfiguration()

    expect(store.secretsHidden.value).toBe(true)
  })

  it('does not report hidden secrets unless the server says so', async () => {
    const server = { maven: { repositories: [] } }
    const store = createConfigurationStore(ref(fakeClient(server)))

    await store.fetchConfiguration()

    expect(store.secretsHidden.value).toBe(false)
  })

  it('stages an imported file in the draft without writing it to the server', async () => {
    const server = { maven: { repositories: [{ id: 'releases', visibility: 'PUBLIC' }] } }
    const client = fakeClient(server)
    let writes = 0
    client.settings.update = async () => { writes++; return { data: {} } }
    const store = createConfigurationStore(ref(client))
    await store.fetchConfiguration()

    store.importDomains({ maven: { repositories: [{ id: 'releases', visibility: 'PRIVATE' }] } })

    expect(store.configurations.value.maven.repositories[0].visibility).toBe('PRIVATE')
    expect(store.hasUnsavedChanges.value, 'the import is an unsaved change').toBe(true)
    expect(store.snapshots.value.maven.repositories[0].visibility).toBe('PUBLIC')
    expect(writes).toBe(0)
  })
})
