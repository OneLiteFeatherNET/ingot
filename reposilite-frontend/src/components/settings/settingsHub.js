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

import { groupedSettingsDomains } from '../../navigation/registry'
import { collectionsOf } from './collections'
import { settingsTabDomains } from './accessDomains'

/*
 * The data behind the settings hub. The hub reads the saved configuration, never the draft: a card
 * shows what the server holds, and marks a domain whose draft differs from it.
 */

const countText = (collection, count) => `${count} ${count === 1 ? collection.noun : collection.title.toLowerCase()}`

// A short text for a domain: the entry counts of its collections ("6 repositories"), or for a domain
// without collections the one-line description of its schema.
const domainSummary = (schema, saved) => {
  const collections = collectionsOf(schema)
  if (collections.length === 0) return schema?.description ?? ''
  return collections.map(collection => countText(collection, saved?.[collection.key]?.length ?? 0)).join(', ')
}

// The cards of the hub in the groups of the registry. `unsaved` lists the domains whose draft differs from
// the saved value, and `editable(domain)` tells whether the visitor may edit one; a group without a card is left out.
const hubGroups = ({ domains, schemas, saved, unsaved, titleOf, editable }) => groupedSettingsDomains(
  settingsTabDomains(domains).filter(editable)
).map(group => ({
  heading: group.heading,
  cards: group.domains.map(domain => ({
    key: domain,
    title: titleOf(domain),
    summary: domainSummary(schemas[domain], saved[domain]),
    unsaved: unsaved.includes(domain),
    to: { name: 'settings', params: { domain } }
  }))
}))

export { domainSummary, hubGroups }
