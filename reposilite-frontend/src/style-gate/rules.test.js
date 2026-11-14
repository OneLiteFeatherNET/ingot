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
import { RULES } from './rules.js'

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

  it('gives each rule a scope and an allowlist file named after its id', () => {
    for (const rule of RULES) {
      expect(rule.scope, `the scope of ${rule.id}`).toBeInstanceOf(Array)
      expect(rule.scope.length, `the scope of ${rule.id} is not empty`).toBeGreaterThan(0)
      expect(rule.allowlist, `the allowlist file of ${rule.id}`).toBe(`${rule.id}.json`)
    }
  })
})
