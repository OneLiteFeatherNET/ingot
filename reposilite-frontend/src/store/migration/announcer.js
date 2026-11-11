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

/*
 * Decides what a screen reader is told about a job's progress. A polite live region that changes on every poll
 * floods the reader, so an announcement is made at most once in ten seconds, and only when the state changed or
 * the percentage moved by ten points or more. A change that comes inside the interval is not dropped: it stays
 * pending, and it is announced by the first offer that comes after the interval has passed.
 *
 * `percent` is null while the total is unknown, and then no percentage is spoken. The clock is passed in.
 */

const ANNOUNCE_INTERVAL_MS = 10000
const ANNOUNCE_STEP_POINTS = 10

const wordsOf = (state) => String(state ?? 'unknown').toLowerCase()

const textOf = (state, percent) => (percent === null
  ? `Import ${wordsOf(state)}.`
  : `Import ${wordsOf(state)}, ${percent} percent.`)

const createAnnouncer = ({ intervalMs = ANNOUNCE_INTERVAL_MS, stepPoints = ANNOUNCE_STEP_POINTS } = {}) => {
  // The last text that was spoken. Offers that are held back leave it as it was.
  let spoken = null

  return {
    offer({ state, percent = null, now }) {
      if (spoken === null) {
        spoken = { state, percent, at: now }
        return textOf(state, percent)
      }
      const stateChanged = state !== spoken.state
      const moved = percent !== null && spoken.percent !== null && Math.abs(percent - spoken.percent) >= stepPoints
      if (!stateChanged && !moved) return null
      if (now - spoken.at < intervalMs) return null
      spoken = { state, percent, at: now }
      return textOf(state, percent)
    },
  }
}

export { createAnnouncer, ANNOUNCE_INTERVAL_MS, ANNOUNCE_STEP_POINTS }
