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

import type { RouteLocationRaw } from 'vue-router'
import type { Token } from '../../store/tokens'
import type { components } from '../../types/api'

type RepositoryVisibility = components['schemas']['RepositoryVisibility']

// A repository of the saved maven settings. The settings are not part of the OpenAPI description, so only
// the fields this checklist reads are named, and visibility is an optional extra of that shape.
export interface MavenRepository {
  id: string
  visibility?: RepositoryVisibility
}

// The saved frontend settings, of which the checklist reads the title and the logo.
export interface FrontendSettings {
  title?: string
  organizationLogo?: string
}

export interface ChecklistAction {
  label: string
  to?: RouteLocationRaw
  dialog?: 'public-repositories'
}

export interface ChecklistItem {
  key: string
  title: string
  description: string
  done: boolean
  action: ChecklistAction
}

export interface ChecklistInput {
  repositories: MavenRepository[] | null
  tokens: Token[] | null
  frontend: FrontendSettings | null
  acknowledgedPublic?: string[] | null
}

/*
 * The administrator's checklist on the dashboard. Each step is decided from the saved state of the
 * server, never from what is on screen: a repository that is only drafted in the settings does not
 * count. The rules here know nothing about Vue, so the card only draws what they return.
 */

// The repositories a fresh Ingot creates (MavenSettings on the server). They do not count as set up.
export const DEFAULT_REPOSITORY_IDS: string[] = ['releases', 'snapshots', 'private']
// The frontend defaults (FrontendSettings on the server). A title or logo still at its default is not set.
export const DEFAULT_TITLE = 'Ingot Repository'
export const DEFAULT_LOGO = 'https://avatars.githubusercontent.com/u/103827826'

const DISMISSED_KEY = 'ingot.gettingStarted.dismissed'
// The public repository ids an admin confirmed, per browser. See `isPublicReviewDone`.
export const PUBLIC_REVIEW_KEY = 'ingot.checklist.publicReview'

const isCustomRepository = (repository: MavenRepository): boolean => !DEFAULT_REPOSITORY_IDS.includes(repository.id)

const logoWithoutQuery = (url: string | null | undefined): string => String(url ?? '').trim().split('?')[0]

const isCustomised = (value: string | null | undefined, fallback: string): boolean => {
  const text = String(value ?? '').trim()
  return text !== '' && text !== fallback
}

const isCustomLogo = (url: string | null | undefined): boolean => {
  const text = String(url ?? '').trim()
  return text !== '' && logoWithoutQuery(text) !== DEFAULT_LOGO
}

// The public repositories, sorted by id so the review lists them the same way every time.
export const publicRepositories = (repositories: MavenRepository[] | null | undefined): MavenRepository[] => (repositories ?? [])
  .filter(repository => repository.visibility === 'PUBLIC')
  .sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0))

export const publicRepositoryIds = (repositories: MavenRepository[] | null | undefined): string[] =>
  publicRepositories(repositories).map(repository => repository.id)

/*
 * Done when nothing is public. Otherwise done only when the admin confirmed exactly the current public
 * set: a repository that became public, or a new public one, brings the step back. `acknowledged` is the
 * stored id list, or null when nothing was stored.
 */
export const isPublicReviewDone = (repositories: MavenRepository[] | null | undefined, acknowledged: string[] | null | undefined): boolean => {
  const publicIds = publicRepositoryIds(repositories)
  if (publicIds.length === 0) return true
  if (!Array.isArray(acknowledged) || acknowledged.length !== publicIds.length) return false
  const confirmed = [...acknowledged].sort()
  return publicIds.every((id, index) => id === confirmed[index])
}

const hasWriteRoute = (token: Token): boolean => (token.routes ?? []).some(route => route.permission?.identifier === 'route:write')

/*
 * The four steps, each with what it asks the admin to do next. A null input means that its data has
 * not arrived yet, which is never shown as done. `repositories` is the saved maven list, `tokens` the
 * token list, `frontend` the saved frontend settings and `acknowledgedPublic` the confirmed public ids.
 * An action either routes to a place (`to`) or opens a dialog by name (`dialog`).
 */
export const checklistItems = ({ repositories, tokens, frontend, acknowledgedPublic = null }: ChecklistInput): ChecklistItem[] => [
  {
    key: 'repository',
    title: 'Create your first repository',
    description: 'Repositories hold the artifacts. The guided setup creates one with its access in a single pass.',
    done: repositories !== null && repositories.some(isCustomRepository),
    action: {
      label: 'Create a repository',
      to: { name: 'settings-repositories', query: { new: 'repository' } }
    }
  },
  {
    key: 'deploy-token',
    title: 'Create a deploy token with write access',
    description: 'Builds and CI jobs publish with a token that can write to a repository.',
    done: tokens !== null && tokens.some(hasWriteRoute),
    action: {
      label: 'Create a token',
      to: { name: 'token', params: { name: 'new' } }
    }
  },
  {
    key: 'branding',
    title: 'Set an organization name and logo',
    description: 'Visitors see the name and logo on the front page and in the footer.',
    done: frontend !== null && isCustomised(frontend.title, DEFAULT_TITLE) && isCustomLogo(frontend.organizationLogo),
    action: {
      label: 'Open frontend settings',
      to: { name: 'settings', params: { domain: 'frontend' } }
    }
  },
  {
    key: 'public-review',
    title: 'Review public repositories',
    description: 'Public repositories are open to everyone. Confirm that each one should be.',
    done: repositories !== null && isPublicReviewDone(repositories, acknowledgedPublic),
    action: {
      label: 'Review',
      dialog: 'public-repositories'
    }
  }
]

export const checklistProgress = (items: ChecklistItem[]): { done: number, total: number } => ({
  done: items.filter(item => item.done).length,
  total: items.length
})

// Storage can be missing or throw (private windows, blocked site data). Then the checklist shows,
// and a dismissal lasts only as long as this page.
const browserStorage = (): Storage | null => {
  try {
    return globalThis.localStorage ?? null
  } catch {
    return null
  }
}

export const isChecklistDismissed = (storage: Storage | null = browserStorage()): boolean => {
  try {
    return storage?.getItem(DISMISSED_KEY) === '1'
  } catch {
    return false
  }
}

export const dismissChecklist = (storage: Storage | null = browserStorage()): void => {
  try {
    storage?.setItem(DISMISSED_KEY, '1')
  } catch {
    // Nothing else depends on the flag, so a failed write only means the checklist shows again.
  }
}

const isStringList = (value: unknown): value is string[] =>
  Array.isArray(value) && value.every(id => typeof id === 'string')

// The confirmed set is read and written per browser. A missing or broken store reads as nothing confirmed,
// so the step stays to do and the admin is asked again instead of being told it is done.
export const readAcknowledgedPublicIds = (storage: Storage | null = browserStorage()): string[] | null => {
  try {
    const value: unknown = JSON.parse(storage?.getItem(PUBLIC_REVIEW_KEY) ?? 'null')
    return isStringList(value) ? value : null
  } catch {
    return null
  }
}

export const saveAcknowledgedPublicIds = (ids: readonly string[], storage: Storage | null = browserStorage()): void => {
  try {
    storage?.setItem(PUBLIC_REVIEW_KEY, JSON.stringify([...ids].sort()))
  } catch {
    // Without the stored set the step shows as to do again, which is the safe direction.
  }
}
