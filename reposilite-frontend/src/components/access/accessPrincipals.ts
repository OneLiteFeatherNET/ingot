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

import { instantToDate } from '../../helpers/apiTime'
import { formatDateTime, formatRelative } from '../identity/identityTokens'

/*
 * The rules of the Access pages that need no component: the search filter, the relative time
 * labels, the password and name checks, the create body and the reading of an admin answer.
 * Nothing here reads the system time; the labels count from the injected clock.
 */

const MIN_PASSWORD_LENGTH = 12

const normalize = (text) => String(text ?? '').trim().toLowerCase()

/** The principals whose username or display name contains the query, ignoring case and outer spaces. */
const filterPrincipals = (principals, query) => {
  const needle = normalize(query)
  if (needle === '') return principals
  return principals.filter(principal =>
    normalize(principal.name).includes(needle) || normalize(principal.displayName).includes(needle))
}

// The values are the Instants the API sends (epoch seconds); a missing one has no date.
const labelOf = (value, never) => {
  const date = instantToDate(value)
  return date ? { text: formatRelative(date), title: formatDateTime(date) } : { text: never, title: undefined }
}

/** The last sign-in of a principal: a relative text, and the absolute time as the tooltip. */
const seenLabel = (lastSeenAt) => labelOf(lastSeenAt, 'Never signed in')

/** The last use of a machine account: a relative text, and the absolute time as the tooltip. */
const usedLabel = (lastUsedAt) => labelOf(lastUsedAt, 'Never used')

const time = (value) => instantToDate(value)?.getTime() ?? -Infinity

/** The most recent lastUsedAt among the tokens, or null when none was ever used. */
const newestLastUsed = (tokens) =>
  (tokens ?? [])
    .map(token => token.lastUsedAt)
    .filter(Boolean)
    .reduce((newest, value) => (newest === null || time(value) > time(newest) ? value : newest), null)

/** The message for each password field, or an empty string where the field is fine. */
const validateNewPassword = (password, confirm) => ({
  password: String(password ?? '').length < MIN_PASSWORD_LENGTH ? `Use at least ${MIN_PASSWORD_LENGTH} characters` : '',
  confirm: confirm !== password ? 'Passwords do not match' : ''
})

/** The message for a name that cannot be used, or an empty string. */
const validatePrincipalName = (name) => {
  const value = String(name ?? '').trim()
  if (value === '') return 'A name is required'
  if (/[:/]/.test(value)) return "A name must not contain ':' or '/'"
  return ''
}

/** The body of a create request. A blank display name and a false disabled flag are left out. */
const buildCreateBody = ({ kind, name, displayName = '', disabled = false }) => {
  const body = { kind, name: String(name).trim() }
  const text = String(displayName ?? '').trim()
  if (text !== '') body.displayName = text
  if (disabled) body.disabled = true
  return body
}

/**
 * The detail of a principal as the views read it: flat, with the lists present. The contract is
 * flat; an answer that nests the principal is flattened, so either shape reads the same.
 */
const detailOf = (data) => {
  const { principal, ...rest } = data ?? {}
  return {
    ...(principal ?? {}),
    ...rest,
    teams: rest.teams ?? [],
    tokens: rest.tokens ?? []
  }
}

const isInstanceAdmin = (detail) => detail?.instanceRole === true || detail?.instanceAdmin === true

/** The routes of a migrated token, one entry per path with the read and write rights it had. */
const legacyGrants = (token) => {
  const byPath = new Map()
  for (const route of token?.routes ?? []) {
    const entry = byPath.get(route.path) ?? { path: route.path, read: false, write: false }
    if (route.permission?.identifier === 'route:read') entry.read = true
    if (route.permission?.identifier === 'route:write') entry.write = true
    byPath.set(route.path, entry)
  }
  return [...byPath.values()]
}

export {
  MIN_PASSWORD_LENGTH,
  buildCreateBody,
  detailOf,
  filterPrincipals,
  isInstanceAdmin,
  legacyGrants,
  newestLastUsed,
  seenLabel,
  usedLabel,
  validateNewPassword,
  validatePrincipalName
}
