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

// @vitest-environment node

import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/*
 * The settings page carries no style block: its two rules are utilities of the stylesheet. Vitest does not process CSS,
 * so the test reads the sources.
 */
const read = (path: string) => readFileSync(fileURLToPath(new URL(path, import.meta.url)), 'utf8')
const component = read('./SettingsView.vue')
const stylesheet = read('../../style.css')

// The body of the @utility block of the given name.
const utilityBody = (name: string) => {
  const start = stylesheet.indexOf(`@utility ${name} {`)
  return start === -1 ? '' : stylesheet.slice(start, stylesheet.indexOf('\n}', start) + 2)
}

describe('SettingsView styles', () => {
  it('has no style block', () => {
    expect(component, 'the component source').not.toMatch(/<style[\s>]/)
  })

  it('draws read-only inputs as fields that cannot be typed in, through a utility on the page', () => {
    const utility = utilityBody('md-readonly-inputs')
    expect(utility, 'a @utility md-readonly-inputs block').not.toBe('')
    expect(utility).toContain(':read-only')
    expect(utility).toContain('bg-surface-container')
    expect(utility).toContain('text-on-surface-variant')
    expect(component, 'the page uses the utility').toContain('md-readonly-inputs')
  })

  it('keeps the last card clear of the unsaved-changes bar, and on phones of the bottom navigation too', () => {
    const utility = utilityBody('md-bar-clearance')
    expect(utility, 'a @utility md-bar-clearance block').not.toBe('')
    expect(utility).toContain('padding-bottom: calc(1rem + var(--settings-clearance, 0px))')
    expect(utility).toContain('@media (max-width: 599px)')
    expect(utility).toContain('var(--settings-nav, 0px)')
    expect(utility).toContain('env(safe-area-inset-bottom, 0px)')
    expect(component, 'the page uses the utility').toContain('md-bar-clearance')
  })

  it('keeps the settings-view hook class that the tests and the anchors read', () => {
    expect(component).toContain('settings-view')
  })
})
