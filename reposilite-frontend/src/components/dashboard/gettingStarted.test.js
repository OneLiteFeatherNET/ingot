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
  DEFAULT_LOGO,
  PUBLIC_REVIEW_KEY,
  isPublicReviewDone,
  publicRepositories,
  publicRepositoryIds,
  readAcknowledgedPublicIds,
  saveAcknowledgedPublicIds
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

  it('keeps the public review step open while a public repository is not yet acknowledged', () => {
    expect(doneOf(itemsFor(), 'public-review')).toBe(false)
    expect(doneOf(itemsFor({ acknowledgedPublic: ['releases', 'snapshots'] }), 'public-review')).toBe(true)
  })

  it('marks the public review step done when no repository is public', () => {
    const repositories = [{ id: 'team', visibility: 'PRIVATE' }]
    expect(doneOf(itemsFor({ repositories }), 'public-review')).toBe(true)
  })

  it('opens the review dialog from the public review step instead of a route', () => {
    const step = itemsFor().find(item => item.key === 'public-review')
    expect(step.action).toEqual({ label: 'Review', dialog: 'public-repositories' })
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
  })

  it('counts the done steps for the progress line', () => {
    const items = itemsFor({ repositories: [...DEFAULT_REPOS, { id: 'team', visibility: 'PRIVATE' }], frontend: CUSTOMISED })
    expect(checklistProgress(items)).toEqual({ done: 2, total: 4 })
  })
})

describe('public repository review', () => {
  const PUBLIC_A = { id: 'mirror', visibility: 'PUBLIC', description: 'Mirror of upstream' }
  const PUBLIC_B = { id: 'alpha', visibility: 'PUBLIC', description: '' }
  const PRIVATE = { id: 'private', visibility: 'PRIVATE' }

  it('lists the public repositories sorted by id, whatever order the server sends them in', () => {
    expect(publicRepositories([PRIVATE, PUBLIC_A, PUBLIC_B]).map(repository => repository.id)).toEqual(['alpha', 'mirror'])
  })

  it('does not change the repository list it was given', () => {
    const repositories = [PUBLIC_A, PUBLIC_B]
    publicRepositories(repositories)
    expect(repositories.map(repository => repository.id)).toEqual(['mirror', 'alpha'])
  })

  it('counts the default repositories when they are public', () => {
    expect(publicRepositoryIds(DEFAULT_REPOS)).toEqual(['releases', 'snapshots'])
  })

  it('is done when nothing is public, whatever was acknowledged before', () => {
    expect(isPublicReviewDone([PRIVATE], null)).toBe(true)
    expect(isPublicReviewDone([PRIVATE], ['mirror'])).toBe(true)
    expect(isPublicReviewDone([], [])).toBe(true)
  })

  it('is to do while a public repository has not been acknowledged', () => {
    expect(isPublicReviewDone([PUBLIC_A], null)).toBe(false)
    expect(isPublicReviewDone([PUBLIC_A], [])).toBe(false)
  })

  it('is done when the acknowledged set equals the current public set, regardless of order', () => {
    expect(isPublicReviewDone([PUBLIC_A, PUBLIC_B], ['mirror', 'alpha'])).toBe(true)
    expect(isPublicReviewDone([PUBLIC_A, PUBLIC_B], ['alpha', 'mirror'])).toBe(true)
  })

  it('returns to to do when a repository becomes public after the acknowledgement', () => {
    const becamePublic = [PUBLIC_A, { id: 'private', visibility: 'PUBLIC' }]
    expect(isPublicReviewDone(becamePublic, ['mirror'])).toBe(false)
  })

  it('returns to to do when a new public repository is added after the acknowledgement', () => {
    expect(isPublicReviewDone([PUBLIC_A, PUBLIC_B], ['mirror'])).toBe(false)
  })

  it('returns to to do when an acknowledged repository is no longer public', () => {
    expect(isPublicReviewDone([PUBLIC_A], ['mirror', 'alpha'])).toBe(false)
  })
})

describe('public repository acknowledgement storage', () => {
  it('uses the agreed localStorage key', () => {
    expect(PUBLIC_REVIEW_KEY).toBe('ingot.checklist.publicReview')
  })

  it('reads nothing when no set was saved', () => {
    expect(readAcknowledgedPublicIds(memoryStorage())).toBeNull()
  })

  it('saves the ids sorted and reads them back', () => {
    const storage = memoryStorage()
    saveAcknowledgedPublicIds(['mirror', 'alpha'], storage)
    expect(readAcknowledgedPublicIds(storage)).toEqual(['alpha', 'mirror'])
  })

  it('ignores a stored value that is not a list of ids', () => {
    const storage = memoryStorage()
    storage.setItem(PUBLIC_REVIEW_KEY, '{"alpha":true}')
    expect(readAcknowledgedPublicIds(storage)).toBeNull()
    storage.setItem(PUBLIC_REVIEW_KEY, 'not json')
    expect(readAcknowledgedPublicIds(storage)).toBeNull()
  })

  it('treats unreadable storage as nothing acknowledged', () => {
    expect(readAcknowledgedPublicIds(memoryStorage({ broken: true }))).toBeNull()
  })

  it('does not throw when the acknowledgement cannot be written', () => {
    expect(() => saveAcknowledgedPublicIds(['alpha'], memoryStorage({ broken: true }))).not.toThrow()
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
