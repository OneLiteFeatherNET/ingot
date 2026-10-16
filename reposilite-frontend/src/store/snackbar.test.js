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

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createSnackbarQueue, AUTO_DISMISS_MS } from './snackbar'

// The lifetimes are part of the behaviour under test, so they are written out here rather
// than imported: a change to the store must show up as a failing test.
const ERROR_MS = 10000
const WARNING_MS = 6000

describe('snackbar notices without text', () => {
  it('never shows a notice whose message is empty', () => {
    const queue = createSnackbarQueue()

    queue.enqueue({ message: '', tone: 'info' })

    expect(queue.current.value, 'an empty bar must not be drawn over the page').toBeNull()
    expect(queue.entries.value).toHaveLength(0)
  })

  it('never shows a notice whose message is only whitespace or missing', () => {
    const queue = createSnackbarQueue()

    queue.enqueue({ message: '   ', tone: 'error' })
    queue.enqueue({ tone: 'warning' })

    expect(queue.entries.value, 'blank notices are dropped, not queued').toHaveLength(0)
  })

  it('keeps a real notice that follows an empty one', () => {
    const queue = createSnackbarQueue()

    queue.enqueue({ message: '', tone: 'info' })
    queue.enqueue({ message: 'Signed out', tone: 'info' })

    expect(queue.current.value?.message).toBe('Signed out')
  })
})

