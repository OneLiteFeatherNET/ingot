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
 * Which groups of the draft name a repository as a member, and the text of the Mixed warning. Nothing here touches
 * Vue, the router or the network.
 */

import { effectiveTypeOf } from '../../store/maven/repositoryTypes'

export const MIXED_WARNING = 'References local and remote repositories. Split it into a group and a proxy.'

/*
 * The ids of the groups of the draft that list the repository `id` as a member, in the order of the draft. An entry
 * without an id (`id` is empty) has no member name, so no group is named for it; a blank reference is not a match.
 */
export const groupsReferencing = (entries, id) => (id === '' ? [] : entries
  .filter(entry => entry.id !== id && effectiveTypeOf(entry).type === 'GROUP')
  .filter(entry => (Array.isArray(entry.proxied) ? entry.proxied : [])
    .some(item => String(item?.reference ?? '').trim() === id))
  .map(entry => entry.id))
