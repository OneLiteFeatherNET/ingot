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

export type SortDirection = 'ascending' | 'descending'

// A cell value as text: text stays itself, a number or a boolean is written out, anything else counts as empty text.
const textOf = (value: unknown): string => {
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'bigint') return String(value)
  return ''
}

// Numbers compare by value and text by its natural order, so "10" comes after "9".
const compare = (a: unknown, b: unknown) => {
  if (typeof a === 'number' && typeof b === 'number') return a - b
  return textOf(a).localeCompare(textOf(b), undefined, { numeric: true })
}

/*
 * The rows sorted by the value under one key, as a new array. Rows with equal values keep their given order in both
 * directions, because Array.prototype.sort is stable and the direction only flips the sign of the comparison.
 */
export const sortRows = <Row extends Record<string, unknown>>(rows: readonly Row[], column: string, direction: SortDirection): Row[] => {
  const factor = direction === 'ascending' ? 1 : -1
  return [...rows].sort((a, b) => factor * compare(a[column], b[column]))
}
