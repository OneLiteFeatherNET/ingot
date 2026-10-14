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

import { computed, ref } from 'vue'

/*
 * The queue behind MdSnackbar. Only the head of the queue is shown, so a burst of
 * notifications becomes a sequence instead of a stack that covers the page.
 *
 * Every notice leaves on its own, so that a persistent failure cannot block the queue behind
 * it: success and info after 4 seconds, warnings after 6, errors after 10. A notice with an
 * action stays until it is dismissed or the action is taken. A caller can override the
 * duration; `null` means "stay until dismissed".
 *
 * Repeats are collapsed: a notice identical to the visible one restarts its timer, and one
 * identical to the last queued notice is not queued again. The waiting line holds at most
 * three notices. When it is full, the oldest waiting non-error notice is dropped; errors are
 * never dropped.
 *
 * The timers are injected so that a test can drive them explicitly. The default uses the
 * global timer functions, which `vi.useFakeTimers()` replaces.
 */

export const AUTO_DISMISS_MS = 4000
export const MAX_WAITING = 3

const DURATION_MS = { success: 4000, info: 4000, warning: 6000, error: 10000 }

const defaultDuration = (tone, action) => (action ? null : DURATION_MS[tone] ?? DURATION_MS.info)

const systemTimers = {
  setTimeout: (callback, ms) => globalThis.setTimeout(callback, ms),
  clearTimeout: (handle) => globalThis.clearTimeout(handle)
}

export function createSnackbarQueue({ timers = systemTimers } = {}) {
  const entries = ref([])
  const timerHandles = new Map()
  let nextId = 1

  // The entry on screen. Everything behind it waits.
  const current = computed(() => entries.value[0] ?? null)

  const cancelTimer = (id) => {
    if (timerHandles.has(id)) {
      timers.clearTimeout(timerHandles.get(id))
      timerHandles.delete(id)
    }
  }

  const startTimer = (entry) => {
    if (entry.duration === null) return
    timerHandles.set(entry.id, timers.setTimeout(() => dismiss(entry.id), entry.duration))
  }

  function dismiss(id) {
    if (!entries.value.some(entry => entry.id === id)) return
    cancelTimer(id)
    entries.value = entries.value.filter(entry => entry.id !== id)
    if (current.value) startTimer(current.value)
  }

  /**
   * Adds a notice to the queue and returns its id. It is shown at once when nothing else is
   * on screen, otherwise after the notices ahead of it have been dismissed.
   */
  function enqueue({ message, tone = 'info', action = null, duration }) {
    const last = entries.value[entries.value.length - 1]
    if (last && last.message === message && last.tone === tone) {
      // A repeat of the notice on screen gets a fresh timer; a repeat of a waiting notice is
      // already queued and says the same thing, so it is not queued again.
      if (entries.value.length === 1) {
        cancelTimer(last.id)
        startTimer(last)
      }
      return last.id
    }

    const entry = {
      id: nextId++,
      message,
      tone,
      action,
      duration: duration === undefined ? defaultDuration(tone, action) : duration
    }
    entries.value = [...entries.value, entry]
    if (entries.value.length === 1) startTimer(entry)
    dropOverflow()
    return entry.id
  }

  // Keeps the waiting line within MAX_WAITING by removing the oldest non-error notice. Errors
  // are never removed, so the line can exceed the limit when it holds only errors.
  function dropOverflow() {
    while (entries.value.length - 1 > MAX_WAITING) {
      const oldest = entries.value.slice(1).find(entry => entry.tone !== 'error')
      if (!oldest) return
      entries.value = entries.value.filter(entry => entry !== oldest)
    }
  }

  // Runs the action of a notice, then dismisses it. Dismissal happens even when the
  // callback throws, so a failing action cannot leave the notice stuck on screen.
  function runAction(id) {
    const entry = entries.value.find(item => item.id === id)
    if (!entry) return
    try {
      entry.action?.onClick?.()
    } finally {
      dismiss(id)
    }
  }

  return { entries, current, enqueue, dismiss, runAction }
}

// The queue the application shares. Components and the toast helper use this one.
export const snackbar = createSnackbarQueue()
