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
import { rateLimitMessage, rateLimitedUntil, retryAfterSeconds } from './rate-limit'

const rejection = (status, headers) => ({ response: { status, headers } })

describe('retryAfterSeconds', () => {
  it('reads the seconds of a Retry-After header', () => {
    expect(retryAfterSeconds(rejection(429, { 'retry-after': '120' }))).toBe(120)
  })

  it('reads the header under its capitalised name too', () => {
    expect(retryAfterSeconds(rejection(429, { 'Retry-After': '30' }))).toBe(30)
  })

  it('reads the header from a headers object with a get method', () => {
    const headers = { get: (name) => (name.toLowerCase() === 'retry-after' ? '45' : null) }

    expect(retryAfterSeconds(rejection(429, headers))).toBe(45)
  })

  it.each([
    ['is missing', undefined],
    ['is empty', { 'retry-after': '' }],
    ['is not a number', { 'retry-after': 'soon' }],
    ['is negative', { 'retry-after': '-5' }],
    ['is a date', { 'retry-after': 'Wed, 21 Oct 2026 07:28:00 GMT' }],
  ])('is null when the header %s', (_, headers) => {
    expect(retryAfterSeconds(rejection(429, headers))).toBeNull()
  })

  it('is null for an error without a response', () => {
    expect(retryAfterSeconds(new Error('Network Error'))).toBeNull()
  })
})

describe('rateLimitedUntil', () => {
  const NOW = Date.parse('2026-10-15T12:00:00Z')

  it('counts the Retry-After seconds from the given time', () => {
    expect(rateLimitedUntil(rejection(429, { 'retry-after': '90' }), NOW)).toBe(NOW + 90000)
  })

  it('is null for a 429 without a Retry-After header', () => {
    expect(rateLimitedUntil(rejection(429, {}), NOW)).toBeNull()
  })

  it('is null for any other status', () => {
    expect(rateLimitedUntil(rejection(401, { 'retry-after': '90' }), NOW)).toBeNull()
  })
})

describe('rateLimitMessage', () => {
  it.each([
    [0, 'Too many attempts, try again in 1 minute'],
    [1, 'Too many attempts, try again in 1 minute'],
    [59, 'Too many attempts, try again in 1 minute'],
    [60, 'Too many attempts, try again in 1 minute'],
    [61, 'Too many attempts, try again in 2 minutes'],
    [120, 'Too many attempts, try again in 2 minutes'],
    [300, 'Too many attempts, try again in 5 minutes'],
  ])('words %s seconds as "%s"', (seconds, message) => {
    expect(rateLimitMessage(seconds)).toBe(message)
  })

  it('asks to try again later when the wait is unknown', () => {
    expect(rateLimitMessage(null)).toBe('Too many attempts, try again later')
  })
})
