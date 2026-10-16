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
import { mount } from '@vue/test-utils'
import { markRaw } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { createAjv } from '@jsonforms/core'
import { useConfiguration } from '../../store/configuration'
import { entryLayout } from './collections'

// The configuration store reads the session at import time, which needs no server here.
vi.mock('../../store/session', () => ({ useSession: () => ({ client: { value: null } }) }))

/* The repository item of the maven settings, with the storage provider variants as the server describes them. */
const repositoryItem = {
  type: 'object',
  title: 'Repository',
  properties: {
    id: { type: 'string', title: 'Id' },
    proxied: {
      title: 'Mirrored repositories',
      type: 'array',
      items: {
        type: 'object',
        properties: {
          reference: { type: 'string', title: 'Link' },
          username: { type: 'string', title: 'Username' },
          password: { type: 'string', title: 'Password' }
        }
      }
    },
    storageProvider: {
      oneOf: [
        {
          type: 'object',
          title: 'Filesystem Storage Provider',
          properties: {
            type: { type: 'string', const: 'fs' },
            rootDirectory: { type: 'string', title: 'Root directory' }
          },
          additionalProperties: false
        },
        {
          type: 'object',
          title: 'S3 Storage Provider',
          properties: {
            type: { type: 'string', const: 's3' },
            bucketName: { type: 'string', title: 'Bucket' },
            endpoint: { type: 'string', title: 'Endpoint' },
            accessKey: { type: 'string', title: 'Access Key' },
            secretKey: { type: 'string', title: 'Secret Key' },
            region: { type: 'string', title: 'Region' }
          },
          additionalProperties: false
        }
      ]
    }
  }
}

const mountEntry = (entry) => {
  const { renderers } = useConfiguration()
  return mount(JsonForms, {
    props: {
      data: entry,
      schema: repositoryItem,
      uischema: entryLayout(repositoryItem),
      renderers,
      config: { showUnfocusedDescription: true },
      ajv: markRaw(createAjv({ useDefaults: true }))
    }
  })
}

const s3Entry = () => ({
  id: 'releases',
  storageProvider: { type: 's3', bucketName: 'releases', endpoint: 'https://s3.example', accessKey: 'AKIA', secretKey: 'fff', region: 'eu' }
})

const inputLabelled = (wrapper, title) => wrapper.findAll('input').find((input) => input.element.labels?.[0]?.textContent.trim().startsWith(title))

describe('secret fields inside the S3 storage provider variant', () => {
  it('masks the Secret Key input with type password', () => {
    const wrapper = mountEntry(s3Entry())

    expect(inputLabelled(wrapper, 'Secret Key').attributes('type')).toBe('password')
  })

  it('offers a Show secret toggle for the Secret Key', () => {
    const wrapper = mountEntry(s3Entry())

    expect(wrapper.find('button[aria-label="Show secret"]').exists()).toBe(true)
  })

  it('keeps browsers from filling the Secret Key and from spellchecking it', () => {
    const input = inputLabelled(mountEntry(s3Entry()), 'Secret Key')

    expect(input.attributes('autocomplete')).toBe('new-password')
    expect(input.attributes('spellcheck')).toBe('false')
  })

  it('keeps the bucket visible and offers no toggle for it', () => {
    const wrapper = mountEntry(s3Entry())

    expect(inputLabelled(wrapper, 'Bucket').attributes('type')).toBe('text')
  })

  it('masks a secret typed after switching the provider from file system to S3', async () => {
    const wrapper = mountEntry({ id: 'releases', storageProvider: { type: 'fs' } })

    const variantSelect = wrapper.findAll('[id]').find((element) => element.attributes('id').endsWith('-variant'))
    await variantSelect.setValue('1')
    await inputLabelled(wrapper, 'Secret Key').setValue('fff')

    expect(inputLabelled(wrapper, 'Secret Key').attributes('type')).toBe('password')
  })

  it('masks the password of a mirrored repository', () => {
    const wrapper = mountEntry({ id: 'releases', proxied: [{ reference: 'other', username: 'u', password: 'pw' }] })

    expect(inputLabelled(wrapper, 'Password').attributes('type')).toBe('password')
  })
})
