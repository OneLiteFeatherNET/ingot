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

import { describe, it, expect } from 'vitest'
import { matchEntries } from './adminFilter'

// The tree is built per test by a function, so no test can change what another one reads. The labels, descriptions and
// keywords are the ones of the Administration tree in registry.js.
const link = (key, label, description, keywords, extra = {}) => ({ key, label, description, keywords, to: { name: key }, ...extra })

const tree = () => [
  {
    id: 'overview',
    label: 'Overview',
    heading: undefined,
    links: [link('page:overview', 'Overview', 'Shared configuration for all instances.', 'settings, configuration, import, export')],
  },
  {
    id: 'Repository',
    heading: 'Artifacts',
    links: [
      link('page:repositories', 'Repositories', 'Hosted, proxied and group repositories, their policies and Maven options.', 'maven, hosted, proxy, mirror, group'),
      link('settings:cleanup', 'Cleanup policies', 'Rules that delete old versions from the repositories they list.', 'cleanup, retention, delete, snapshots'),
      link('settings:search', 'Search index', 'The index behind artifact search: its nightly check against storage and scheduled rebuilds.', 'search, index, rebuild, reindex, verify'),
    ],
  },
  {
    id: 'Security',
    heading: 'Access',
    links: [
      link('page:users', 'Users', 'People who sign in, with their permissions and tokens.', 'people, account, password, token'),
      link('page:sso', 'Single sign-on', 'External sign-in providers such as OpenID Connect.', 'sso, oidc, oauth, openid, login'),
      link('plugin:acme-mirror/mirrors', 'Mirror pages', 'Upstream mirror status.', '', { plugin: 'acme-mirror' }),
    ],
  },
  {
    id: 'Support',
    heading: 'Monitoring and support',
    links: [
      link('page:audit', 'Audit log', 'Deployments, deletions, settings changes, token changes and failed sign ins on this instance, newest first.', 'audit, events, history, who'),
      link('page:maintenance', 'Maintenance', 'Storage usage, the search index and cleanup for this Ingot instance.', 'storage, rebuild index, cleanup run'),
      link('settings:audit', 'Audit settings', 'How long audit events are kept, and whether the client address is recorded.', 'audit, retention, client address'),
    ],
  },
  {
    id: 'System',
    heading: 'Instance',
    links: [
      link('settings:frontend', 'Branding', 'Name, description, logo, colours and links shown on this instance.', 'frontend, logo, colour, color, theme, title, privacy'),
      link('settings:web', 'Reverse proxy', 'The headers that carry the client address and the path prefix behind a proxy.', 'web, nginx, traefik, x-forwarded, header'),
    ],
  },
]

const labelsOf = (groups) => groups.flatMap(group => group.links.map(item => item.label))
const keysOf = (groups) => groups.flatMap(group => group.links.map(item => item.key))

describe('matchEntries finds a page by the words an operator has in mind', () => {
  it('finds Reverse proxy by its old label, web, which is a keyword', () => {
    expect(labelsOf(matchEntries(tree(), 'web').groups)).toEqual(['Reverse proxy'])
  })

  it('finds Reverse proxy by the name of the software that runs in front of it', () => {
    expect(labelsOf(matchEntries(tree(), 'nginx').groups)).toEqual(['Reverse proxy'])
  })

  it('finds both homes of rebuild index, Search index and Maintenance, in tree order', () => {
    expect(labelsOf(matchEntries(tree(), 'rebuild index').groups)).toEqual(['Search index', 'Maintenance'])
  })

  it('finds Branding by its keyword logo', () => {
    expect(labelsOf(matchEntries(tree(), 'logo').groups)).toEqual(['Branding'])
  })

  it('finds the retention pages, Cleanup policies and Audit settings, in tree order', () => {
    expect(labelsOf(matchEntries(tree(), 'retention').groups)).toEqual(['Cleanup policies', 'Audit settings'])
  })

  it('matches a word by its start only, so ret finds retention', () => {
    expect(labelsOf(matchEntries(tree(), 'ret').groups)).toEqual(['Cleanup policies', 'Audit settings'])
  })

  it('ignores case, so NGINX finds Reverse proxy', () => {
    expect(labelsOf(matchEntries(tree(), 'NGINX').groups)).toEqual(['Reverse proxy'])
  })

  it('finds a plugin page by its plugin id, when its title and description do not hold the word', () => {
    const groups = matchEntries(tree(), 'acme').groups

    expect(groups.map(group => group.heading), 'the plugin page is listed under its group').toEqual(['Access'])
    expect(labelsOf(groups)).toEqual(['Mirror pages'])
  })

  it('finds every entry of a group by the group heading', () => {
    expect(labelsOf(matchEntries(tree(), 'monitoring').groups)).toEqual(['Audit log', 'Maintenance', 'Audit settings'])
  })

  it('keeps an entry only when every typed word matches it', () => {
    expect(labelsOf(matchEntries(tree(), 'rebuild nginx').groups), 'no entry has both words').toEqual([])
  })

  it('finds nothing for text that no entry holds', () => {
    const result = matchEntries(tree(), 'xyz')

    expect(result.groups, 'no group is left').toEqual([])
    expect(result.count, 'no match is counted').toBe(0)
  })
})

describe('matchEntries keeps the tree in shape', () => {
  it('drops the groups that have no kept entry', () => {
    expect(matchEntries(tree(), 'nginx').groups.map(group => group.heading)).toEqual(['Instance'])
  })

  it('keeps the tree order, with Overview first when it is kept as the current entry', () => {
    const groups = matchEntries(tree(), 'nginx', 'page:overview').groups

    expect(groups.map(group => group.id), 'Overview, then the group of the match').toEqual(['overview', 'System'])
  })

  it('keeps the current entry visible even when the text does not match it', () => {
    const groups = matchEntries(tree(), 'nginx', 'page:users').groups

    expect(keysOf(groups), 'the current Users entry, then the match').toEqual(['page:users', 'settings:web'])
  })

  it('counts the matches, and not a current entry that only stays because it is current', () => {
    expect(matchEntries(tree(), 'nginx', 'page:users').count).toBe(1)
  })

  it('counts a current entry when it matches the text itself', () => {
    expect(matchEntries(tree(), 'people', 'page:users').count).toBe(1)
  })

  it('counts two matches for a word that two entries hold', () => {
    expect(matchEntries(tree(), 'retention').count).toBe(2)
  })

  it('returns the tree unchanged for a blank text, and counts nothing', () => {
    const result = matchEntries(tree(), '   ')

    expect(result.groups, 'every entry is listed again').toEqual(tree())
    expect(result.count).toBe(0)
  })

  it('does not change the groups it is given', () => {
    const groups = tree()

    matchEntries(groups, 'nginx', 'page:users')

    expect(groups, 'the input is the tree it was').toEqual(tree())
  })
})
