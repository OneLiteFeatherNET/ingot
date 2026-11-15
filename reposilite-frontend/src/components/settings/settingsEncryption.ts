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
 * Rules of the password protected settings file. The server is the one that enforces them; the dialog
 * checks them first so that a mistake is shown before a request is sent.
 */

export const EXPORT_FORMAT = 'ingot-settings-export'
export const MIN_PASSWORD_LENGTH = 12

/* True for a file written by the password protected export, which is opened with its password before it is reviewed. */
export const isEncryptedExport = (value) =>
  value !== null && typeof value === 'object' && !Array.isArray(value) && value.format === EXPORT_FORMAT

/*
 * The reason the password is not accepted, or null when it may be sent. Export asks for a confirmation,
 * import does not, because a wrong password there only fails the decryption.
 */
export const passwordProblem = ({ password, confirmation = null }) => {
  if (password.length < MIN_PASSWORD_LENGTH) return 'short'
  if (confirmation !== null && confirmation !== password) return 'mismatch'
  return null
}

export const PASSWORD_PROBLEM_MESSAGES = {
  short: `The password must be at least ${MIN_PASSWORD_LENGTH} characters long.`,
  mismatch: 'The two passwords do not match.'
}

/* The file name of the export, and the body of the request that makes it. */
export const exportRequest = (password) => ({ password })

export const importRequest = (password, exported) => ({ password, export: exported })
