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
import { entryLayout } from './collections'
import type { SettingsSchema } from './collections'
import { errorCountsOf, firstErrorTab, labelWithErrors, tabsOfLayout } from './sectionTabs'
import type { SectionError } from './sectionTabs'

const schema: SettingsSchema = {
  type: 'object',
  properties: {
    verifyEnabled: { type: 'boolean', title: 'Verify enabled' },
    rebuildSchedule: {
      type: 'object',
      title: 'Rebuild schedule',
      properties: { everyDays: { type: 'integer', title: 'Every days' } }
    },
    retention: {
      type: 'object',
      title: 'Retention',
      properties: { days: { type: 'integer', title: 'Days' } }
    }
  }
}

const tabs = tabsOfLayout(entryLayout(schema, { exclude: [] }), schema)

describe('the tabs of a settings layout', () => {
  it('draws General first for the loose fields, then one tab per section named after its title', () => {
    expect(tabs.map(tab => [tab.key, tab.label])).toEqual([
      ['general', 'General'],
      ['rebuild-schedule', 'Rebuild schedule'],
      ['retention', 'Retention']
    ])
  })

  it('draws no General tab when every field is a section', () => {
    const sectionsOnly: SettingsSchema = { type: 'object', properties: { retention: schema.properties?.retention ?? {} } }

    expect(tabsOfLayout(entryLayout(sectionsOnly, { exclude: [] }), sectionsOnly).map(tab => tab.key)).toEqual(['retention'])
  })

  it('finds the tab of an address, and falls back to the first one', () => {
    expect(tabs.find(tab => tab.key === 'retention')?.label).toBe('Retention')
    expect(tabs[0].key).toBe('general')
  })
})

describe('the errors of each tab', () => {
  it('counts an error of a nested field in the tab of its section', () => {
    const errors: SectionError[] = [{ instancePath: '/rebuildSchedule/everyDays', keyword: 'type' }]

    expect(errorCountsOf(tabs, errors)).toEqual({ general: 0, 'rebuild-schedule': 1, retention: 0 })
  })

  it('counts a missing field at the top level in General', () => {
    const errors: SectionError[] = [{ instancePath: '', keyword: 'required', params: { missingProperty: 'verifyEnabled' } }]

    expect(errorCountsOf(tabs, errors)).toEqual({ general: 1, 'rebuild-schedule': 0, retention: 0 })
  })

  it('counts a missing field of a section in that section', () => {
    const errors: SectionError[] = [{ instancePath: '/retention', keyword: 'required', params: { missingProperty: 'days' } }]

    expect(errorCountsOf(tabs, errors)).toEqual({ general: 0, 'rebuild-schedule': 0, retention: 1 })
  })

  it('finds the first tab, in tab order, that holds an error', () => {
    expect(firstErrorTab(tabs, { general: 0, 'rebuild-schedule': 2, retention: 1 })).toBe('rebuild-schedule')
    expect(firstErrorTab(tabs, { general: 0, 'rebuild-schedule': 0, retention: 0 })).toBeUndefined()
  })

  it('words the label with the count of errors', () => {
    expect(labelWithErrors('Rebuild schedule', 0)).toBe('Rebuild schedule')
    expect(labelWithErrors('Rebuild schedule', 1)).toBe('Rebuild schedule (1 error)')
    expect(labelWithErrors('Rebuild schedule', 3)).toBe('Rebuild schedule (3 errors)')
  })
})
