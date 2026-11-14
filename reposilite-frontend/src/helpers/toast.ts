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

import { snackbar } from '../store/snackbar'

/*
 * The notifications of the dashboard. They are shown by MdSnackbar, which is mounted once in
 * App.vue, so every caller shares one queue and one place on screen: bottom centre, or
 * bottom left on wide screens. Only one notice is visible at a time; the others wait.
 *
 * Each helper takes a message and an optional `{ action: { label, onClick }, duration }`.
 * A duration of `null` keeps the notice until it is dismissed. Success and info notices
 * leave by themselves, warnings and errors stay.
 */
export type Tone = 'info' | 'success' | 'warning' | 'error'

export interface ToastOptions {
  action?: { label: string; onClick: () => void }
  duration?: number | null
}

// The queue is still a JavaScript module, and TypeScript infers its types from it: the entries as never[], and the
// enqueue parameter with action as null. This view names the shape the helpers read and write, until the store has
// types of its own, and it is the only place that bridges the two.
interface Notice {
  message: string
  tone: Tone
  action?: ToastOptions['action']
  duration?: number | null
}

interface NoticeQueue {
  entries: { value: Notice[] }
  enqueue(notice: Notice): unknown
}

const queue = snackbar as unknown as NoticeQueue

const show = (tone: Tone) => (message: string, options: ToastOptions = {}) =>
  queue.enqueue({ message, tone, ...options })

// The fields of a failed request that the message is read from. Any other value reads as its text.
interface FailedRequest {
  message?: unknown
  response?: { status?: number; data?: { message?: unknown } }
}

/**
 * The readable part of a failed request.
 *
 * Callers reached into `error.response.status` and `error.response.data.message` directly,
 * which works for a server that answered and throws for one that did not. The throw
 * happened inside a `.catch`, so it took the handler with it: exactly when the connection
 * was refused or the host was unreachable, the user was told nothing at all. That is the
 * most common failure there is, and it was the one case with no message.
 */
const errorMessage = (error: unknown): string => {
  const failure = error as FailedRequest | null | undefined
  return withoutExceptionName(
    failure?.response?.data?.message ||
    (failure?.response?.status ? `Request failed with status ${failure.response.status}` : null) ||
    failure?.message ||
    String(error)
  )
}

/*
 * A Java failure reaches the dashboard as "java.lang.IllegalArgumentException: text", and the class name is no part of
 * what the user needs to read. Only the qualified name at the start is removed, so the text itself is kept as written.
 */
const JAVA_EXCEPTION_NAME = /^(?:[a-z_$][\w$]*\.)+[A-Z][\w$]*:\s*/

const withoutExceptionName = (text: unknown): string => String(text).replace(JAVA_EXCEPTION_NAME, '')

const createInfoToast = show('info')

// An info notice that is skipped while one with the same text is still in the queue, so a burst of
// refused navigations shows the message once.
const createInfoToastOnce = (message: string, options?: ToastOptions) =>
  queue.entries.value.some(entry => entry.message === message)
    ? null
    : createInfoToast(message, options)

const createSuccessToast = show('success')

const createWarningToast = show('warning')

const createErrorToast = show('error')

export {
  createInfoToast,
  createInfoToastOnce,
  createSuccessToast,
  createWarningToast,
  createErrorToast,
  errorMessage
}
