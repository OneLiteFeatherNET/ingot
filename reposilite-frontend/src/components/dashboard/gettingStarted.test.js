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
import {
  checklistItems,
  checklistProgress,
  isChecklistDismissed,
  dismissChecklist,
  DEFAULT_REPOSITORY_IDS,
  DEFAULT_TITLE,
  DEFAULT_LOGO
} from './gettingStarted'

const DEFAULT_REPOS = [
  { id: 'releases', visibility: 'PUBLIC' },
  { id: 'snapshots', visibility: 'PUBLIC' },
  { id: 'private', visibility: 'PRIVATE' }
]

const CUSTOMISED = { title: 'Example Network', organizationLogo: 'https://example.net/logo.png' }
const STOCK = { title: DEFAULT_TITLE, organizationLogo: DEFAULT_LOGO }

const token = (name, identifiers) => ({
  name,
  routes: identifiers.map(identifier => ({ path: '/releases', permission: { identifier } }))
})

const itemsFor = (overrides = {}) => checklistItems({
  repositories: DEFAULT_REPOS,
  tokens: [],
  frontend: STOCK,
  ...overrides
})

const doneOf = (items, key) => items.find(item => item.key === key).done

describe('getting started checklist', () => {
  it('lists the four steps of a first setup in order', () => {
    expect(itemsFor().map(item => item.key)).toEqual(['repository', 'deploy-token', 'branding', 'public-review'])
  })

  it('treats the three default repositories as no repository at all', () => {
    expect(DEFAULT_REPOSITORY_IDS).toEqual(['releases', 'snapshots', 'private'])
    expect(doneOf(itemsFor({ repositories: DEFAULT_REPOS }), 'repository')).toBe(false)
  })

  it('marks the repository step done once a repository outside the defaults exists', () => {
    const repositories = [...DEFAULT_REPOS, { id: 'team', visibility: 'PRIVATE' }]
    expect(doneOf(itemsFor({ repositories }), 'repository')).toBe(true)
  })

  it('does not treat an empty repository list as done', () => {
    expect(doneOf(itemsFor({ repositories: [] }), 'repository')).toBe(false)
  })

  it('marks the token step done only when a token has a write route', () => {
    const readOnly = [token('reader', ['route:read'])]
    expect(doneOf(itemsFor({ tokens: readOnly }), 'deploy-token')).toBe(false)

    const deployer = [token('reader', ['route:read']), token('ci', ['route:read', 'route:write'])]
    expect(doneOf(itemsFor({ tokens: deployer }), 'deploy-token')).toBe(true)
  })

  it('keeps the branding step open while the title or the logo is still the default', () => {
    expect(doneOf(itemsFor({ frontend: { ...STOCK, title: 'Example Network' } }), 'branding')).toBe(false)
    expect(doneOf(itemsFor({ frontend: { ...STOCK, organizationLogo: 'https://example.net/logo.png' } }), 'branding')).toBe(false)
    expect(doneOf(itemsFor({ frontend: CUSTOMISED }), 'branding')).toBe(true)
  })

  it('treats the default logo with another query string as the default', () => {
    const frontend = { ...CUSTOMISED, organizationLogo: `${DEFAULT_LOGO}?s=200&v=4` }
    expect(doneOf(itemsFor({ frontend }), 'branding')).toBe(false)
  })

  it('treats an empty title as not set', () => {
    expect(doneOf(itemsFor({ frontend: { ...CUSTOMISED, title: '  ' } }), 'branding')).toBe(false)
  })

  it('marks the public review step done when a public repository outside the defaults exists', () => {
    expect(doneOf(itemsFor(), 'public-review')).toBe(false)
    const repositories = [...DEFAULT_REPOS, { id: 'mirror', visibility: 'PUBLIC' }]
    expect(doneOf(itemsFor({ repositories }), 'public-review')).toBe(true)
  })

  it('reports nothing as done while the settings and tokens have not loaded', () => {
    const items = checklistItems({ repositories: null, tokens: null, frontend: null })
    expect(items.map(item => item.done)).toEqual([false, false, false, false])
  })

  it('links each step to the place that completes it', () => {
    const byKey = Object.fromEntries(itemsFor().map(item => [item.key, item.action.to]))
    expect(byKey.repository).toEqual({ name: 'settings', params: { domain: 'maven' }, query: { new: 'repository' } })
    expect(byKey['deploy-token']).toEqual({ name: 'token', params: { name: 'new' } })
    expect(byKey.branding).toEqual({ name: 'settings', params: { domain: 'frontend' } })
    expect(byKey['public-review']).toEqual({ name: 'settings', params: { domain: 'maven' } })
  })

  it('counts the done steps for the progress line', () => {
    const items = itemsFor({ repositories: [...DEFAULT_REPOS, { id: 'team', visibility: 'PRIVATE' }], frontend: CUSTOMISED })
    expect(checklistProgress(items)).toEqual({ done: 2, total: 4 })
  })
})

// A storage that behaves like localStorage, with a switch to make every access throw, as a locked-down browser does.
const memoryStorage = ({ broken = false } = {}) => {
  const values = new Map()
  const guard = () => { if (broken) throw new Error('storage is disabled') }
  return {
    getItem: (key) => { guard(); return values.has(key) ? values.get(key) : null },
    setItem: (key, value) => { guard(); values.set(key, String(value)) }
  }
}

describe('checklist dismissal', () => {
  it('is not dismissed until the admin dismisses it', () => {
    const storage = memoryStorage()
    expect(isChecklistDismissed(storage)).toBe(false)
    dismissChecklist(storage)
    expect(isChecklistDismissed(storage)).toBe(true)
  })

  it('shows the checklist when storage cannot be read, instead of hiding it for ever', () => {
    expect(isChecklistDismissed(memoryStorage({ broken: true }))).toBe(false)
  })

  it('does not throw when storage cannot be written', () => {
    expect(() => dismissChecklist(memoryStorage({ broken: true }))).not.toThrow()
  })
})
