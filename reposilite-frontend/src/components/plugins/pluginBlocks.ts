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
 * The helpers of the plugin blocks. The server checks each path when the plugin registers it
 * (PluginUiValidator); the dashboard checks it again before it calls anything, so a manifest changed
 * in transit cannot widen a call. The rule is the same in both places.
 */

export const BLOCK_TYPES = ['text', 'keyValue', 'table', 'form', 'action', 'status']

export const MAX_TABLE_ROWS = 500

const PATH_CHARACTERS = /^[A-Za-z0-9._~/-]+$/

// A path is allowed when it is below the prefix of the plugin and climbs out of nothing.
export const pathAllowed = (plugin, path) => {
  const prefix = `/api/plugins/${plugin}/`
  if (typeof path !== 'string' || !path.startsWith(prefix) || !PATH_CHARACTERS.test(path)) return false
  const rest = path.slice(prefix.length)
  return rest.length > 0 && rest.split('/').every(segment => segment !== '' && segment !== '.' && segment !== '..')
}

// The text a value shows as: strings as they are, everything else as compact JSON.
export const displayValue = (value) => {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

// The rows a table shows and how many it cut. The count of the rows is kept, so the cut is said out loud.
export const tableRows = (rows) => ({
  shown: rows.slice(0, MAX_TABLE_ROWS),
  total: rows.length,
  cut: rows.length > MAX_TABLE_ROWS,
})
