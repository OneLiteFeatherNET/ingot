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
 * Fixtures of the Repositories page tests. Every call builds fresh objects, so no test shares state with another.
 * The schema is the maven repository item, reduced to the fields the page reads.
 */

import { ref } from 'vue'
import { createConfigurationStore } from '../../../store/configuration'

// A repository as the server serves it. The fixtures build it field by field, so only the keys are named here.
export type RepositoryRecord = Record<string, unknown>

// The served maven schema, reduced to the repositories collection the page reads.
export interface RepositorySchema {
  type: string
  title: string
  properties: {
    repositories: {
      title: string
      description: string
      type: string
      items: { type: string; title: string; properties: Record<string, unknown> }
    }
    [domainField: string]: unknown
  }
}

// The client the configuration store reads, as its own signature names it.
type ClientRef = Parameters<typeof createConfigurationStore>[0]

// The server state of the fixtures, keyed by domain.
export type ServerState = Record<string, { repositories: RepositoryRecord[] }>

/* The item schema of a maven repository. The policy fields are added only when a test asks for them. */
export const repositorySchema = ({ versionPolicy = false, readOnly = false, redeployment = true }: { versionPolicy?: boolean; readOnly?: boolean; redeployment?: boolean } = {}): RepositorySchema => {
  const properties: Record<string, unknown> = {
    id: { type: 'string', title: 'Id' },
    type: { type: 'string', enum: ['HOSTED', 'PROXY', 'GROUP'], title: 'Type' },
    visibility: { type: 'string', enum: ['PUBLIC', 'HIDDEN', 'PRIVATE'], title: 'Visibility' },
    description: { type: 'string', title: 'Description' },
    tags: { type: 'array', items: { type: 'string' }, title: 'Tags' },
    storageProvider: {
      oneOf: [
        {
          type: 'object',
          title: 'File system Storage Provider',
          properties: { type: { type: 'string', const: 'fs' } },
          additionalProperties: false
        },
        {
          type: 'object',
          title: 'S3 Storage Provider',
          properties: { type: { type: 'string', const: 's3' }, bucketName: { type: 'string', title: 'Bucket' } },
          additionalProperties: false
        }
      ]
    },
    proxied: {
      type: 'array',
      title: 'Mirrored repositories',
      items: { type: 'object', properties: { reference: { type: 'string', title: 'Reference' } } }
    }
  }
  if (redeployment) properties.redeployment = { type: 'boolean', title: 'Redeployment' }
  if (readOnly) properties.readOnly = { type: 'boolean', title: 'Read only' }
  if (versionPolicy) properties.versionPolicy = { type: 'string', enum: ['RELEASE', 'SNAPSHOT', 'MIXED'], title: 'Version policy' }
  return {
    type: 'object',
    title: 'Maven',
    properties: {
      repositories: {
        title: 'Repositories',
        description: 'List of Maven repositories.',
        type: 'array',
        items: { type: 'object', title: 'Repository', properties }
      }
    }
  }
}

/*
 * One declared hosted, one untyped hosted, one untyped proxy, one untyped mixed and one declared group. The untyped
 * entries are what the page derives; `all` names `releases` as a member, which the delete dialog must list.
 */
export const repositoriesServer = (): { maven: { repositories: RepositoryRecord[] } } => ({
  maven: {
    repositories: [
      {
        id: 'releases',
        type: 'HOSTED',
        visibility: 'PUBLIC',
        description: 'Release artifacts',
        tags: ['team-a'],
        storageProvider: { type: 'fs' },
        redeployment: false,
        readOnly: false,
        versionPolicy: 'RELEASE'
      },
      { id: 'snapshots', visibility: 'PRIVATE', storageProvider: { type: 's3', bucketName: 'snap' }, redeployment: true },
      { id: 'central', visibility: 'PUBLIC', proxied: [{ reference: 'https://repo.example.org/maven' }] },
      {
        id: 'mixed',
        visibility: 'PUBLIC',
        proxied: [{ reference: 'https://repo.example.org/maven' }, { reference: 'releases' }]
      },
      { id: 'all', type: 'GROUP', visibility: 'PUBLIC', proxied: [{ reference: 'releases' }] }
    ]
  }
})

const fakeClient = (server: ServerState, schema: RepositorySchema) => ({
  settings: {
    domains: () => Promise.resolve({ data: Object.keys(server) }),
    schema: () => Promise.resolve({ data: schema }),
    fetch: (domain: string) => Promise.resolve({ data: JSON.parse(JSON.stringify(server[domain])) as unknown }),
    update: () => Promise.resolve({ data: {} })
  }
})

/* A configuration store with the server state loaded, as the settings page has it after its first fetch. */
export const loadedStore = async ({ server = repositoriesServer(), schema = repositorySchema({ versionPolicy: true, readOnly: true }) }: { server?: ServerState; schema?: RepositorySchema } = {}) => {
  // The fake answers the four settings calls the store makes. The rest of the client is never reached from these tests.
  const store = createConfigurationStore(ref(fakeClient(server, schema)) as unknown as ClientRef)
  const loaded = await store.fetchConfiguration()
  if (!loaded) throw new Error('the fixture configuration should load')
  return store
}

/* The schema fields the page is given, as SettingsView derives them from the served schema. */
export const schemaFieldsOf = (schema: RepositorySchema): string[] => Object.keys(schema.properties.repositories.items.properties)
