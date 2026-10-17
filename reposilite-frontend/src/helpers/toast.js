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
const show = (tone) => (message, options = {}) =>
  snackbar.enqueue({ message, tone, ...options })

/**
 * The readable part of a failed request.
 *
 * Callers reached into `error.response.status` and `error.response.data.message` directly,
 * which works for a server that answered and throws for one that did not. The throw
 * happened inside a `.catch`, so it took the handler with it: exactly when the connection
 * was refused or the host was unreachable, the user was told nothing at all. That is the
 * most common failure there is, and it was the one case with no message.
 */
const errorMessage = (error) => withoutExceptionName(
  error?.response?.data?.message ||
  (error?.response?.status ? `Request failed with status ${error.response.status}` : null) ||
  error?.message ||
  `${error}`
)

/*
 * A Java failure reaches the dashboard as "java.lang.IllegalArgumentException: text", and the class name is no part of
 * what the user needs to read. Only the qualified name at the start is removed, so the text itself is kept as written.
 */
const JAVA_EXCEPTION_NAME = /^(?:[a-z_$][\w$]*\.)+[A-Z][\w$]*:\s*/

const withoutExceptionName = (text) => String(text).replace(JAVA_EXCEPTION_NAME, '')

const createInfoToast = show('info')

// An info notice that is skipped while one with the same text is still in the queue, so a burst of
// refused navigations shows the message once.
const createInfoToastOnce = (message, options) =>
  snackbar.entries.value.some(entry => entry.message === message)
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
