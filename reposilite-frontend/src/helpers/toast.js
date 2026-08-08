import { createToast } from 'mosha-vue-toastify'

/*
 * Bottom right, and in one place. The library defaults to the top right corner, which is
 * exactly where the header keeps the account name and the logout button: while a toast was
 * up, logging out was not clickable. Callers used to reach for `createToast` directly and
 * pick a corner each, so a single login attempt could put its success message in one corner
 * and its failure in another.
 */
const options = (type) => ({ type, position: 'bottom-right' })

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

const createInfoToast = (message) =>
  createToast(message, options('info'))

const createSuccessToast = (message) =>
  createToast(message, options('success'))

const createWarningToast = (message) =>
  createToast(message, options('warning'))

const createErrorToast = (message) =>
  createToast(message, options('danger'))

export {
  createInfoToast,
  createSuccessToast,
  createWarningToast,
  createErrorToast,
  errorMessage
}
