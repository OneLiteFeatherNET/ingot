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

import { adminGroups, OVERVIEW_TREE_KEY } from '../../navigation/registry'
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

// The cards of the hub: the Administration tree the visitor may open, in its groups. A settings domain is a card
// with its saved summary, and `unsaved` lists the domains whose draft differs from the saved value. A page is a
// card without a summary. `can(node)` answers for the visitor, and a settings domain is listed only with the settings node.
// The Repositories page edits the maven domain, so its card carries the summary and the draft mark of that domain.
const domainOfCard = (link) => {
  if (link.to.name === 'settings') return link.to.params.domain
  return link.to.name === 'settings-repositories' ? 'maven' : undefined
}

// The entries of the tree that a domain with an unsaved draft marks. The Repositories page edits the maven domain, so its
// draft marks that page, and the other domains mark their own settings entry.
const unsavedTreeKeys = (domains) => domains.map(domain => (domain === 'maven' ? 'page:repositories' : `settings:${domain}`))

// Overview is the page this list is drawn on, so the cards leave it out.
const hubGroups = ({ can, domains, schemas, saved, unsaved, titleOf }) => adminGroups(can, {
  domains: settingsTabDomains(domains),
  titleOf,
}).map(group => ({
  heading: group.heading,
  cards: group.links.filter(link => link.key !== OVERVIEW_TREE_KEY).map(link => {
    const domain = domainOfCard(link)
    const isDomain = domain !== undefined
    return {
      key: link.key,
      title: link.label,
      summary: isDomain ? domainSummary(schemas[domain], saved[domain]) : '',
      unsaved: isDomain && unsaved.includes(domain),
      to: link.to,
    }
  }),
})).filter(group => group.cards.length > 0)

export { domainSummary, hubGroups, unsavedTreeKeys }
