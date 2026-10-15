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
 * Tags and search for the repositories. Nothing here touches Vue, the router or the network, so
 * the rules that the settings list, the chip editor and the overview share can be tested directly.
 *
 * The matching mirrors GET /api/maven/repositories: a text query matches the id, the description
 * and the tags, without regard to case, and every tag of the criteria must be on the repository.
 * The overview filters the list it has already loaded with these functions, so a keystroke never
 * waits for a request.
 */

export const TAG_PATTERN = /^[a-z0-9][a-z0-9-]{0,31}$/
export const MAX_TAGS = 10

const TAG_HELP = 'Use lower case letters, digits and hyphens, starting with a letter or digit, up to 32 characters.'

/* The message for a tag that may be added to `existing`, or an empty string when it may. */
export const validateTag = (tag, existing = []) => {
  if (tag === '') return 'Enter a tag.'
  if (!TAG_PATTERN.test(tag)) return TAG_HELP
  if (existing.length >= MAX_TAGS) return `A repository takes at most ${MAX_TAGS} tags.`
  if (existing.includes(tag)) return 'This tag is already added.'
  return ''
}

/*
 * A search box reads `tag:name` tokens as tags, and every other word as text. Tags are compared
 * in lower case, as they are stored. A `tag:` without a name is ignored rather than matching nothing.
 */
export const parseSearch = (query = '') => {
  const words = String(query).trim().split(/\s+/).filter(word => word !== '')
  const tags = []
  const text = []
  for (const word of words) {
    const tag = /^tag:(.*)$/i.exec(word)
    if (!tag) {
      text.push(word)
    } else if (tag[1] !== '') {
      tags.push(tag[1].toLowerCase())
    }
  }
  return { text: text.join(' '), tags }
}

/* Whether one repository (or entry) matches the criteria: the text in id, description or tags, and every tag. */
export const matchesSearch = (item, { text = '', tags = [] } = {}) => {
  const needle = text.trim().toLowerCase()
  const itemTags = Array.isArray(item?.tags) ? item.tags.map(tag => String(tag).toLowerCase()) : []
  const fields = [item?.id, item?.description, ...itemTags]
    .map(field => String(field ?? '').toLowerCase())
  const textMatches = needle === '' || fields.some(field => field.includes(needle))
  const tagsMatch = tags.every(tag => itemTags.includes(tag.toLowerCase()))
  return textMatches && tagsMatch
}

const byId = (left, right) => (left.id < right.id ? -1 : left.id > right.id ? 1 : 0)

/* The repositories that match, sorted by id. The list it is given is left as it is. */
export const filterRepositories = (repositories, criteria = {}) =>
  repositories
    .filter(repository => matchesSearch(repository, criteria))
    .sort(byId)

/* Every tag with the number of repositories that carry it, the most used first and then by name. */
export const tagUsage = (repositories) => {
  const counts = new Map()
  for (const repository of repositories) {
    for (const tag of new Set(repository.tags ?? [])) {
      counts.set(tag, (counts.get(tag) ?? 0) + 1)
    }
  }
  return [...counts]
    .map(([tag, count]) => ({ tag, count }))
    .sort((left, right) => right.count - left.count || (left.tag < right.tag ? -1 : left.tag > right.tag ? 1 : 0))
}

/* Every tag used by at least one of the repositories, sorted by name. */
export const tagsInUse = (repositories) =>
  [...new Set(repositories.flatMap(repository => repository.tags ?? []))].sort()
