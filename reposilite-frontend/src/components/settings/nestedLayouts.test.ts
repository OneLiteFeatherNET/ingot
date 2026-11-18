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
import { mount, type VueWrapper } from '@vue/test-utils'
import { markRaw } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { createAjv, findMatchingUISchema } from '@jsonforms/core'
import type { ControlElement, JsonSchema7, UISchemaElement } from '@jsonforms/core'
import { useConfiguration } from '../../store/configuration'
import { nestedUischemas } from './nestedLayouts'

const { renderers } = useConfiguration()

/* The schemas as the backend generates them, with the property order of the Kotlin classes. */
const mirrorSchema: JsonSchema7 = {
  type: 'object',
  title: 'Mirrored Maven Repository',
  properties: {
    reference: { type: 'string', title: 'Link' },
    store: { type: 'boolean', title: 'Store' },
    allowedGroups: { type: 'array', title: 'Allowed Groups', items: { type: 'string' } },
    allowedExtensions: { type: 'array', title: 'Allowed Extensions', items: { type: 'string' } },
    connectTimeout: { type: 'integer', title: 'Connect Timeout' },
    readTimeout: { type: 'integer', title: 'Read Timeout' },
    authorization: {
      type: 'object',
      title: 'Mirror Credentials',
      properties: {
        method: { type: 'string', title: 'Method' },
        login: { type: 'string', title: 'Login' },
        password: { type: 'string', title: 'Password' }
      }
    },
    httpProxy: { type: 'string', title: 'HTTP Proxy' },
    authenticatedFetchingOnly: { type: 'boolean', title: 'Authenticated Fetching Only' },
    checksumPolicy: { type: 'string', title: 'Checksum policy' }
  }
}

const s3Schema: JsonSchema7 = {
  type: 'object',
  title: 'S3 Storage Provider',
  properties: {
    type: { type: 'string', const: 's3' },
    bucketName: { type: 'string', title: 'Bucket' },
    endpoint: { type: 'string', title: 'Endpoint' },
    accessKey: { type: 'string', title: 'Access Key' },
    secretKey: { type: 'string', title: 'Secret Key' },
    region: { type: 'string', title: 'Region' },
    prefix: { type: 'string', title: 'Key Prefix' },
    sharedBucket: { type: 'boolean', title: 'Shared Bucket' },
    metadataCacheSettings: { type: 'object', title: 'Local Metadata Cache', properties: {} }
  }
}

const fileSystemSchema: JsonSchema7 = {
  type: 'object',
  title: 'File system Storage Provider',
  properties: {
    type: { type: 'string', const: 'fs' },
    quota: { type: 'string', title: 'Quota' },
    mount: { type: 'string', title: 'Mount' }
  }
}

const ldapSchema: JsonSchema7 = {
  type: 'object',
  title: 'LDAP',
  properties: {
    enabled: { type: 'boolean', title: 'Enabled' },
    ssl: { type: 'boolean', title: 'SSL' },
    hostname: { type: 'string', title: 'Hostname' },
    port: { type: 'integer', title: 'Port' },
    baseDn: { type: 'string', title: 'Base DN' },
    searchUserDn: { type: 'string', title: 'Search-User DN' },
    searchUserPassword: { type: 'string', title: 'Search-User Password' },
    typeAttribute: { type: 'string', title: 'Type Attribute' },
    userAttribute: { type: 'string', title: 'User Attribute' },
    userFilter: { type: 'string', title: 'User Filter' },
    userType: { type: 'string', title: 'User Type' }
  }
}

const breachCheckSchema: JsonSchema7 = {
  type: 'object',
  title: 'Breached Password Check',
  properties: {
    mode: { type: 'string', title: 'Mode', enum: ['OFF', 'RANGE', 'OFFLINE'] },
    file: { type: 'string', title: 'File' },
    rangeUrl: { type: 'string', title: 'Range URL' },
    failClosed: { type: 'boolean', title: 'Fail Closed' }
  }
}

const mountProperty = (schema: JsonSchema7, data: Record<string, unknown>, field: string): VueWrapper => mount(JsonForms, {
  props: {
    data,
    schema: { type: 'object', properties: { [field]: schema } },
    uischema: { type: 'VerticalLayout', elements: [{ type: 'Control', scope: `#/properties/${field}` }] } as UISchemaElement,
    renderers,
    uischemas: nestedUischemas,
    config: { showUnfocusedDescription: true },
    ajv: markRaw(createAjv({ useDefaults: true }))
  }
})

/*
 * The names of the drawn controls and list headings, in the order they are drawn. A switch names itself through
 * aria-labelledby, a text field through its label, a list through its heading. Names not in the expected list are left out.
 */
const shownLabels = (wrapper: VueWrapper, expected: string[]): string[] => {
  const root = wrapper.element as HTMLElement
  const nameOf = (control: Element): string => {
    const labelledBy = control.getAttribute('aria-labelledby')
    const text = control.tagName === 'H3'
      ? control.textContent
      : labelledBy !== null
        ? root.querySelector(`[id="${labelledBy}"]`)?.textContent
        : root.querySelector(`label[for="${control.id}"]`)?.textContent
    return (text ?? '').replace(/\*$/, '').trim()
  }
  return [...root.querySelectorAll('input, textarea, [role="combobox"], button[role="switch"], h3')]
    .map(nameOf)
    .filter(name => expected.includes(name))
}

