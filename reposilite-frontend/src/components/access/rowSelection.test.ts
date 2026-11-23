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
import { selectedKey } from './rowSelection'

const ITEMS = [{ name: 'a' }, { name: 'b' }, { name: 'c' }]
const keyOf = (item: { name: string }) => item.name

describe('selectedKey', () => {
  it('returns the key of the row the route names', () => {
    expect(selectedKey(ITEMS, keyOf, 'b')).toBe('b')
  })

  it('returns null while the route names no row', () => {
    expect(selectedKey(ITEMS, keyOf, undefined)).toBeNull()
  })

  it('returns null when the named row is not in the list', () => {
    expect(selectedKey(ITEMS, keyOf, 'z')).toBeNull()
  })

  it('leaves exactly one row selected for a route that names one', () => {
    const key = selectedKey(ITEMS, keyOf, 'c')
    expect(ITEMS.filter(item => item.name === key).map(keyOf)).toEqual(['c'])
  })
})
