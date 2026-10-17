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
 * The repository types of the maven domain, as the views show them. Nothing here touches Vue, the
 * router or the network, so the rules can be tested directly.
 *
 * A repository has an effective type in the lists: hosted, proxy, group, or mixed when it has no
 * type of its own. A repository without a type of its own keeps the behaviour it had before, and
 * the badge says so with a hint. The type the settings store is the upper case name of the same.
 */

export const LEGACY_HINT = 'No type set, behaves as before. Set a type to enforce the rules.'

export const TYPE_LABELS = { hosted: 'Hosted', proxy: 'Proxy', group: 'Group', mixed: 'Mixed' }

const TYPE_KEYS = Object.keys(TYPE_LABELS)

/* The badge of one repository of the list: its key, its label, and the legacy hint when it has no type of its own. */
export const typeBadgeOf = (repository = {}) => {
  const raw = typeof repository?.type === 'string' ? repository.type.toLowerCase() : ''
  const key = TYPE_KEYS.includes(raw) ? raw : 'mixed'
  const explicit = repository?.typeExplicit === true
  return {
    key,
    label: TYPE_LABELS[key],
    legacy: !explicit,
    hint: explicit ? '' : LEGACY_HINT
  }
}

/* The badge of one entry of the settings, where the type is set exactly when the entry has one. */
export const settingsTypeBadge = (entry = {}) => typeBadgeOf({ type: entry.type, typeExplicit: entry.type !== undefined })

/* The chips of the overview, in the order they are shown. */
export const TYPE_FILTERS = TYPE_KEYS.map(key => ({ key, label: TYPE_LABELS[key] }))

/* The types named in the address, lower cased, without unknown or repeated values. */
export const typesFromParam = (param) => {
  const values = [param ?? []].flat()
  const keys = values
    .filter(value => typeof value === 'string')
    .map(value => value.trim().toLowerCase())
    .filter(value => TYPE_KEYS.includes(value))
  return [...new Set(keys)]
}

/* The repositories whose badge is one of the selected types. No selection keeps them all. */
export const filterByType = (repositories, selected = []) => (selected.length === 0
  ? repositories
  : repositories.filter(repository => selected.includes(typeBadgeOf(repository).key)))
