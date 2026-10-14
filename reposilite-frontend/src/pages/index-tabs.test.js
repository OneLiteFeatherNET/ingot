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
import { resolveTab, withTab, tabLabel } from './index-tabs'

describe('resolveTab', () => {
  it('falls back to overview when no tab is requested', () => {
    expect(resolveTab(undefined, true)).toBe('overview')
  })

  it('selects a manager tab named in the query when the user is a manager', () => {
    expect(resolveTab('dashboard', true)).toBe('dashboard')
  })

  it('falls back to overview for a manager tab when the user is not a manager', () => {
    expect(resolveTab('dashboard', false)).toBe('overview')
  })

  it('falls back to overview for a tab name that does not exist', () => {
    expect(resolveTab('unknown', true)).toBe('overview')
  })

  it('ignores a repeated tab parameter and falls back to overview', () => {
    expect(resolveTab(['dashboard', 'tokens'], true)).toBe('overview')
  })

  it('selects overview when the query names it explicitly', () => {
    expect(resolveTab('overview', false)).toBe('overview')
  })
})

describe('withTab', () => {
  it('adds the tab to the query and keeps the other parameters', () => {
    expect(withTab({ q: 'x' }, 'tokens')).toEqual({ q: 'x', tab: 'tokens' })
  })

  it('removes the tab from the query when overview is selected', () => {
    expect(withTab({ q: 'x', tab: 'tokens' }, 'overview')).toEqual({ q: 'x' })
  })

  it('does not change the query it was given', () => {
    const query = { tab: 'tokens' }

    withTab(query, 'console')

    expect(query).toEqual({ tab: 'tokens' })
  })
})

describe('tabLabel', () => {
  it('returns the label the tab shows and the views expect', () => {
    expect(tabLabel('settings')).toBe('Settings')
  })
})