const scopesOf = (layout: UISchemaElement | undefined): string[] => ((layout as { elements?: UISchemaElement[] } | undefined)?.elements ?? [])
  .map(element => (element as ControlElement).scope.replace('#/properties/', ''))

const layoutOf = (schema: JsonSchema7): UISchemaElement | undefined => findMatchingUISchema(nestedUischemas)(schema, '#/properties/field', '')

describe('nested form registry', () => {
  it('matches the mirrored repository by its title', () => {
    expect(layoutOf(mirrorSchema)).toBeDefined()
  })

  it('lists the mirror fields in task order: reference, credentials, store and checksum policy first', () => {
    expect(scopesOf(layoutOf(mirrorSchema))).toEqual([
      'reference',
      'authorization',
      'store',
      'checksumPolicy',
      'authenticatedFetchingOnly',
      'allowedGroups',
      'allowedExtensions',
      'httpProxy',
      'connectTimeout',
      'readTimeout'
    ])
  })

  it('lists the S3 fields with the region before the credentials', () => {
    expect(scopesOf(layoutOf(s3Schema))).toEqual([
      'type',
      'bucketName',
      'region',
      'endpoint',
      'prefix',
      'sharedBucket',
      'accessKey',
      'secretKey',
      'metadataCacheSettings'
    ])
  })

  it('lists the file system fields with the mount before the quota', () => {
    expect(scopesOf(layoutOf(fileSystemSchema))).toEqual(['type', 'mount', 'quota'])
  })

  it('lists the LDAP fields of Maven clients with the host and port before the SSL switch', () => {
    expect(scopesOf(layoutOf(ldapSchema))).toEqual([
      'enabled',
      'hostname',
      'port',
      'ssl',
      'baseDn',
      'searchUserDn',
      'searchUserPassword',
      'userFilter',
      'typeAttribute',
      'userAttribute',
      'userType'
    ])
  })

  it('lists the breached password check with the range URL and file after the mode', () => {
    expect(scopesOf(layoutOf(breachCheckSchema))).toEqual(['mode', 'rangeUrl', 'file', 'failClosed'])
  })

  it('leaves the LDAP sign-in of the login page to the generated order', () => {
    const loginLdap: JsonSchema7 = {
      type: 'object',
      title: 'LDAP',
      properties: { enabled: { type: 'boolean' }, url: { type: 'string' } }
    }
    expect(layoutOf(loginLdap)).toBeUndefined()
  })

  it('keeps a field that the registry does not name, after the named ones', () => {
    const mirrorWithFuture: JsonSchema7 = {
      ...mirrorSchema,
      properties: { ...(mirrorSchema.properties as Record<string, JsonSchema7>), futureSetting: { type: 'string', title: 'Future' } }
    }
    const scopes = scopesOf(layoutOf(mirrorWithFuture))
    expect(scopes).toHaveLength(11)
    expect(scopes.at(-1)).toBe('futureSetting')
  })

  it('hands out a fresh layout on every lookup, so renderers that write into it do not share it', () => {
    expect(layoutOf(mirrorSchema)).not.toBe(layoutOf(mirrorSchema))
  })
})

describe('nested form order on screen', () => {
  it('draws the mirrored repository with the credentials and the store ahead of the timeouts', () => {
    const wrapper = mountProperty(mirrorSchema, { reference: 'central' }, 'mirror')

    expect(shownLabels(wrapper, ['Link', 'Method', 'Login', 'Password', 'Store', 'Checksum policy', 'Authenticated Fetching Only', 'Allowed Groups', 'Allowed Extensions', 'HTTP Proxy', 'Connect Timeout', 'Read Timeout']))
      .toEqual(['Link', 'Method', 'Login', 'Password', 'Store', 'Checksum policy', 'Authenticated Fetching Only', 'Allowed Groups', 'Allowed Extensions', 'HTTP Proxy', 'Connect Timeout', 'Read Timeout'])
  })

  it('draws the S3 provider with the region before the access key', () => {
    const wrapper = mountProperty(s3Schema, { storageProvider: { type: 's3' } }, 'storageProvider')

    expect(shownLabels(wrapper, ['Bucket', 'Endpoint', 'Access Key', 'Secret Key', 'Region', 'Key Prefix', 'Shared Bucket']))
      .toEqual(['Bucket', 'Region', 'Endpoint', 'Key Prefix', 'Shared Bucket', 'Access Key', 'Secret Key'])
  })

  it('draws the file system provider with the mount before the quota', () => {
    const wrapper = mountProperty(fileSystemSchema, { type: 'fs' }, 'storageProvider')

    expect(shownLabels(wrapper, ['Quota', 'Mount'])).toEqual(['Mount', 'Quota'])
  })

  it('draws the LDAP directory of Maven clients with the host and port ahead of the SSL switch', () => {
    const wrapper = mountProperty(ldapSchema, { ldap: {} }, 'ldap')

    expect(shownLabels(wrapper, ['Enabled', 'SSL', 'Hostname', 'Port'])).toEqual(['Enabled', 'Hostname', 'Port', 'SSL'])
  })

  it('draws the breached password check with the mode first and the file after the range URL', () => {
    const wrapper = mountProperty(breachCheckSchema, { breachCheck: { mode: 'OFF' } }, 'breachCheck')

    expect(shownLabels(wrapper, ['Mode', 'File', 'Range URL', 'Fail Closed'])).toEqual(['Mode', 'Range URL', 'File', 'Fail Closed'])
  })
})
