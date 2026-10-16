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
 * The export of the settings as a file. The clock is passed in, so the file name is a function of
 * the day it is asked for and a test can fix that day.
 */

export const settingsClockKey = Symbol('settings-clock')

export const systemClock = () => new Date()

const pad = (value) => String(value).padStart(2, '0')

/* ingot-settings-<yyyy-mm-dd>.json, for the local calendar day of `date`. */
export const settingsFileName = (date) => {
  const day = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  return `ingot-settings-${day}.json`
}

/* The values of the listed domains, copied, in the order they are listed. Anything else in `source` is left out. */
export const settingsByDomain = (domains, source) => Object.fromEntries(
  domains.map(domain => [domain, JSON.parse(JSON.stringify(source[domain] ?? {}))])
)

export const serializeSettings = (settings) => `${JSON.stringify(settings, null, 2)}\n`
