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
import { listRows, MAX_LIST_ROWS } from './pluginBlocks'

const items = (count: number) => Array.from({ length: count }, (_, index) => ({ title: `item ${index}` }))

describe('listRows skips what has no title', () => {
  it('skips an item without a title and counts it', () => {
    const rows = listRows([{ title: 'kept' }, { subtitle: 'no title' }, 'text only'])

    expect(rows.shown.map(row => row.title), 'the rows shown').toEqual(['kept'])
    expect(rows.skipped, 'the skipped items').toBe(2)
  })

  it('skips an item with an empty title', () => {
    const rows = listRows([{ title: '' }])

    expect(rows.total, 'the rows').toBe(0)
  })
})

describe('listRows cuts at the limit and says so', () => {
  it('shows at most 100 rows and keeps the total', () => {
    const rows = listRows(items(120))

    expect(MAX_LIST_ROWS, 'the limit').toBe(100)
    expect(rows.shown, 'the rows shown').toHaveLength(100)
    expect(rows.total, 'the total').toBe(120)
    expect(rows.cut, 'the cut flag').toBe(true)
  })

  it('does not cut 100 rows', () => {
    expect(listRows(items(100)).cut, 'the cut flag').toBe(false)
  })
})

describe('listRows keeps the chips of an item', () => {
  it('keeps at most 8 chips and drops those that are not text', () => {
    const chips = [1, 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i']
    const [row] = listRows([{ title: 'chips', chips }]).shown

    expect(row.chips, 'the chips').toEqual(['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'])
  })
})

describe('listRows checks the link of each row', () => {
  it('keeps a same-origin href as the link of the row', () => {
    const [row] = listRows([{ title: 'jar', href: '/browse/repo/a/b.jar' }]).shown

    expect(row.link, 'the link').toBe('/browse/repo/a/b.jar')
    expect(row.linkRefused, 'the refusal').toBe(false)
  })

  it('refuses a javascript href and marks the row as refused', () => {
    const [row] = listRows([{ title: 'bad', href: 'javascript:alert(1)' }]).shown

    expect(row.link, 'the link').toBeNull()
    expect(row.linkRefused, 'the refusal').toBe(true)
  })

  it('shows no link and no refusal for a row without an href', () => {
    const [row] = listRows([{ title: 'plain' }]).shown

    expect(row.link, 'the link').toBeNull()
    expect(row.linkRefused, 'the refusal').toBe(false)
  })
})
