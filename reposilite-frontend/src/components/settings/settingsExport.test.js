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
import { settingsFileName, settingsByDomain, serializeSettings } from './settingsExport'

describe('settingsFileName', () => {
  it('names the file after the local calendar day of the injected clock', () => {
    expect(settingsFileName(new Date(2026, 9, 15, 23, 59))).toBe('ingot-settings-2026-10-15.json')
  })

  it('pads the month and the day to two digits', () => {
    expect(settingsFileName(new Date(2026, 0, 3, 8, 0))).toBe('ingot-settings-2026-01-03.json')
  })
})

describe('settingsByDomain', () => {
  it('keeps only the listed domains, in the order they are listed', () => {
    const source = { frontend: { title: 'Ingot' }, maven: { repositories: [] }, stray: {} }

    expect(settingsByDomain(['maven', 'frontend'], source)).toEqual({
      maven: { repositories: [] },
      frontend: { title: 'Ingot' }
    })
  })

  it('does not share objects with the source, so later edits cannot change the export', () => {
    const source = { maven: { repositories: [{ id: 'releases' }] } }

    const exported = settingsByDomain(['maven'], source)
    source.maven.repositories[0].id = 'changed'

    expect(exported.maven.repositories[0].id).toBe('releases')
  })
})

describe('serializeSettings', () => {
  it('writes the settings as JSON that parses back to the same values', () => {
    const settings = { maven: { repositories: [{ id: 'releases', redeployment: false }] } }

    expect(JSON.parse(serializeSettings(settings))).toEqual(settings)
  })
})
