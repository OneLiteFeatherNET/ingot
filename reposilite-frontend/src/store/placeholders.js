/*
 * Copyright (c) 2023 dzikoysk
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
 * The dashboard gets its settings from the server in both deployments.
 *
 * Served by the server itself, the placeholders are substituted into the bundle before it
 * reaches the browser, so the base path is known at once. The rest of the settings (title,
 * appearance) still come from the settings endpoint, so the fetch always runs.
 *
 * Served by anything else, a container of its own for instance, the placeholders arrive
 * untouched. The base path is then the one the build was made for, and the settings are
 * fetched from under that mount point.
 *
 * loadFrontendSettings has to finish before the app mounts, because usePlaceholders is read
 * synchronously, right down to module scope in the API client. The wait is bounded by a
 * timeout: when the settings do not arrive in time, or fail, the built-in defaults hold.
 *
 * Note that only the arguments to read() may spell a placeholder out. Anything else that
 * did would be rewritten by the substitution as well, including the table of fallbacks.
 */

import { setAppearance } from './appearance'

/* global __INGOT_FAKE_API_PORT__ */
// Development only: the fake backend, whose port vite.config.js defines. The typeof guard keeps
// the test runner, which does not apply that define, on the default port.
const DEV_API_PORT = typeof __INGOT_FAKE_API_PORT__ === 'undefined' ? '8887' : __INGOT_FAKE_API_PORT__
const DEV_API_ORIGIN = `http://localhost:${DEV_API_PORT}`

const FALLBACKS = {
  'REPOSILITE.BASE_PATH': '/',
  'REPOSILITE.ID': 'ingot-repository',
  'REPOSILITE.TITLE': 'Ingot Repository',
  'REPOSILITE.DESCRIPTION': 'Public Maven repository hosted through Ingot',
  'REPOSILITE.ORGANIZATION_WEBSITE': '',
  'REPOSILITE.ORGANIZATION_LOGO': 'https://avatars.githubusercontent.com/u/103827826?s=200&v=4',
  'REPOSILITE.PRIVACY_POLICY': '',
  'REPOSILITE.ICP_LICENSE': '',
  'REPOSILITE.JAVADOC_SUFFIXES': '-javadoc.jar,-groovydoc.jar'
}

// The settings field behind each token. The typed fields are read first; the placeholder map
// the server sends alongside them is only a fallback for an older server.
const TYPED_FIELDS = {
  'REPOSILITE.ID': 'id',
  'REPOSILITE.TITLE': 'title',
  'REPOSILITE.DESCRIPTION': 'description',
  'REPOSILITE.ORGANIZATION_WEBSITE': 'organizationWebsite',
  'REPOSILITE.ORGANIZATION_LOGO': 'organizationLogo',
  'REPOSILITE.PRIVACY_POLICY': 'privacyPolicy',
  'REPOSILITE.ICP_LICENSE': 'icpLicense'
}

const SETTINGS_TIMEOUT_MS = 2000

// The settings as the server last sent them, or null when they could not be had.
let fetched = null

/* A placeholder that still names its own token was never substituted. */
const substituted = (value, token) => !value.includes(token)

/*
 * The mount point of the dashboard. A bundled dashboard has the server's base path where the
 * placeholder was, so the literal itself is the value. A detached dashboard has no substitution
 * and takes the base path its build was made for, which Vite keeps as BASE_URL. The bundled Vite
 * base is relative (the server strips its slashes), so it is not used there.
 */
export const resolveMountPath = (
  bundledValue = '{{REPOSILITE.BASE_PATH}}',
  viteBase = import.meta.env.BASE_URL
) => (substituted(bundledValue, 'REPOSILITE.BASE_PATH') ? bundledValue : viteBase)

const read = (value, token) => {
  if (substituted(value, token)) {
    return value
  }
  const field = TYPED_FIELDS[token]
  if (fetched && field && fetched[field] !== undefined && fetched[field] !== null) {
    return fetched[field]
  }
  const key = `{{${token}}}`
  if (fetched?.placeholders?.[key] !== undefined) {
    return fetched.placeholders[key]
  }
  return FALLBACKS[token]
}

/*
 * The settings endpoint below the mount point. In production it is a path under the mount point,
 * never under the current address, which may be a deep link of the client router.
 */
export const settingsUrlFor = ({ mountPath, origin, production }) => {
  if (!production) {
    return `${DEV_API_ORIGIN}/api/frontend/settings`
  }
  const base = mountPath.endsWith('/') ? mountPath : `${mountPath}/`
  return new URL(`${base}api/frontend/settings`, origin).href
}

/*
 * Fetches the settings and applies their appearance, bundled or detached. A failure or a timeout
 * keeps the defaults. It is reported in the console only, so an anonymous visitor never sees an
 * error.
 */
export async function loadFrontendSettings({
  mountPath = resolveMountPath(),
  timeoutMs = SETTINGS_TIMEOUT_MS
} = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)

  try {
    const url = settingsUrlFor({
      mountPath,
      origin: location.origin,
      production: process.env.NODE_ENV === 'production'
    })
    const response = await fetch(url, { headers: { Accept: 'application/json' }, signal: controller.signal })
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const settings = await response.json()
    fetched = settings
    // An older server sends no appearance block, which means the default theme.
    setAppearance(settings.appearance)
  } catch (error) {
    // Rendering with fallbacks beats not rendering: the dashboard still works, it just
    // shows generic branding. Say why rather than failing silently.
    console.error('Could not load the frontend settings, falling back to defaults:', error)
    fetched = null
    setAppearance(undefined)
  } finally {
    clearTimeout(timer)
  }
}

export default function usePlaceholders() {
  const basePath = read('{{REPOSILITE.BASE_PATH}}', 'REPOSILITE.BASE_PATH')
  const id = read('{{REPOSILITE.ID}}', 'REPOSILITE.ID')
  const title = read('{{REPOSILITE.TITLE}}', 'REPOSILITE.TITLE')
  const description = read('{{REPOSILITE.DESCRIPTION}}', 'REPOSILITE.DESCRIPTION')
  const organizationLogo = read('{{REPOSILITE.ORGANIZATION_LOGO}}', 'REPOSILITE.ORGANIZATION_LOGO')
  const privacyPolicy = read('{{REPOSILITE.PRIVACY_POLICY}}', 'REPOSILITE.PRIVACY_POLICY')
  const icpLicense = read('{{REPOSILITE.ICP_LICENSE}}', 'REPOSILITE.ICP_LICENSE')
  const javadocSuffixes = read('{{REPOSILITE.JAVADOC_SUFFIXES}}', 'REPOSILITE.JAVADOC_SUFFIXES')
    .split(',')
    .filter(suffix => suffix.length > 0)

  const organizationWebsite =
    read('{{REPOSILITE.ORGANIZATION_WEBSITE}}', 'REPOSILITE.ORGANIZATION_WEBSITE') ||
    location.protocol + '//' + location.host + basePath

  const productionUrl =
    window.location.protocol + '//' + location.host + basePath

  const baseUrl =
    process.env.NODE_ENV === 'production'
      ? (productionUrl.endsWith('/') ? productionUrl.slice(0, -1) : productionUrl)
      : DEV_API_ORIGIN

  return {
    basePath,
    id,
    title,
    description,
    organizationWebsite,
    organizationLogo,
    privacyPolicy,
    icpLicense,
    javadocSuffixes,
    productionUrl,
    baseUrl
  }
}
