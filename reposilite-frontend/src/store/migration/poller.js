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
 * Reads a job until it is finished. The wait starts at two seconds and grows by one and a half while
 * the answer stays the same, up to fifteen seconds. A changed answer brings it back to two seconds.
 * A failed read (a server error or a lost connection) waits the longest interval and tries again.
 * A refusal (403, 404 or any other client error) stops the poller, and a 401 is handed to the sign in
 * flow and stops it too.
 *
 * Nothing here reads a clock or the page. The timers, the fetch and the tab visibility are passed in,
 * so the tests drive the schedule without waiting. While the tab is hidden no read is scheduled, and
 * one read happens at once when it is shown again.
 */

const FIRST_INTERVAL_MS = 2000
const MAX_INTERVAL_MS = 15000
const GROWTH = 1.5

const sameAnswer = (a, b) => JSON.stringify(a) === JSON.stringify(b)

const isRefusal = (status) => status >= 400 && status < 500

/*
 * options:
 * - fetch()            reads the job, resolves to its answer or rejects with an error that has a `status`
 *                      (0 when no answer came at all)
 * - timers             { setTimeout(callback, ms), clearTimeout(handle) }
 * - visibility         { isHidden(), subscribe(listener) => unsubscribe }
 * - isFinished(answer) true when the job will not change any more
 * - onUpdate, onFinished, onError, onUnauthorized, onStopped: the outcome of each read
 */
const createPoller = ({
  fetch,
  timers,
  visibility,
  isFinished = () => false,
  same = sameAnswer,
  onUpdate = () => {},
  onFinished = () => {},
  onError = () => {},
  onUnauthorized = () => {},
  onStopped = () => {},
}) => {
  let running = false
  let inFlight = false
  let interval = FIRST_INTERVAL_MS
  let previous
  let handle = null
  let unsubscribe = null

  const cancelWait = () => {
    if (handle !== null) {
      timers.clearTimeout(handle)
      handle = null
    }
  }

  const finish = () => {
    running = false
    cancelWait()
    if (unsubscribe) unsubscribe()
    unsubscribe = null
  }

  const waitThenRead = () => {
    cancelWait()
    if (!running || visibility.isHidden()) return
    handle = timers.setTimeout(read, interval)
  }

  async function read() {
    handle = null
    if (!running || inFlight) return
    inFlight = true
    let answer
    try {
      answer = await fetch()
    } catch (error) {
      inFlight = false
      if (!running) return
      const status = error?.status ?? 0
      if (status === 401) {
        finish()
        onUnauthorized(error)
        return
      }
      if (isRefusal(status)) {
        finish()
        onStopped(error)
        return
      }
      onError(error)
      interval = MAX_INTERVAL_MS
      waitThenRead()
      return
    }
    inFlight = false
    if (!running) return
    onUpdate(answer)
    if (isFinished(answer)) {
      finish()
      onFinished(answer)
      return
    }
    const changed = previous === undefined || !same(previous, answer)
    previous = answer
    interval = changed ? FIRST_INTERVAL_MS : Math.min(MAX_INTERVAL_MS, interval * GROWTH)
    waitThenRead()
  }

  return {
    start() {
      if (running) return
      running = true
      interval = FIRST_INTERVAL_MS
      previous = undefined
      unsubscribe = visibility.subscribe(() => {
        if (!running) return
        if (visibility.isHidden()) {
          cancelWait()
        } else if (!inFlight) {
          read()
        }
      })
      read()
    },
    stop() {
      finish()
    },
  }
}

export { createPoller, FIRST_INTERVAL_MS, MAX_INTERVAL_MS }