// Each test builds its own queue and its own fake clock, so no state or timer carries over.
describe('snackbar queue', () => {
  let queue

  beforeEach(() => {
    vi.useFakeTimers()
    queue = createSnackbarQueue()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  const messageOnScreen = () => queue.current.value?.message ?? null

  it('shows only the first notice and keeps the others waiting', () => {
    queue.enqueue({ message: 'First', tone: 'error' })
    queue.enqueue({ message: 'Second', tone: 'error' })

    expect(messageOnScreen(), 'the first notice must be the one on screen').toBe('First')
    expect(queue.entries.value.length, 'both notices must be queued').toBe(2)
  })

  it('shows the next notice once the visible one is dismissed', () => {
    const first = queue.enqueue({ message: 'First', tone: 'error' })
    queue.enqueue({ message: 'Second', tone: 'error' })

    queue.dismiss(first)

    expect(messageOnScreen(), 'the queued notice must take the place of the dismissed one').toBe('Second')
  })

  it('removes a success notice after the auto-dismiss delay', () => {
    queue.enqueue({ message: 'Configuration updated', tone: 'success' })

    vi.advanceTimersByTime(AUTO_DISMISS_MS - 1)
    expect(messageOnScreen(), 'the notice must still be visible just before the delay').toBe('Configuration updated')

    vi.advanceTimersByTime(1)
    expect(messageOnScreen(), 'the notice must be gone once the delay has passed').toBeNull()
  })

  it('removes an info notice after the auto-dismiss delay', () => {
    queue.enqueue({ message: 'Signed out', tone: 'info' })

    vi.advanceTimersByTime(AUTO_DISMISS_MS)

    expect(messageOnScreen(), 'info notices leave by themselves like success notices').toBeNull()
  })

  it('keeps an error notice on screen until ten seconds have passed', () => {
    queue.enqueue({ message: 'Cannot delete file', tone: 'error' })

    vi.advanceTimersByTime(ERROR_MS - 1)
    expect(messageOnScreen(), 'an error must stay visible for its full ten seconds').toBe('Cannot delete file')

    vi.advanceTimersByTime(1)
    expect(messageOnScreen(), 'an error must leave after ten seconds').toBeNull()
  })

  it('removes a warning notice after six seconds', () => {
    queue.enqueue({ message: 'Session expired, please sign in again', tone: 'warning' })

    vi.advanceTimersByTime(WARNING_MS - 1)
    expect(messageOnScreen(), 'a warning must stay visible for its six seconds').toBe('Session expired, please sign in again')

    vi.advanceTimersByTime(1)
    expect(messageOnScreen(), 'a warning must leave after six seconds').toBeNull()
  })

  it('lets a persistent error at the head pass after its delay so the queue keeps moving', () => {
    queue.enqueue({ message: 'Cannot load instance status', tone: 'error' })
    queue.enqueue({ message: 'Configuration loaded', tone: 'success' })

    vi.advanceTimersByTime(ERROR_MS)

    expect(messageOnScreen(), 'the notice behind a failed request must not wait forever').toBe('Configuration loaded')
  })

  it('keeps a notice with an action until the user dismisses it or takes the action', () => {
    queue.enqueue({ message: 'Deleted', tone: 'success', action: { label: 'Undo', onClick: () => {} } })

    vi.advanceTimersByTime(60000)

    expect(messageOnScreen(), 'a notice offering an action must not expire on its own').toBe('Deleted')
  })

  it('dismisses a notice with an action when the user dismisses it', () => {
    const id = queue.enqueue({ message: 'Deleted', tone: 'success', action: { label: 'Undo', onClick: () => {} } })

    queue.dismiss(id)

    expect(messageOnScreen(), 'dismissing an action notice must remove it').toBeNull()
  })

  it('does not enqueue a message identical to the visible one', () => {
    queue.enqueue({ message: 'Cannot load statistics', tone: 'error' })
    queue.enqueue({ message: 'Cannot load statistics', tone: 'error' })

    expect(queue.entries.value.length, 'a repeated failure must not stack a second notice').toBe(1)
  })

  it('restarts the timer of the visible notice when an identical one arrives', () => {
    queue.enqueue({ message: 'Saved', tone: 'success' })
    vi.advanceTimersByTime(3000)

    queue.enqueue({ message: 'Saved', tone: 'success' })

    vi.advanceTimersByTime(AUTO_DISMISS_MS - 1)
    expect(messageOnScreen(), 'the repeated notice must get a fresh full delay').toBe('Saved')

    vi.advanceTimersByTime(1)
    expect(messageOnScreen(), 'the repeated notice must leave after its fresh delay').toBeNull()
  })

  it('does not enqueue a message identical to the last waiting notice', () => {
    queue.enqueue({ message: 'Busy', tone: 'error' })
    queue.enqueue({ message: 'Retrying', tone: 'info' })
    queue.enqueue({ message: 'Retrying', tone: 'info' })

    expect(queue.entries.value.length, 'a repeat of the last queued notice must be collapsed').toBe(2)
  })

  it('keeps messages with the same text but a different severity apart', () => {
    queue.enqueue({ message: 'Done', tone: 'success' })
    queue.enqueue({ message: 'Done', tone: 'error' })

    expect(queue.entries.value.length, 'severity is part of the identity of a notice').toBe(2)
  })

  it('keeps at most three waiting notices and drops the oldest non-error one', () => {
    queue.enqueue({ message: 'Visible', tone: 'error' })
    queue.enqueue({ message: 'Waiting 1', tone: 'info' })
    queue.enqueue({ message: 'Waiting 2', tone: 'info' })
    queue.enqueue({ message: 'Waiting 3', tone: 'info' })
    queue.enqueue({ message: 'Waiting 4', tone: 'info' })

    const waiting = queue.entries.value.slice(1).map(entry => entry.message)
    expect(waiting, 'the oldest non-error notice must be the one dropped').toEqual(['Waiting 2', 'Waiting 3', 'Waiting 4'])
  })

  it('never drops a waiting error to make room', () => {
    queue.enqueue({ message: 'Visible', tone: 'info' })
    queue.enqueue({ message: 'Failure 1', tone: 'error' })
    queue.enqueue({ message: 'Failure 2', tone: 'error' })
    queue.enqueue({ message: 'Failure 3', tone: 'error' })
    queue.enqueue({ message: 'Note', tone: 'info' })

    const waiting = queue.entries.value.slice(1).map(entry => entry.message)
    expect(waiting, 'errors must be kept, so the new non-error notice is the one dropped').toEqual(['Failure 1', 'Failure 2', 'Failure 3'])
  })

  it('starts the timer of the next success notice only when it becomes visible', () => {
    const first = queue.enqueue({ message: 'Saved', tone: 'success' })
    queue.enqueue({ message: 'Uploaded', tone: 'success' })

    vi.advanceTimersByTime(AUTO_DISMISS_MS - 1000)
    queue.dismiss(first)
    vi.advanceTimersByTime(AUTO_DISMISS_MS - 1)
    expect(messageOnScreen(), 'the second notice must get its full delay after it appears').toBe('Uploaded')

    vi.advanceTimersByTime(1)
    expect(messageOnScreen(), 'the second notice must leave once its own delay has passed').toBeNull()
  })

  it('honours a duration given by the caller', () => {
    queue.enqueue({ message: 'Short lived', tone: 'error', duration: 1000 })

    vi.advanceTimersByTime(1000)

    expect(messageOnScreen(), 'an explicit duration must override the tone default').toBeNull()
  })

  it('runs the action callback once and dismisses the notice', () => {
    const onClick = vi.fn()
    queue.enqueue({ message: 'Deleted', tone: 'success', action: { label: 'Undo', onClick } })

    queue.runAction(queue.current.value.id)

    expect(onClick, 'the action callback must run exactly once').toHaveBeenCalledTimes(1)
    expect(messageOnScreen(), 'the notice must be dismissed after its action ran').toBeNull()
  })

  it('dismisses the notice even when the action callback throws', () => {
    queue.enqueue({
      message: 'Deleted',
      tone: 'error',
      action: { label: 'Retry', onClick: () => { throw new Error('boom') } }
    })

    expect(() => queue.runAction(queue.current.value.id), 'the error must reach the caller').toThrow('boom')
    expect(messageOnScreen(), 'a failing action must not leave the notice stuck on screen').toBeNull()
  })

  it('does nothing when dismissing an id that is not queued', () => {
    queue.enqueue({ message: 'Only one', tone: 'error' })

    queue.dismiss(9999)

    expect(messageOnScreen(), 'an unknown id must leave the visible notice alone').toBe('Only one')
  })

  it('gives every notice a distinct id', () => {
    const first = queue.enqueue({ message: 'A', tone: 'error' })
    const second = queue.enqueue({ message: 'B', tone: 'error' })

    expect(first, 'ids must not repeat within a queue').not.toBe(second)
  })
})
