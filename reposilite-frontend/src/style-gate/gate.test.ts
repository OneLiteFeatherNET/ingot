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
import { RULES, type Rule } from './rules'
import { collectSources } from './sources'
import { scanSources, type Resolver } from './scan'
import { createResolver } from './tailwind'
import { checkRatchet, parseList, type Problem } from './ratchet'

// The repository test. It reads the committed sources, baselines and allowlists under the frontend directory, where
// vitest runs, and never writes. Every test rebuilds its inputs from disk, so no state carries from one test to another.
const FRONTEND = process.cwd()
const SRC = join(FRONTEND, 'src')
const GATE = join(SRC, 'style-gate')

const readGateFile = (folder: string, ruleId: string): string => readFileSync(join(GATE, folder, `${ruleId}.json`), 'utf8')

// The sources and the compiled stylesheet are read once per file and shared by every rule. The cache is read-only and
// holds what is on disk, so a test gets the same answer whatever order the tests run in.
let sourcesOnce: ReturnType<typeof collectSources> | undefined
let resolverOnce: Promise<Resolver> | undefined
const sourcesOf = () => (sourcesOnce ??= collectSources(SRC))
const resolverOf = () => (resolverOnce ??= createResolver(SRC))

const scanOnce = new Map<string, Promise<Problem[]>>()

// The three tests of a rule ask the same question, so the scan of a rule runs once per file, not once per test.
const problemsOf = (ruleId: string): Promise<Problem[]> => {
  let problems = scanOnce.get(ruleId)
  if (problems === undefined) {
    problems = computeProblems(ruleId)
    scanOnce.set(ruleId, problems)
  }
  return problems
}

const computeProblems = async (ruleId: string): Promise<Problem[]> => {
  const rule = RULES.find((candidate) => candidate.id === ruleId)!
  const occurrences = scanSources(rule, sourcesOf(), await resolverOf())
  const baseline = parseList(readGateFile('baseline', ruleId), 'baseline')
  const allowlist = parseList(readGateFile('allowlist', ruleId), 'allowlist')

  return checkRatchet({ occurrences, baseline, allowlist })
}

const messagesOf = (problems: Problem[], kind: Problem['kind'], rule: Rule): string[] =>
  problems.filter((problem) => problem.kind === kind).map((problem) => `${problem.message}. ${rule.hint}`)

// The first test of a rule runs its scan: compiling the stylesheet and reading the whole source tree. That is about a
// second on an idle machine and several times longer under load, so the default test timeout is too short for it.
// The timeout is set for these suites only, and the global timeout stays as it is.
const FULL_TREE_SCAN_TIMEOUT_MS = 30_000

for (const rule of RULES) {
  describe(`style gate rule ${rule.number} (${rule.id})`, { timeout: FULL_TREE_SCAN_TIMEOUT_MS }, () => {
    it('has no violation that the baseline does not record', async () => {
      const problems = await problemsOf(rule.id)

      expect(messagesOf(problems, 'unexpected', rule), 'new violations: fix them, or record a documented exception in the allowlist').toEqual([])
    })

    it('has no baseline entry for a violation that is gone', async () => {
      const problems = await problemsOf(rule.id)

      expect(messagesOf(problems, 'stale', rule), 'fixed violations: remove or lower their baseline entries in the same change').toEqual([])
    })

    it('has no allowlist entry that matches nothing', async () => {
      const problems = await problemsOf(rule.id)

      expect(messagesOf(problems, 'dead', rule), 'dead allowlist entries: remove them').toEqual([])
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
