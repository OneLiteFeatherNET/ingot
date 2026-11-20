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

import { describe, expect, it } from 'vitest'
import { sortRows } from './MdDataTable.sort'

// Every test builds its own rows, so no test sees the order another one produced.
const rows = () => [
  { name: 'releases', size: 30 },
  { name: 'snapshots', size: 5 },
  { name: 'docs', size: 12 },
]

const names = (sorted: { name: string }[]) => sorted.map((row) => row.name)

describe('sortRows', () => {
  it('sorts text ascending', () => {
    expect(names(sortRows(rows(), 'name', 'ascending'))).toEqual(['docs', 'releases', 'snapshots'])
  })

  it('sorts text descending', () => {
    expect(names(sortRows(rows(), 'name', 'descending'))).toEqual(['snapshots', 'releases', 'docs'])
  })

  it('sorts numbers by value, not by text', () => {
    expect(names(sortRows(rows(), 'size', 'ascending'))).toEqual(['snapshots', 'docs', 'releases'])
  })

  it('sorts numeric text in its natural order, so 10 comes after 9', () => {
    const sorted = sortRows([{ name: 'v10' }, { name: 'v9' }], 'name', 'ascending')

    expect(names(sorted)).toEqual(['v9', 'v10'])
  })

  it('keeps rows with equal keys in their given order in both directions', () => {
    const tied = [
      { name: 'a', group: 'maven' },
      { name: 'b', group: 'maven' },
      { name: 'c', group: 'maven' },
    ]

    expect(names(sortRows(tied, 'group', 'ascending')), 'ascending is stable').toEqual(['a', 'b', 'c'])
    expect(names(sortRows(tied, 'group', 'descending')), 'descending is stable').toEqual(['a', 'b', 'c'])
  })

  it('places a missing value like an empty text', () => {
    const sorted = sortRows([{ name: 'b', tag: 'x' }, { name: 'a' }], 'tag', 'ascending')

    expect(names(sorted)).toEqual(['a', 'b'])
  })

  it('does not change the given array', () => {
    const input = rows()
    const before = names(input)

    const sorted = sortRows(input, 'name', 'ascending')

    expect(names(input), 'the input keeps its order').toEqual(before)
    expect(sorted, 'the result is a new array').not.toBe(input)
  })
})
