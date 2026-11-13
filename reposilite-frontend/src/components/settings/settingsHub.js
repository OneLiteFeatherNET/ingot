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

import { collectionsOf } from './collections'

/*
 * The data behind the Overview page of /settings. Both lists read the saved configuration, never the draft: At a glance
 * shows what the server holds, and Needs attention names the tree entries whose domain has a draft that differs.
 */

// The settings domain a settings link of the Administration tree edits. The Repositories page edits the maven domain.
const domainOfLink = (link) => {
  if (link.to.name === 'settings') return link.to.params?.domain
  return link.to.name === 'settings-repositories' ? 'maven' : undefined
}

// Needs attention: the tree links whose key is in `unsavedKeys` (see unsavedTreeKeys), in the order of the tree, each with
// the router location it opens.
const attentionRows = (links, unsavedKeys) => links
  .filter(link => unsavedKeys.includes(link.key))
  .map(({ key, label, to }) => ({ key, label, to }))

// At a glance: one row per collection of the settings links, with the number of entries the saved settings hold. A domain
// with one collection is named by its tree entry; with several, each row names the collection after the entry.
const glanceRows = ({ links, schemas, saved }) => links.flatMap(link => {
  const domain = domainOfLink(link)
  if (domain === undefined) return []
  const collections = collectionsOf(schemas[domain])
  const several = collections.length > 1
  return collections.map(collection => ({
    key: several ? `${link.key}:${collection.key}` : link.key,
    label: several ? `${link.label}, ${collection.title}` : link.label,
    count: saved?.[domain]?.[collection.key]?.length ?? 0,
  }))
})

// The keys of the tree entries that a domain with an unsaved draft marks. The Repositories page edits the maven domain, so its
// draft marks that page, and the other domains mark their own settings entry.
const unsavedTreeKeys = (domains) => domains.map(domain => (domain === 'maven' ? 'page:repositories' : `settings:${domain}`))

export { attentionRows, glanceRows, unsavedTreeKeys }
