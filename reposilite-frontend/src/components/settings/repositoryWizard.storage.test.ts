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
import {
  emptyDraft,
  repositoryEntry,
  storageVariantsOf,
  pluginFieldsOf,
  unavailableStorageTypeOf,
  validateStorage
} from './repositoryWizard'
import type { WizardDraft } from './repositoryWizard'

// The storage variant of a served schema: the discriminator is a constant, as the backend writes it.
const variant = (type: string, title: string, properties: Record<string, object>, required: string[] = []) => ({
  type: 'object',
  title,
  properties: { type: { type: 'string', const: type }, ...properties },
  required: ['type', ...required]
})

const fileSystem = variant('fs', 'File system Storage Provider', { quota: { type: 'string', title: 'Quota' }, mount: { type: 'string' } })
const s3 = variant('s3', 'S3 Storage Provider', { bucketName: { type: 'string' }, region: { type: 'string' } }, ['bucketName'])
const fakeStore = variant('fake-store', 'Fake Store Storage Provider', {
  endpoint: { type: 'string', title: 'Endpoint' },
  retries: { type: 'integer', title: 'Retries' }
}, ['endpoint'])
const zetaStore = variant('zeta-store', 'Zeta Storage Provider', { room: { type: 'string' } })
const alphaStore = variant('alpha-store', 'Alpha Storage Provider', { room: { type: 'string' } })

// A served repository item: the built-in variants first, then a plugin variant listed in the order the server gives it.
const served = (oneOf: object[]) => ({ type: 'object', properties: { storageProvider: { oneOf } } })

const draftWith = (storage: Partial<WizardDraft['storage']>): WizardDraft => ({
  ...emptyDraft(),
  type: 'HOSTED',
  storage: { ...emptyDraft().storage, ...storage }
})

const NO_OPTIONAL = { description: false, tags: false }

describe('storage kinds from the served schema', () => {
  it('offers the built-in variants when the schema has no storage property', () => {
    expect(storageVariantsOf(undefined).map(entry => entry.value)).toEqual(['fs', 's3'])
  })

  it('offers the served variants, with the plugin variant after the built-in ones', () => {
    expect(storageVariantsOf(served([fileSystem, s3, fakeStore])).map(entry => entry.value)).toEqual(['fs', 's3', 'fake-store'])
  })

  it('sorts plugin variants by their type id, whatever order the schema lists them in', () => {
    expect(storageVariantsOf(served([fileSystem, s3, zetaStore, alphaStore])).map(entry => entry.value))
      .toEqual(['fs', 's3', 'alpha-store', 'zeta-store'])
  })

  it('labels a plugin variant with its title', () => {
    expect(storageVariantsOf(served([fileSystem, s3, fakeStore])).at(-1)?.label).toBe('Fake Store Storage Provider')
  })
})

describe('plugin fields of a variant', () => {
  it('lists the fields of the variant in the order of its schema, without the discriminator', () => {
    expect(pluginFieldsOf(served([fileSystem, s3, fakeStore]), 'fake-store').map(field => field.name)).toEqual(['endpoint', 'retries'])
  })

  it('marks the required fields of the variant', () => {
    const fields = pluginFieldsOf(served([fileSystem, s3, fakeStore]), 'fake-store')
    expect(fields.find(field => field.name === 'endpoint')?.required).toBe(true)
    expect(fields.find(field => field.name === 'retries')?.required).toBe(false)
  })

  it('lists no fields for a kind the schema does not offer', () => {
    expect(pluginFieldsOf(served([fileSystem, s3]), 'fake-store')).toEqual([])
  })
})

describe('validation of a plugin variant', () => {
  it('asks for a required field of the plugin variant that is blank', () => {
    const storage = draftWith({ kind: 'fake-store', plugin: { endpoint: '  ' } }).storage
    expect(validateStorage(storage, { required: ['endpoint'] }).endpoint).toBe('Enter a value for Endpoint.')
  })

  it('accepts a plugin variant whose required field is filled', () => {
    const storage = draftWith({ kind: 'fake-store', plugin: { endpoint: 'https://store.example.net' } }).storage
    expect(validateStorage(storage, { required: ['endpoint'] })).toEqual({})
  })
})

describe('saving a plugin variant', () => {
  const options = { optional: NO_OPTIONAL, storageVariants: storageVariantsOf(served([fileSystem, s3, fakeStore])) }

  it('writes the type and the filled fields, and leaves the blank optional fields out', () => {
    const draft = draftWith({ kind: 'fake-store', plugin: { endpoint: 'https://store.example.net', retries: '' } })
    expect(repositoryEntry(draft, options).storageProvider).toEqual({ type: 'fake-store', endpoint: 'https://store.example.net' })
  })

  it('writes an integer field as a number', () => {
    const draft = draftWith({ kind: 'fake-store', plugin: { endpoint: 'https://store.example.net', retries: '3' } })
    expect(repositoryEntry(draft, options).storageProvider).toEqual({ type: 'fake-store', endpoint: 'https://store.example.net', retries: 3 })
  })

  it('keeps writing the file system block as it does today', () => {
    expect(repositoryEntry(draftWith({ kind: 'fs', quota: '10GB' }), options).storageProvider).toEqual({ type: 'fs', quota: '10GB' })
  })
})

describe('a stored storage type the schema does not offer', () => {
  it('names the type that is not installed', () => {
    expect(unavailableStorageTypeOf({ storageProvider: { type: 'azure-blob' } }, served([fileSystem, s3]))).toBe('azure-blob')
  })

  it('names nothing for a built-in or an offered type', () => {
    expect(unavailableStorageTypeOf({ storageProvider: { type: 's3' } }, served([fileSystem, s3]))).toBeNull()
    expect(unavailableStorageTypeOf({ storageProvider: { type: 'fake-store' } }, served([fileSystem, s3, fakeStore]))).toBeNull()
  })

  it('names nothing for an entry without storage', () => {
    expect(unavailableStorageTypeOf({}, served([fileSystem, s3]))).toBeNull()
  })
})

describe('the order of the fields of a variant', () => {
  it('follows the display layout of the variant when one is registered for its title', () => {
    const s3InClassOrder = variant('s3-like', 'S3 Storage Provider', {
      accessKey: { type: 'string' },
      secretKey: { type: 'string' },
      bucketName: { type: 'string' },
      region: { type: 'string' }
    })
    expect(pluginFieldsOf(served([fileSystem, s3, s3InClassOrder]), 's3-like').map(field => field.name), 'the registered layout order')
      .toEqual(['bucketName', 'region', 'accessKey', 'secretKey'])
  })

  it('keeps the schema order for a variant without a registered layout', () => {
    expect(pluginFieldsOf(served([fileSystem, s3, fakeStore]), 'fake-store').map(field => field.name), 'the settings class order')
      .toEqual(['endpoint', 'retries'])
  })
})
