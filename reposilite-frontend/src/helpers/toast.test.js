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

// The helpers write to the shared snackbar queue. Modules are reset before every test, so
// each test starts with an empty queue and nothing leaks from one test into the next.
describe('toast helpers', () => {
  let toast
  let snackbar

  beforeEach(async () => {
    vi.resetModules()
    vi.useFakeTimers()
    toast = await import('./toast')
    snackbar = (await import('../store/snackbar')).snackbar
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('maps a failure to the error tone', () => {
    toast.createErrorToast('Cannot delete file')

    expect(snackbar.current.value.tone, 'createErrorToast must enqueue an error notice').toBe('error')
    expect(snackbar.current.value.message).toBe('Cannot delete file')
  })

  it('maps a confirmation to the success tone', () => {
    toast.createSuccessToast('Configuration updated')

    expect(snackbar.current.value.tone, 'createSuccessToast must enqueue a success notice').toBe('success')
  })

  it('maps the warning and info helpers to their own tones', () => {
    toast.createWarningToast('Session expired')
    toast.createInfoToast('Signed out')

    expect(snackbar.entries.value.map(entry => entry.tone), 'each helper keeps its own tone').toEqual(['warning', 'info'])
  })

  it('passes an action through to the queue', () => {
    const onClick = vi.fn()

    toast.createSuccessToast('Deleted', { action: { label: 'Undo', onClick } })

    expect(snackbar.current.value.action.label, 'the action label must reach the queue').toBe('Undo')
    snackbar.runAction(snackbar.current.value.id)
    expect(onClick, 'running the action must call the caller callback').toHaveBeenCalledTimes(1)
  })

  it('keeps an error on screen while a success queued behind it waits', () => {
    toast.createErrorToast('Cannot load statistics')
    toast.createSuccessToast('Configuration loaded')

    vi.advanceTimersByTime(60000)

    expect(snackbar.current.value.message, 'the error must still be the one on screen').toBe('Cannot load statistics')
    expect(snackbar.entries.value.length, 'the success must still be waiting').toBe(2)
  })
})
