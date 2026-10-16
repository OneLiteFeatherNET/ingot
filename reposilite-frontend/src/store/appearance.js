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
 * The operator's appearance settings, validated once when the frontend settings arrive.
 *
 * loadFrontendSettings (placeholders.js) hands the `appearance` block here before the app
 * mounts. Until then, and whenever the server does not send one, the defaults hold: no seed,
 * so the static OneLiteFeather theme is used unchanged.
 */

import { normalizeAppearance, applyFavicon, devSeedFromSearch, removeTheme } from '../theme/settings'

let current = normalizeAppearance({})

/* Development only: a ?theme-seed query parameter overrides the seed for previewing. */
const withDevelopmentSeed = (raw) => {
  if (!import.meta.env.DEV) {
    return raw
  }
  const seed = devSeedFromSearch(location.search)
  return seed ? { ...raw, seedColor: seed } : raw
}

export function setAppearance(raw) {
  current = normalizeAppearance(withDevelopmentSeed(raw))
}

export function useAppearance() {
  return current
}

/*
 * Applies the favicon, and the theme when a seed is set. Without a seed any generated theme is
 * taken out again, so clearing the seed in the settings takes effect without a reload. The colour
 * library is imported only here, so a default install never downloads it. A failed import keeps
 * the default theme and does not stop the app from mounting.
 */
export async function applyAppearance(doc = document) {
  applyFavicon(current.favicon, doc)
  if (!current.seedColor) {
    removeTheme(doc)
    return
  }
  try {
    const { applyTheme } = await import('../theme/runtime')
    applyTheme(current, doc)
  } catch (error) {
    console.error('Could not apply the operator theme, keeping the default:', error)
  }
}
