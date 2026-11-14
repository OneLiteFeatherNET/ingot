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
 * The time values of the API, read in one place. The backend's Jackson defaults decide the shapes
 * (ReposiliteObjectMapper; ApiTimeWireFormatTest pins them):
 *
 *   java.time.Instant   a number of Unix epoch seconds, with a fraction for sub-second precision
 *   java.time.LocalDate an array [year, month, day]
 *   epoch timestamps    Long values in milliseconds (status snapshots, statistics); used as they are
 *
 * A few endpoints format their own times as ISO-8601 strings (audit, cleanup runs, failures, storage
 * usage). Those are read with new Date(text) where they are shown, not through this module.
 */

/** The moment an Instant from the API names, or null when it was not sent or is epoch zero. */
const instantToDate = (epochSeconds) => {
  if (typeof epochSeconds !== 'number' || !Number.isFinite(epochSeconds) || epochSeconds <= 0) {
    return null
  }
  return new Date(Math.round(epochSeconds * 1000))
}

/** The UTC midnight of a LocalDate from the API, or null when it is not a [year, month, day] array. */
const localDateToDate = (value) => {
  if (!Array.isArray(value) || value.length !== 3 || !value.every(Number.isInteger)) {
    return null
  }
  const [year, month, day] = value
  return new Date(Date.UTC(year, month - 1, day))
}

export {
  instantToDate,
  localDateToDate
}
