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
import { isSensitive, keyOf } from './sensitive.js'

const plain = { type: 'string' }

describe('isSensitive', () => {
  it.each(['secretKey', 'Secret', 'password', 'Proxy_PASSWORD'])('masks the key %s', (key) => {
    expect(isSensitive(key, plain)).toBe(true)
  })

  it.each(['accessKey', 'access-key', 'access_key', 'AccessKey'])('masks the access key %s', (key) => {
    expect(isSensitive(key, plain)).toBe(true)
  })

  it.each(['endpoint', 'Endpoint'])('masks the endpoint %s', (key) => {
    expect(isSensitive(key, plain)).toBe(true)
  })

  it('masks any string with the password format, whatever its key', () => {
    expect(isSensitive('token', { type: 'string', format: 'password' })).toBe(true)
  })

  it.each(['bucketName', 'region', 'id', 'rootDirectory', 'reference'])('leaves the ordinary key %s visible', (key) => {
    expect(isSensitive(key, plain)).toBe(false)
  })

  it('matches on the last segment of a nested path', () => {
    expect(isSensitive('proxied.0.password', plain)).toBe(true)
    expect(isSensitive('storageProvider.secretKey', plain)).toBe(true)
    expect(isSensitive('storageProvider.bucketName', plain)).toBe(false)
  })

  it('is false for a missing path', () => {
    expect(isSensitive(undefined, plain)).toBe(false)
  })
})

describe('keyOf', () => {
  it('returns the last segment of a path', () => {
    expect(keyOf('storageProvider.secretKey')).toBe('secretKey')
  })

  it('returns an empty string for a missing path', () => {
    expect(keyOf(null)).toBe('')
  })
})
