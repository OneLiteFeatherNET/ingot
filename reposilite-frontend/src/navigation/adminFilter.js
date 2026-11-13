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
 * The matching of the find field of the Administration tree. It is a pure function of the tree and the text: it keeps no
 * state and reads no DOM, so SideNavLayout renders its result and the tests give it plain input.
 *
 * A link matches when every typed word is the start of a word of the link: its label, its group heading, its description,
 * its keywords, and for a plugin page the plugin id (its title is the label and its description the description). Case does
 * not count. Groups without a kept link are dropped, and the order of the tree is kept. The current entry is always kept,
 * but it is counted only when it matches the text itself.
 */

// The words of a text: letters and digits, lower case. A missing text has no words.
const wordsOf = (text) => (text ?? '').toLowerCase().split(/[^\p{L}\p{N}]+/u).filter(word => word !== '')

// The typed words: the text split on spaces, in lower case.
const tokensOf = (query) => (query ?? '').trim().toLowerCase().split(/\s+/).filter(token => token !== '')

const haystackOf = (link, heading) => [link.label, heading, link.description, link.keywords, link.plugin].flatMap(wordsOf)

const matches = (tokens, haystack) => tokens.every(token => haystack.some(word => word.startsWith(token)))

/*
 * The groups of the tree that the text keeps: [{ id, heading, label, links }] in the order given, and the number of links that
 * match the text. A blank text keeps every group and counts nothing.
 */
const matchEntries = (groups, query, current) => {
  const tokens = tokensOf(query)
  if (tokens.length === 0) return { groups, count: 0 }

  let count = 0
  const kept = groups
    .map(group => ({
      ...group,
      links: group.links.filter(link => {
        const hit = matches(tokens, haystackOf(link, group.heading))
        if (hit) count += 1
        return hit || link.key === current
      }),
    }))
    .filter(group => group.links.length > 0)

  return { groups: kept, count }
}

export { matchEntries }
