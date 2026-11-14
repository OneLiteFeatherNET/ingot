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
import { checkRatchet } from './ratchet.js'

// Occurrences are plain { file, match } pairs, one per matched token in the source. Each test builds its own inputs.
const occurrences = (file, match, count) => Array.from({ length: count }, () => ({ file, match }))

const FILE = 'src/components/Fixture.vue'

describe('ratchet between the scan and the baseline', () => {
  it('reports a key that the baseline does not record as unexpected', () => {
    const problems = checkRatchet({ occurrences: occurrences(FILE, '<style', 1), baseline: [], allowlist: [] })

    expect(problems.map((problem) => problem.kind), 'the kinds of problems').toEqual(['unexpected'])
    expect(problems[0].message, 'the message names the file and the token').toContain(FILE)
    expect(problems[0].message, 'the message names the token').toContain('<style')
  })

  it('reports a key whose count exceeds the baseline as unexpected', () => {
    const baseline = [{ file: FILE, match: '<style', count: 1 }]
    const problems = checkRatchet({ occurrences: occurrences(FILE, '<style', 2), baseline, allowlist: [] })

    expect(problems.map((problem) => problem.kind), 'a second occurrence is new').toEqual(['unexpected'])
  })

  it('reports a key whose count fell below the baseline as stale', () => {
    const baseline = [{ file: FILE, match: '<style', count: 2 }]
    const problems = checkRatchet({ occurrences: occurrences(FILE, '<style', 1), baseline, allowlist: [] })

    expect(problems.map((problem) => problem.kind), 'a fixed occurrence leaves a stale entry').toEqual(['stale'])
    expect(problems[0].message, 'the message asks for the entry to be lowered or removed').toMatch(/remove or lower/)
  })

  it('reports an allowlist entry that matches no occurrence as dead', () => {
    const allowlist = [{ file: FILE, match: 'min-[600px]:flex', reason: 'a fixture' }]
    const problems = checkRatchet({ occurrences: [], baseline: [], allowlist })

    expect(problems.map((problem) => problem.kind), 'an entry without an occurrence is dead').toEqual(['dead'])
  })

  it('reports nothing when the occurrences equal the baseline exactly', () => {
    const baseline = [{ file: FILE, match: '<style', count: 2 }]

    expect(checkRatchet({ occurrences: occurrences(FILE, '<style', 2), baseline, allowlist: [] }), 'an exact match').toEqual([])
  })

  it('subtracts an allowlisted occurrence before comparing with the baseline', () => {
    const match = 'min-[600px]:flex'
    const allowlist = [{ file: FILE, match, reason: 'a fixture' }]
    const problems = checkRatchet({ occurrences: occurrences(FILE, match, 3), baseline: [], allowlist })

    expect(problems, 'the allowlisted occurrences are not counted').toEqual([])
  })
})
