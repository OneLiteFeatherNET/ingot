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
 * The administrator's checklist on the dashboard. Each step is decided from the saved state of the
 * server, never from what is on screen: a repository that is only drafted in the settings does not
 * count. The rules here know nothing about Vue, so the card only draws what they return.
 */

// The repositories a fresh Ingot creates (MavenSettings on the server). They do not count as set up.
export const DEFAULT_REPOSITORY_IDS = ['releases', 'snapshots', 'private']
// The frontend defaults (FrontendSettings on the server). A title or logo still at its default is not set.
export const DEFAULT_TITLE = 'Ingot Repository'
export const DEFAULT_LOGO = 'https://avatars.githubusercontent.com/u/103827826'

const DISMISSED_KEY = 'ingot.gettingStarted.dismissed'

const isCustomRepository = (repository) => !DEFAULT_REPOSITORY_IDS.includes(repository.id)

const logoWithoutQuery = (url) => String(url ?? '').trim().split('?')[0]

const isCustomised = (value, fallback) => {
  const text = String(value ?? '').trim()
  return text !== '' && text !== fallback
}

const isCustomLogo = (url) => {
  const text = String(url ?? '').trim()
  return text !== '' && logoWithoutQuery(text) !== DEFAULT_LOGO
}

const hasWriteRoute = (token) => (token.routes ?? []).some(route => route.permission?.identifier === 'route:write')

/*
 * The four steps, each with what it asks the admin to do next. A null input means that its data has
 * not arrived yet, which is never shown as done. `repositories` is the saved maven list, `tokens` the
 * token list and `frontend` the saved frontend settings.
 */
export const checklistItems = ({ repositories, tokens, frontend }) => [
  {
    key: 'repository',
    title: 'Create your first repository',
    description: 'Repositories hold the artifacts. The guided setup creates one with its access in a single pass.',
    done: repositories !== null && repositories.some(isCustomRepository),
    action: {
      label: 'Create a repository',
      to: { name: 'settings', params: { domain: 'maven' }, query: { new: 'repository' } }
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
    description: 'Public repositories are open to everyone. Check that each one should be.',
    done: repositories !== null && repositories.some(repository => repository.visibility === 'PUBLIC' && isCustomRepository(repository)),
    action: {
      label: 'Open repositories',
      to: { name: 'settings', params: { domain: 'maven' } }
    }
  }
]

export const checklistProgress = (items) => ({
  done: items.filter(item => item.done).length,
  total: items.length
})

// Storage can be missing or throw (private windows, blocked site data). Then the checklist shows,
// and a dismissal lasts only as long as this page.
const browserStorage = () => {
  try {
    return globalThis.localStorage ?? null
  } catch {
    return null
  }
}

export const isChecklistDismissed = (storage = browserStorage()) => {
  try {
    return storage?.getItem(DISMISSED_KEY) === '1'
  } catch {
    return false
  }
}

export const dismissChecklist = (storage = browserStorage()) => {
  try {
    storage?.setItem(DISMISSED_KEY, '1')
  } catch {
    // Nothing else depends on the flag, so a failed write only means the checklist shows again.
  }
}
