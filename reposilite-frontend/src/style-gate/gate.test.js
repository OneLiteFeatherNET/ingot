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
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { RULES } from './rules.js'
import { collectSources } from './sources.js'
import { scanSources } from './scan.js'
import { createResolver } from './tailwind.js'
import { checkRatchet, parseList } from './ratchet.js'

// The repository test. It reads the committed sources, baselines and allowlists under the frontend directory, where
// vitest runs, and never writes. Every test rebuilds its inputs from disk, so no state carries from one test to another.
const FRONTEND = process.cwd()
const SRC = join(FRONTEND, 'src')
const GATE = join(SRC, 'style-gate')

const readGateFile = (folder, ruleId) => readFileSync(join(GATE, folder, `${ruleId}.json`), 'utf8')

const problemsOf = async (ruleId) => {
  const rule = RULES.find((candidate) => candidate.id === ruleId)
  const resolver = await createResolver(SRC)
  const occurrences = scanSources(rule, collectSources(SRC), resolver)
  const baseline = parseList(readGateFile('baseline', ruleId), 'baseline')
  const allowlist = parseList(readGateFile('allowlist', ruleId), 'allowlist')

  return checkRatchet({ occurrences, baseline, allowlist })
}

const messagesOf = (problems, kind) => problems.filter((problem) => problem.kind === kind).map((problem) => problem.message)

for (const rule of RULES) {
  describe(`style gate rule ${rule.number} (${rule.id})`, () => {
    it('has no violation that the baseline does not record', async () => {
      const problems = await problemsOf(rule.id)

      expect(messagesOf(problems, 'unexpected'), 'new violations: fix them, or record a documented exception in the allowlist').toEqual([])
    })

    it('has no baseline entry for a violation that is gone', async () => {
      const problems = await problemsOf(rule.id)

      expect(messagesOf(problems, 'stale'), 'fixed violations: remove or lower their baseline entries in the same change').toEqual([])
    })

    it('has no allowlist entry that matches nothing', async () => {
      const problems = await problemsOf(rule.id)

      expect(messagesOf(problems, 'dead'), 'dead allowlist entries: remove them').toEqual([])
    })
  })
}

describe('baseline and allowlist files of the gate', () => {
  for (const rule of RULES) {
    it(`parses the baseline of ${rule.id}`, () => {
      expect(() => parseList(readGateFile('baseline', rule.id), 'baseline'), `the baseline file of ${rule.id}`).not.toThrow()
    })

    it(`gives every allowlist entry of ${rule.id} a non-empty reason`, () => {
      const entries = parseList(readGateFile('allowlist', rule.id), 'allowlist')
      const withoutReason = entries.filter((entry) => entry.reason.trim() === '').map((entry) => `${entry.file} ${entry.match}`)

      expect(withoutReason, `the allowlist entries of ${rule.id} without a reason`).toEqual([])
    })
  }
})
