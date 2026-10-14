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
const errorMessage = (error) =>
  error?.response?.data?.message ||
  (error?.response?.status ? `Request failed with status ${error.response.status}` : null) ||
  error?.message ||
  `${error}`

const createInfoToast = show('info')

const createSuccessToast = show('success')

const createWarningToast = show('warning')

const createErrorToast = show('error')

export {
  createInfoToast,
  createSuccessToast,
  createWarningToast,
  createErrorToast,
  errorMessage
}
