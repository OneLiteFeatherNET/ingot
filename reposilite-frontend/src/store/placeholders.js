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
 * The dashboard gets its settings one of two ways.
 *
 * Served by the server itself, the placeholders below are substituted into this file before
 * it reaches the browser, and there is nothing to fetch.
 *
 * Served by anything else, a container of its own for instance, the placeholders arrive
 * untouched and the settings are fetched from the server instead. loadFrontendSettings has
 * to finish before the app mounts, because usePlaceholders is read synchronously, right
 * down to module scope in the API client.
 *
 * Note that only the arguments to read() may spell a placeholder out. Anything else that
 * did would be rewritten by the substitution as well, including the table of fallbacks.
 */

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

let fetched = null

/* A placeholder that still names its own token was never substituted. */
const substituted = (value, token) => !value.includes(token)

const read = (value, token) => {
  if (substituted(value, token)) {
    return value
  }
  const key = `{{${token}}}`
  return (fetched && fetched[key] !== undefined) ? fetched[key] : FALLBACKS[token]
}

/*
 * The directory the dashboard is served from. Routing is hash based, so this stays the
 * mount point wherever the user navigates, which makes it the right prefix for reaching a
 * server published alongside the dashboard under the same path.
 */
const currentDirectory = () =>
  location.pathname.endsWith('/')
    ? location.pathname
    : location.pathname.replace(/[^/]*$/, '')

const settingsUrl = () =>
  process.env.NODE_ENV === 'production'
    ? currentDirectory() + 'api/frontend/settings'
    : 'http://localhost:8887/api/frontend/settings'

export async function loadFrontendSettings() {
  // Substituted placeholders mean the server has already answered this.
  if (substituted('{{REPOSILITE.BASE_PATH}}', 'REPOSILITE.BASE_PATH')) {
    return
  }

  try {
    const response = await fetch(settingsUrl(), { headers: { Accept: 'application/json' } })
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    fetched = (await response.json()).placeholders
  } catch (error) {
    // Rendering with fallbacks beats not rendering: the dashboard still works, it just
    // shows generic branding. Say why rather than failing silently.
    console.error('Could not load the frontend settings, falling back to defaults:', error)
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
      : 'http://localhost:8887'

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
