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

// Rules of the system information page that need no component: which tab holds a section anchor, and how the
// filter of the long tabs narrows a list of rows. Both work on the strings the server sent and redacted.

export type SystemInfoTab = 'overview' | 'plugins' | 'environment' | 'configuration'

// The section anchors of the page that predate the tabs. Each one opens the tab that holds its section.
const TAB_BY_FRAGMENT: Readonly<Record<string, SystemInfoTab>> = Object.freeze({
  'support-ingot': 'overview',
  'support-java': 'overview',
  'support-os': 'overview',
  'support-database': 'overview',
  'support-storage': 'overview',
  'support-cluster': 'overview',
  'support-plugins': 'plugins',
  'support-environment': 'environment',
  'support-properties': 'environment',
  'support-configuration': 'configuration',
})

// The tab of a fragment such as "#support-properties", or null when the fragment is not one of the section anchors.
export const tabForFragment = (hash: string): SystemInfoTab | null => {
  const id = hash.startsWith('#') ? hash.slice(1) : hash
  return Object.prototype.hasOwnProperty.call(TAB_BY_FRAGMENT, id) ? TAB_BY_FRAGMENT[id] : null
}

// Keeps the rows whose key or value contains the query, without regard to case. An empty query keeps every row.
export const filterRows = (rows: readonly [string, string][], query: string): [string, string][] => {
  const needle = query.trim().toLowerCase()
  if (needle === '') return [...rows]
  return rows.filter(([key, value]) => key.toLowerCase().includes(needle) || value.toLowerCase().includes(needle))
}
