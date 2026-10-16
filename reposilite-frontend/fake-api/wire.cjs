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
 * The fake keeps times as ISO-8601 strings and converts them where it answers, to the shapes the
 * real backend sends (ApiTimeWireFormatTest pins them; frontend/src/helpers/apiTime.js reads them):
 *
 *   Instant    -> a number of Unix epoch seconds, as Jackson writes java.time.Instant
 *   LocalDate  -> an array [year, month, day]
 *
 * Endpoints that format their own times (audit, cleanup runs, failures, storage usage) keep their
 * ISO strings, as the backend does, and do not pass through here.
 */

/** An Instant on the wire, or null when there is none. */
const instantOnWire = (iso) => (iso === null || iso === undefined ? null : Date.parse(iso) / 1000)

/** A LocalDate on the wire: the day of an ISO date or timestamp as [year, month, day]. */
const localDateOnWire = (iso) => {
  const [year, month, day] = String(iso).slice(0, 10).split("-").map(Number)
  return [year, month, day]
}

module.exports = { instantOnWire, localDateOnWire }
