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
import { filterRows, tabForFragment } from './systemInfoTabs'

// Pure rules of the system information page: no component, no router and no clock.
describe('tabForFragment', () => {
  it.each([
    ['#support-ingot', 'overview'],
    ['#support-java', 'overview'],
    ['#support-os', 'overview'],
    ['#support-database', 'overview'],
    ['#support-storage', 'overview'],
    ['#support-cluster', 'overview'],
  ])('opens Overview for the card fragment %s', (hash, tab) => {
    expect(tabForFragment(hash), `the card ${hash} sits on Overview`).toBe(tab)
  })

  it('opens Plugins for the plugins fragment', () => {
    expect(tabForFragment('#support-plugins')).toBe('plugins')
  })

  it.each(['#support-environment', '#support-properties'])('opens Environment for %s', (hash) => {
    expect(tabForFragment(hash)).toBe('environment')
  })

  it('opens Configuration for the configuration fragment', () => {
    expect(tabForFragment('#support-configuration')).toBe('configuration')
  })

  it('returns null for an unknown fragment, so the browser handles it', () => {
    expect(tabForFragment('#somewhere-else')).toBeNull()
  })

  it('returns null when the address has no fragment', () => {
    expect(tabForFragment('')).toBeNull()
  })
})

describe('filterRows', () => {
  const rows: [string, string][] = [
    ['JAVA_HOME', '/usr/lib/jvm/temurin'],
    ['PUID', '1000'],
    ['INGOT_TOKEN', '[withheld]'],
  ]

  it('keeps every row for an empty filter', () => {
    expect(filterRows(rows, '')).toEqual(rows)
  })

  it('matches the key without regard to case', () => {
    expect(filterRows(rows, 'java')).toEqual([['JAVA_HOME', '/usr/lib/jvm/temurin']])
  })

  it('matches the value without regard to case', () => {
    expect(filterRows(rows, 'TEMURIN')).toEqual([['JAVA_HOME', '/usr/lib/jvm/temurin']])
  })

  it('keeps no row when nothing matches', () => {
    expect(filterRows(rows, 'missing')).toEqual([])
  })

  it('matches the masked text the server sent, and never a value that is not on the page', () => {
    expect(filterRows(rows, 'withheld')).toEqual([['INGOT_TOKEN', '[withheld]']])
    expect(filterRows(rows, 'secret'), 'the hidden value is not on the page, so it cannot match').toEqual([])
  })

  it('keeps the rows in the order given', () => {
    expect(filterRows(rows, 'i').map(([key]) => key)).toEqual(['JAVA_HOME', 'INGOT_TOKEN'])
  })
})
