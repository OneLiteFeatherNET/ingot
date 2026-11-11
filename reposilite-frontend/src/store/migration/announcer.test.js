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
import { createAnnouncer, ANNOUNCE_INTERVAL_MS } from './announcer'

// Times are given by the test, never read from the system clock.
const at = (seconds) => seconds * 1000

describe('the progress announcer', () => {
  it('announces the first state it is offered', () => {
    const announcer = createAnnouncer()

    expect(announcer.offer({ state: 'RUNNING', percent: 0, now: at(0) })).toBe('Import running, 0 percent.')
  })

  it('announces at most once in ten seconds while the percentage moves by one point a second', () => {
    const announcer = createAnnouncer()
    const announced = []
    for (let second = 0; second <= 30; second++) {
      const text = announcer.offer({ state: 'RUNNING', percent: second, now: at(second) })
      if (text) announced.push(second)
    }

    expect(ANNOUNCE_INTERVAL_MS).toBe(10000)
    expect(announced, 'the first offer, then one every ten points or ten seconds, whichever comes later').toEqual([0, 10, 20, 30])
  })

  it('does not announce a move of less than ten points', () => {
    const announcer = createAnnouncer()
    announcer.offer({ state: 'RUNNING', percent: 0, now: at(0) })

    expect(announcer.offer({ state: 'RUNNING', percent: 9, now: at(60) })).toBeNull()
  })

  it('announces a move of ten points or more once the interval has passed', () => {
    const announcer = createAnnouncer()
    announcer.offer({ state: 'RUNNING', percent: 0, now: at(0) })

    expect(announcer.offer({ state: 'RUNNING', percent: 10, now: at(10) })).toBe('Import running, 10 percent.')
  })

  it('announces a change of state at once when the interval has passed', () => {
    const announcer = createAnnouncer()
    announcer.offer({ state: 'RUNNING', percent: 40, now: at(0) })

    expect(announcer.offer({ state: 'PAUSED', percent: 40, now: at(12) })).toBe('Import paused, 40 percent.')
  })

  it('holds a change of state that comes inside the interval and announces it when the interval has passed', () => {
    const announcer = createAnnouncer()
    announcer.offer({ state: 'RUNNING', percent: 40, now: at(0) })

    expect(announcer.offer({ state: 'PAUSED', percent: 40, now: at(4) }), 'inside the interval').toBeNull()
    expect(announcer.offer({ state: 'PAUSED', percent: 40, now: at(11) }), 'after the interval').toBe('Import paused, 40 percent.')
  })

  it('announces no percentage while the total is unknown', () => {
    const announcer = createAnnouncer()

    expect(announcer.offer({ state: 'RUNNING', percent: null, now: at(0) })).toBe('Import running.')
  })
})
