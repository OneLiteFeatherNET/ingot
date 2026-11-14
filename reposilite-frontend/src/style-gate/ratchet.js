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

// The ratchet compares what the scan finds with what the baseline records. The comparison is exact in both directions:
// more occurrences than recorded is a new violation, and fewer is a fix that left its entry behind. An allowlist entry
// removes its occurrences from the comparison, and an entry that matches nothing is dead.

const keyOf = (file, match) => `${file}\u0000${match}`

const labelOf = (file, match) => `${file}: "${match}"`

// occurrences: [{ file, match }], one per match. baseline: [{ file, match, count }]. allowlist: [{ file, match, reason }].
export function checkRatchet({ occurrences, baseline, allowlist }) {
  const allowed = new Set(allowlist.map((entry) => keyOf(entry.file, entry.match)))
  const found = new Map()
  const rawFound = new Map()

  for (const { file, match } of occurrences) {
    const key = keyOf(file, match)
    rawFound.set(key, (rawFound.get(key) ?? 0) + 1)
    if (!allowed.has(key)) {
      found.set(key, (found.get(key) ?? 0) + 1)
    }
  }

  const recorded = new Map(baseline.map((entry) => [keyOf(entry.file, entry.match), entry.count]))
  const keys = new Set([...found.keys(), ...recorded.keys()])
  const problems = []

  for (const key of keys) {
    const [file, match] = key.split('\u0000')
    const count = found.get(key) ?? 0
    const expected = recorded.get(key) ?? 0

    if (count > expected) {
      problems.push({
        kind: 'unexpected',
        file,
        match,
        found: count,
        recorded: expected,
        message: `new violation in ${labelOf(file, match)}: it occurs ${count} times and the baseline records ${expected}`
      })
    } else if (count < expected) {
      problems.push({
        kind: 'stale',
        file,
        match,
        found: count,
        recorded: expected,
        message: `fixed but not removed in ${labelOf(file, match)}: it occurs ${count} times and the baseline records ${expected}, so remove or lower the entry in the same change`
      })
    }
  }

  for (const entry of allowlist) {
    if (!rawFound.has(keyOf(entry.file, entry.match))) {
      problems.push({
        kind: 'dead',
        file: entry.file,
        match: entry.match,
        found: 0,
        recorded: 0,
        message: `dead allowlist entry for ${labelOf(entry.file, entry.match)}: it matches no occurrence, so remove it`
      })
    }
  }

  return problems.sort((left, right) => keyOf(left.file, left.match).localeCompare(keyOf(right.file, right.match)))
}

const isText = (value) => typeof value === 'string' && value.length > 0

// Parses a baseline or an allowlist file. The shape is checked here so that a malformed file fails the test by name.
// A baseline entry carries a positive count and is unique by file and match. An allowlist entry carries a reason
// string, which the gate test requires to be non-empty.
export function parseList(text, kind) {
  const entries = JSON.parse(text)

  if (!Array.isArray(entries)) {
    throw new Error(`the ${kind} file must hold a JSON array`)
  }

  const seen = new Set()
  for (const entry of entries) {
    if (!isText(entry?.file) || !isText(entry?.match)) {
      throw new Error(`every ${kind} entry needs a file and a match: ${JSON.stringify(entry)}`)
    }

    if (kind === 'baseline' && !(Number.isInteger(entry.count) && entry.count > 0)) {
      throw new Error(`a baseline entry needs a positive integer count: ${JSON.stringify(entry)}`)
    }

    if (kind === 'allowlist' && typeof entry.reason !== 'string') {
      throw new Error(`an allowlist entry needs a reason: ${JSON.stringify(entry)}`)
    }

    const key = keyOf(entry.file, entry.match)
    if (seen.has(key)) {
      throw new Error(`the ${kind} lists ${labelOf(entry.file, entry.match)} twice`)
    }
    seen.add(key)
  }

  return entries
}
