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
 * Presentation of a recorded failure. The server sends ISO-8601 instants in UTC. The reference
 * instant of the relative form is an argument, so the output depends on nothing but its inputs.
 */

const RELATIVE_FORMAT = new Intl.RelativeTimeFormat('en', { numeric: 'always' })

// Anything closer than this reads as "now" rather than as a count of seconds.
const JUST_NOW_SECONDS = 5

const UNITS = [
  ['day', 86400],
  ['hour', 3600],
  ['minute', 60],
  ['second', 1]
]

/** "3 minutes ago", counted in the largest unit that fits. */
export const relativeTime = (iso, now) => {
  const seconds = Math.round((Date.parse(iso) - now) / 1000)
  if (Math.abs(seconds) < JUST_NOW_SECONDS) {
    return 'just now'
  }

  const [unit, size] = UNITS.find(([, unitSize], index) =>
    Math.abs(seconds) >= unitSize || index === UNITS.length - 1
  )
  return RELATIVE_FORMAT.format(Math.round(seconds / size), unit)
}

/** "2026-10-15 11:59:30 UTC", the tooltip that gives the exact moment. */
export const absoluteTime = (iso) =>
  iso.replace('T', ' ').replace(/(\.\d+)?Z$/, ' UTC')
