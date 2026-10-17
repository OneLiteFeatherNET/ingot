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
 * The members of a group repository: the repository ids it lists, in order. In the settings a
 * group keeps them as mirrored entries whose reference is the id, so the list is read from and
 * written to `proxied`. Nothing here touches Vue, so the picker and the wizard share the rules.
 */

/* The ids a group lists, in the order of its mirrored entries. */
export const membersOf = (proxied) => (Array.isArray(proxied) ? proxied : [])
  .map(item => String(item?.reference ?? ''))
  .filter(id => id !== '')

/* The mirrored entries that a group with these members is saved with. */
export const proxiedOfMembers = (ids) => ids.map(id => ({ reference: id }))

/* The members with one more id at the end. An empty id, or one already listed, changes nothing. */
export const addMember = (ids, id) => (id && !ids.includes(id) ? [...ids, id] : ids)

export const removeMember = (ids, id) => ids.filter(member => member !== id)

/* The members with the one at `index` moved by `delta` places. A move past either end changes nothing. */
export const moveMember = (ids, index, delta) => {
  const target = index + delta
  if (index < 0 || index >= ids.length || target < 0 || target >= ids.length) return ids
  const next = [...ids]
  ;[next[index], next[target]] = [next[target], next[index]]
  return next
}

/* The repositories that can still be added, in the order they are given. */
export const choosableMembers = (candidates, ids) => candidates.filter(candidate => !ids.includes(candidate))
