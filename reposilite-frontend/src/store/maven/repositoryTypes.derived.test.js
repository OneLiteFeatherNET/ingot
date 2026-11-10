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
import { derivedTypeOf, effectiveTypeOf } from './repositoryTypes'

// The same table as RepositoryTypeTest in the backend: local references, external references, derived type.
const LOCAL = 'releases'
const EXTERNAL = 'https://repo.example.net/maven'
const withReferences = (references) => ({ id: 'all', proxied: references.map(reference => ({ reference })) })

describe('derived type of an untyped repository, as the server derives it', () => {
  it.each([
    ['no references', [], 'HOSTED'],
    ['only local references', [LOCAL, 'snapshots'], 'GROUP'],
    ['only external references', [EXTERNAL, 'https://a.example/', 'https://b.example/'], 'PROXY'],
    ['local and external references', [LOCAL, EXTERNAL], 'MIXED']
  ])('derives the type for %s', (_, references, expected) => {
    expect(derivedTypeOf(withReferences(references))).toBe(expected)
  })

  it('ignores blank references, so an entry that names nothing does not change the type', () => {
    expect(derivedTypeOf(withReferences(['', '   ', '/']))).toBe('HOSTED')
    expect(derivedTypeOf(withReferences([LOCAL, '']))).toBe('GROUP')
  })

  it('treats a reference that is not an http or https address as local', () => {
    expect(derivedTypeOf(withReferences(['ftp://files.example/']))).toBe('GROUP')
  })

  it('derives hosted for an entry without a mirrored list at all', () => {
    expect(derivedTypeOf({ id: 'releases' })).toBe('HOSTED')
  })
})

describe('effective type of a repository, where a declared type wins', () => {
  it('reports the declared type, whatever the references say', () => {
    expect(effectiveTypeOf({ id: 'a', type: 'PROXY', proxied: [{ reference: LOCAL }] })).toEqual({ type: 'PROXY', declared: true })
  })

  it('reports the derived type and says it is not declared for an untyped repository', () => {
    expect(effectiveTypeOf({ id: 'a', proxied: [{ reference: LOCAL }] })).toEqual({ type: 'GROUP', declared: false })
  })

  it('treats an unknown declared value as undeclared', () => {
    expect(effectiveTypeOf({ id: 'a', type: 'ARCHIVE' })).toEqual({ type: 'HOSTED', declared: false })
  })
})
