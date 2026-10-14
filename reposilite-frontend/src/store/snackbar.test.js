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

  it('keeps an error notice on screen long after the auto-dismiss delay', () => {
    queue.enqueue({ message: 'Cannot delete file', tone: 'error' })

    vi.advanceTimersByTime(60000)

    expect(messageOnScreen(), 'an error must stay until the user dismisses it').toBe('Cannot delete file')
  })

  it('keeps a warning notice on screen until it is dismissed', () => {
    queue.enqueue({ message: 'Session expired, please sign in again', tone: 'warning' })

    vi.advanceTimersByTime(60000)

    expect(messageOnScreen(), 'a warning asks the user to act, so it must not expire').toBe('Session expired, please sign in again')
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
