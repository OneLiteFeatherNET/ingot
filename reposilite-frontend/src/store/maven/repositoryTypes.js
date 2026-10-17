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

/* The types a repository may be given in the settings, and the choice that gives none. */
export const SETTING_TYPE_OPTIONS = [
  { value: '', label: 'Not set, as before' },
  { value: 'HOSTED', label: 'Hosted' },
  { value: 'PROXY', label: 'Proxy' },
  { value: 'GROUP', label: 'Group' }
]

/* An address of a remote repository: http or https, with a host and an optional path. */
export const isRemoteUrl = (text) => /^https?:\/\/[^\s/]+\S*$/i.test(String(text ?? '').trim())

/*
 * The settings a type keeps. Storage, uploads and mirrored entries belong to one type each, and a
 * group keeps its mirrored entries as its members (repository ids), not as remote addresses.
 */
const KEPT = {
  HOSTED: ['storageProvider', 'redeployment', 'preserveSnapshots'],
  PROXY: ['proxied', 'storagePolicy', 'metadataMaxAge', 'resolutionCacheMaxEntries'],
  GROUP: ['proxied']
}

// The fields the form shows for a type. A group's members are edited as a list of repositories, so the form leaves them out.
const SHOWN = { HOSTED: KEPT.HOSTED, PROXY: KEPT.PROXY, GROUP: [] }

const TYPED_FIELDS = [...new Set(Object.values(KEPT).flat())]

const FIELD_LABELS = {
  storageProvider: 'Storage',
  redeployment: 'Redeployment',
  preserveSnapshots: 'Preserved snapshots',
  proxied: 'Mirrored repositories',
  storagePolicy: 'Storage policy',
  metadataMaxAge: 'Metadata max age',
  resolutionCacheMaxEntries: 'Resolution cache'
}

// A setting holds something when it is not left empty or switched off, so there is nothing to lose when it does not.
const isSet = (value) => value !== undefined && value !== null && value !== '' && value !== false
  && !(Array.isArray(value) && value.length === 0)

const isTyped = (type) => Object.prototype.hasOwnProperty.call(KEPT, type)

/* The typed settings the form of this type leaves out. Nothing is left out of an entry without a type. */
export const hiddenFormFields = (type) => (isTyped(type)
  ? TYPED_FIELDS.filter(field => !SHOWN[type].includes(field))
  : [])

// Switching between a proxy and a group changes what the mirrored entries mean, so they go too.
const dropsMirrors = (from, to) => (from === 'PROXY' || from === 'GROUP') && (to === 'PROXY' || to === 'GROUP') && from !== to

// The settings of the entry that the new type does not keep.
const notKept = (from, to) => TYPED_FIELDS.filter(field => !KEPT[to].includes(field)
  || (field === 'proxied' && dropsMirrors(from, to)))

/* What switching the entry to `to` removes, with the label the user knows it by. Empty settings are not listed. */
export const removalsFor = (entry, from, to) => {
  if (!isTyped(to)) return []
  return notKept(from, to)
    .filter(field => isSet(entry[field]))
    .map(field => {
      const count = Array.isArray(entry[field]) && entry[field].length > 1 ? ` (${entry[field].length})` : ''
      return { key: field, label: `${FIELD_LABELS[field]}${count}` }
    })
}

/* The entry with the type changed: what the new type does not keep is taken out, and the type is written. None takes the type away. */
export const switchedEntry = (entry, to) => {
  const next = { ...entry }
  if (!isTyped(to)) {
    delete next.type
    return next
  }
  for (const field of notKept(entry.type ?? '', to)) delete next[field]
  next.type = to
  return next
}

/*
 * The rules the server enforces, checked before the settings are saved, so the problem is shown next to the type.
 * `otherIds` are the ids of the other repositories; a group may list only those.
 */
export const violationsOf = (entry, { otherIds = [] } = {}) => {
  const references = Array.isArray(entry.proxied) ? entry.proxied.map(item => String(item?.reference ?? '')) : []
  if (entry.type === 'HOSTED') {
    return references.length > 0
      ? ['A hosted repository cannot mirror other repositories. Remove the mirrored repositories, or choose Proxy or Group.']
      : []
  }
  if (entry.type === 'PROXY') {
    if (references.length === 0) return ['A proxy needs at least one remote repository.']
    return references
      .filter(reference => !isRemoteUrl(reference))
      .map(reference => `"${reference}" is not a remote address. A proxy fetches from http or https addresses.`)
  }
  if (entry.type === 'GROUP') {
    if (references.length === 0) return ['A group needs at least one repository to list.']
    return references.flatMap(reference => {
      if (isRemoteUrl(reference)) return [`"${reference}" is an address. A group lists repository ids.`]
      if (reference === entry.id) return ['A group cannot list itself.']
      if (!otherIds.includes(reference)) return [`"${reference}" is not a repository of this instance.`]
      return []
    })
  }
  return []
}
