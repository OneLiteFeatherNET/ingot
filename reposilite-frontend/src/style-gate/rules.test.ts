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
import { RULES } from './rules'

const IDS = [
  'style-block',
  'arbitrary-value',
  'raw-colour',
  'non-md3-default',
  'inline-style',
  'class-token',
  'class-selector',
  'inline-svg'
]

describe('the rule table', () => {
  it('lists the eight rules of the design in their order', () => {
    expect(RULES.map((rule) => rule.id), 'the rule ids in order').toEqual(IDS)
  })

  it('accepts the Vue transition classes of MdTransition in the md3 stylesheets, and only there', () => {
    const classSelector = RULES.find((rule) => rule.id === 'class-selector')!
    const findingsOf = (file: string, text: string) => classSelector.match({ file, text } as never)

    expect(findingsOf('src/styles/md3/motion-transitions.css', '.motion-menu-enter-active { opacity: 1; }'), 'a transition class in an md3 sheet').toEqual([])
    expect(findingsOf('src/styles/md3/motion-transitions.css', '.motion-shared-x-back-leave-to { opacity: 0; }')).toEqual([])
    expect(findingsOf('src/styles/md3/motion-transitions.css', '.motion-menu-open { opacity: 1; }'), 'a motion class that is not a transition hook').toEqual(['.motion-menu-open'])
    expect(findingsOf('src/components/card/Other.css', '.motion-menu-enter-active { opacity: 1; }'), 'a transition class outside the documented sheets').toEqual(['.motion-menu-enter-active'])
  })

  it('accepts the scrim transition classes of the dialog pattern, and no scrim of another pattern', () => {
    const classSelector = RULES.find((rule) => rule.id === 'class-selector')!
    const findingsOf = (file: string, text: string) => classSelector.match({ file, text } as never)

    expect(findingsOf('src/styles/md3/motion-transitions.css', '.motion-dialog-scrim-enter-active { opacity: 1; }'), 'the scrim of a dialog').toEqual([])
    expect(findingsOf('src/styles/md3/motion-transitions.css', '.motion-dialog-scrim-leave-to { opacity: 0; }'), 'the scrim of a dialog').toEqual([])
    expect(findingsOf('src/styles/md3/motion-transitions.css', '.motion-menu-scrim-enter-active { opacity: 1; }'), 'a menu has no scrim').toEqual(['.motion-menu-scrim-enter-active'])
  })

  it('gives each rule a scope and an allowlist file named after its id', () => {
    for (const rule of RULES) {
      expect(rule.scope, `the scope of ${rule.id}`).toBeInstanceOf(Array)
      expect(rule.scope.length, `the scope of ${rule.id} is not empty`).toBeGreaterThan(0)
      expect(rule.allowlist, `the allowlist file of ${rule.id}`).toBe(`${rule.id}.json`)
    }
  })
})
