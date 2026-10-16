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

// A sign in that is answered with 429 carries a Retry-After header: the seconds until the
// server accepts another attempt. These helpers read it and word it for the sign in dialog.

const headerOf = (headers, name) =>
  headers?.get?.(name) ?? headers?.[name] ?? headers?.['Retry-After'] ?? null

/** The seconds a failed request must wait, or null when the response does not say. */
export const retryAfterSeconds = (error) => {
  const value = String(headerOf(error?.response?.headers, 'retry-after') ?? '').trim()
  return /^\d+$/.test(value) ? Number(value) : null
}

/** The instant (milliseconds) at which another attempt is allowed, or null when it is unknown. */
export const rateLimitedUntil = (error, nowMs) => {
  if (error?.response?.status !== 429) return null
  const seconds = retryAfterSeconds(error)
  return seconds === null ? null : nowMs + seconds * 1000
}

/** "Too many attempts, try again in N minutes", rounded up to whole minutes and never under one. */
export const rateLimitMessage = (seconds) => {
  if (seconds === null || seconds === undefined) return 'Too many attempts, try again later'
  const minutes = Math.max(1, Math.ceil(seconds / 60))
  return `Too many attempts, try again in ${minutes} ${minutes === 1 ? 'minute' : 'minutes'}`
}
