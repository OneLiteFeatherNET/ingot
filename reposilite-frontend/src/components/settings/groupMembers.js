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
 * The members of a group repository: the mirrored entries it lists, in order. In the settings a group keeps
 * them as mirrored entries whose reference is the repository id. An entry carries more than the reference:
 * its credential, store, allowed groups and timeouts, and any key this dashboard does not know. So the members
 * are changed as the entries themselves: a member that stays is the same object, and nothing rebuilds it from
 * its id. Nothing here touches Vue, so the picker and the editor share the rules.
 */

/* The repository id an entry names, or an empty string for an entry that names none. */
export const referenceOf = (entry) => String(entry?.reference ?? '')

/* The only method a group member takes: a loopback link to a repository of this instance, with a login and a password. */
export const LOOPBACK_METHOD = 'LOOPBACK_LINK'

/*
 * The entry with one field of its credential set to `value`. A member without a credential gets a loopback one. Every other
 * key of the credential and of the entry is kept, and the entry it is given is not changed.
 */
export const withCredentialField = (entry, field, value) => ({
  ...entry,
  authorization: {
    method: LOOPBACK_METHOD,
    ...(entry.authorization ?? {}),
    [field]: value
  }
})

/* The entries with one more repository at the end, as a bare reference. An empty id, or one already listed, changes nothing. */
export const addMember = (entries, id) => (id && !entries.some(entry => referenceOf(entry) === id)
  ? [...entries, { reference: id }]
  : entries)

/* The entries without the one at `index`. Another entry that names the same repository stays: only the place given is taken out. */
export const removeMemberAt = (entries, index) => entries.filter((_, position) => position !== index)

/* The entries with the one at `index` moved by `delta` places. A move past either end changes nothing. */
export const moveMember = (entries, index, delta) => {
  const target = index + delta
  if (index < 0 || index >= entries.length || target < 0 || target >= entries.length) return entries
  const next = [...entries]
  ;[next[index], next[target]] = [next[target], next[index]]
  return next
}

/* The repositories that can still be added, in the order they are given. */
export const choosableMembers = (candidates, entries) => candidates.filter(candidate => !entries.some(entry => referenceOf(entry) === candidate))
