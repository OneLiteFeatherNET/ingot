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

import { describe, it, expect, vi } from 'vitest'
import { createPoller, FIRST_INTERVAL_MS, MAX_INTERVAL_MS } from './poller'

// Lets every pending promise callback of the fake fetch run before the test looks again.
const settle = async () => {
  for (let turn = 0; turn < 20; turn++) await Promise.resolve()
}

// Timers that move only when the test advances them. A new one is built for every test.
const manualTimers = () => {
  let now = 0
  let nextId = 1
  const pending = new Map()
  return {
    setTimeout: (callback, ms) => {
      const id = nextId++
      pending.set(id, { at: now + ms, callback })
      return id
    },
    clearTimeout: (id) => pending.delete(id),
    delays: () => [...pending.values()].map(entry => entry.at - now),
    async advance(ms) {
      const target = now + ms
      for (;;) {
        const due = [...pending.entries()]
          .filter(([, entry]) => entry.at <= target)
          .sort(([, a], [, b]) => a.at - b.at)[0]
        if (!due) break
        pending.delete(due[0])
        now = due[1].at
        due[1].callback()
        await settle()
      }
      now = target
    },
  }
}

// A tab that the test can hide and show. Listeners are kept per test.
const visibilitySource = () => {
  let hidden = false
  const listeners = new Set()
  return {
    isHidden: () => hidden,
    subscribe: (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    setHidden: (value) => {
      hidden = value
      listeners.forEach(listener => listener())
    },
  }
}

const job = (overrides = {}) => ({ id: 7, kind: 'IMPORT', status: 'RUNNING', results: {}, ...overrides })

const problem = (status) => Object.assign(new Error(`status ${status}`), { status })

// A fetch whose answers are queued by the test. The last answer repeats when the queue runs out.
const scriptedFetch = (answers) => {
  const queue = [...answers]
  return vi.fn(async () => {
    const next = queue.length > 1 ? queue.shift() : queue[0]
    if (next instanceof Error) throw next
    return next
  })
}

const harness = (answers) => {
  const timers = manualTimers()
  const visibility = visibilitySource()
  const fetch = scriptedFetch(answers)
  const handlers = {
    onUpdate: vi.fn(),
    onFinished: vi.fn(),
    onError: vi.fn(),
    onUnauthorized: vi.fn(),
    onStopped: vi.fn(),
  }
  const poller = createPoller({ fetch, timers, visibility, isFinished: (value) => value.status === 'COMPLETED', ...handlers })
  return { poller, timers, visibility, fetch, ...handlers }
}

describe('the job poller', () => {
  it('reads the job once at once when it starts', async () => {
    const { poller, fetch } = harness([job()])

    poller.start()
    await settle()

    expect(fetch, 'the poller reads the job as soon as it starts').toHaveBeenCalledTimes(1)
  })

  it('waits two seconds before the next read after a changed answer', async () => {
    const { poller, timers } = harness([job({ results: { COPIED: 1 } })])

    poller.start()
    await settle()

    expect(timers.delays(), 'the first wait is the first interval').toEqual([FIRST_INTERVAL_MS])
    expect(FIRST_INTERVAL_MS).toBe(2000)
  })

  it('grows the wait by one and a half while the answer stays the same', async () => {
    const { poller, timers, fetch } = harness([job()])

    poller.start()
    await settle()
    await timers.advance(2000)
    await timers.advance(3000)

    expect(fetch, 'three reads, each after the wait before it').toHaveBeenCalledTimes(3)
    expect(timers.delays(), 'the next wait is 4.5 seconds').toEqual([4500])
  })

  it('caps the wait at fifteen seconds while the answer stays the same', async () => {
    const { poller, timers } = harness([job()])

    poller.start()
    await settle()
    for (let step = 0; step < 10; step++) await timers.advance(timers.delays()[0])

    expect(MAX_INTERVAL_MS).toBe(15000)
    expect(timers.delays(), 'the wait never exceeds the cap').toEqual([15000])
  })

  it('returns to two seconds as soon as the answer changes', async () => {
    const { poller, timers } = harness([job(), job(), job(), job({ results: { COPIED: 3 } })])

    poller.start()
    await settle()
    await timers.advance(timers.delays()[0])
    await timers.advance(timers.delays()[0])
    await timers.advance(timers.delays()[0])

    expect(timers.delays(), 'a change resets the wait to the first interval').toEqual([FIRST_INTERVAL_MS])
  })

  it('reports every answer to the update handler', async () => {
    const { poller, onUpdate } = harness([job({ results: { COPIED: 1 } })])

    poller.start()
    await settle()

    expect(onUpdate).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }))
  })

  it('stops once the job is finished and reports it', async () => {
    const finished = job({ status: 'COMPLETED' })
    const { poller, timers, onFinished, fetch } = harness([job(), finished])

    poller.start()
    await settle()
    await timers.advance(timers.delays()[0])

    expect(onFinished, 'the finished job is reported once').toHaveBeenCalledWith(expect.objectContaining({ status: 'COMPLETED' }))
    expect(timers.delays(), 'no further read is scheduled').toEqual([])
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('pauses while the tab is hidden and reads once at once when it is shown again', async () => {
    const { poller, timers, visibility, fetch } = harness([job()])

    poller.start()
    await settle()
    visibility.setHidden(true)
    await timers.advance(60000)

    expect(fetch, 'no read happens while the tab is hidden').toHaveBeenCalledTimes(1)

    visibility.setHidden(false)
    await settle()

    expect(fetch, 'one read at once when the tab is visible again').toHaveBeenCalledTimes(2)
  })

  it('keeps retrying at the longest interval after a server error and says so', async () => {
    const { poller, timers, onError, fetch } = harness([problem(503), job()])

    poller.start()
    await settle()

    expect(onError).toHaveBeenCalledWith(expect.objectContaining({ status: 503 }))
    expect(timers.delays(), 'a failed read waits the longest interval').toEqual([MAX_INTERVAL_MS])

    await timers.advance(MAX_INTERVAL_MS)
    expect(fetch, 'polling continues after the failure').toHaveBeenCalledTimes(2)
  })

  it('keeps retrying at the longest interval after a lost connection', async () => {
    const { poller, timers, onError } = harness([problem(0)])

    poller.start()
    await settle()

    expect(onError).toHaveBeenCalledTimes(1)
    expect(timers.delays()).toEqual([MAX_INTERVAL_MS])
  })

  it('stops on 403 and says why', async () => {
    const { poller, timers, onStopped } = harness([problem(403)])

    poller.start()
    await settle()

    expect(onStopped).toHaveBeenCalledWith(expect.objectContaining({ status: 403 }))
    expect(timers.delays(), 'no read is scheduled after a refusal').toEqual([])
  })

  it('stops on 404 and says why', async () => {
    const { poller, timers, onStopped } = harness([problem(404)])

    poller.start()
    await settle()

    expect(onStopped).toHaveBeenCalledWith(expect.objectContaining({ status: 404 }))
    expect(timers.delays()).toEqual([])
  })

  it('hands 401 to the sign in hook and stops', async () => {
    const { poller, timers, onUnauthorized, onStopped } = harness([problem(401)])

    poller.start()
    await settle()

    expect(onUnauthorized, 'the sign in flow is started once').toHaveBeenCalledTimes(1)
    expect(onStopped, 'a 401 is not reported as a stop').not.toHaveBeenCalled()
    expect(timers.delays()).toEqual([])
  })

  it('cancels the pending read when it is stopped', async () => {
    const { poller, timers, fetch } = harness([job()])

    poller.start()
    await settle()
    poller.stop()
    await timers.advance(60000)

    expect(fetch, 'no read happens after stop').toHaveBeenCalledTimes(1)
    expect(timers.delays()).toEqual([])
  })
})
