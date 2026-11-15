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

type ClientRef = Parameters<typeof createConfigurationStore>[0]
/* The fake client implements only the settings calls the store makes, so it is cast to the store's client type. */
const clientRef = (client: unknown): ClientRef => ref(client) as unknown as ClientRef
type Repository = { id: string; visibility: string }

vi.mock('../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))
vi.mock('../helpers/toast', () => ({
  createSuccessToast: vi.fn(),
  createErrorToast: vi.fn(),
  errorMessage: (error: unknown) => (error as { message?: string } | undefined)?.message ?? String(error)
}))

// JSON round trip that keeps the value's type, so fixtures do not leak untyped values.
const cloneJson = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

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

const fakeClient = (server: Record<string, unknown>) => ({
  settings: {
    domains: () => Promise.resolve({ data: ['maven'] }),
    schema: () => Promise.resolve({ data: SCHEMA }),
    fetch: () => Promise.resolve({ data: cloneJson(server.maven) }),
    update: (domain: string, content: unknown) => {
      server[domain] = JSON.parse(JSON.stringify(content))
      return Promise.resolve({ data: {} })
    }
  }
})

describe('configuration store snapshots', () => {
  it('keeps the server state as the snapshot while the draft is edited', async () => {
    const server = { maven: { repositories: [{ id: 'releases', visibility: 'PUBLIC' }] } }
    const store = createConfigurationStore(clientRef(fakeClient(server)))
    await store.fetchConfiguration()

    store.setEntry('maven', 'repositories', 0, { id: 'releases', visibility: 'PRIVATE' })

    expect((store.savedSettings.value.maven.repositories as Repository[])[0].visibility, 'the saved state is not the draft').toBe('PUBLIC')
    expect((store.configurations.value.maven.repositories as Repository[])[0].visibility).toBe('PRIVATE')
  })

  it('moves the snapshot to the written state once the save is confirmed', async () => {
    const server = { maven: { repositories: [{ id: 'releases', visibility: 'PUBLIC' }] } }
    const store = createConfigurationStore(clientRef(fakeClient(server)))
    await store.fetchConfiguration()
    store.setEntry('maven', 'repositories', 0, { id: 'releases', visibility: 'PRIVATE' })

    await store.saveConfiguration()

    expect((store.savedSettings.value.maven.repositories as Repository[])[0].visibility).toBe('PRIVATE')
  })

  it('reports that the server hides secrets, as the injected flag says', async () => {
    const server = { maven: { repositories: [] } }
    const store = createConfigurationStore(clientRef(fakeClient(server)), { secretsHidden: () => true })

    await store.fetchConfiguration()

    expect(store.secretsHidden.value).toBe(true)
  })

  it('does not report hidden secrets unless the server says so', async () => {
    const server = { maven: { repositories: [] } }
    const store = createConfigurationStore(clientRef(fakeClient(server)))

    await store.fetchConfiguration()

    expect(store.secretsHidden.value).toBe(false)
  })

  it('stages an imported file in the draft without writing it to the server', async () => {
    const server = { maven: { repositories: [{ id: 'releases', visibility: 'PUBLIC' }] } }
    const client = fakeClient(server)
    let writes = 0
    client.settings.update = () => { writes++; return Promise.resolve({ data: {} }) }
    const store = createConfigurationStore(clientRef(client))
    await store.fetchConfiguration()

    store.importDomains({ maven: { repositories: [{ id: 'releases', visibility: 'PRIVATE' }] } })

    expect((store.configurations.value.maven.repositories as Repository[])[0].visibility).toBe('PRIVATE')
    expect(store.hasUnsavedChanges.value, 'the import is an unsaved change').toBe(true)
    expect((store.savedSettings.value.maven.repositories as Repository[])[0].visibility).toBe('PUBLIC')
    expect(writes).toBe(0)
  })
})
