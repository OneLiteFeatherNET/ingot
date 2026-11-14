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
 * The icon of each sign in provider kind, as the backend names it (the icon enum of
 * GET /api/auth/providers). Brand marks come from simple-icons (CC0). Each one is a named export,
 * so the bundler keeps only the marks that are imported here.
 *
 * Microsoft has no mark in the installed simple-icons set, and a hand-drawn stand-in would imitate
 * the logo, so it uses the generic key like every value the set does not know.
 */
import {
  siAuth0,
  siAuthentik,
  siGitea,
  siGithub,
  siGitlab,
  siGoogle,
  siKeycloak,
  siOkta
} from 'simple-icons'

// Material Symbols "key", outlined (Apache 2.0). The same glyph as the token pages use.
const GENERIC_KEY_PATH = 'M280-412q-28 0-48-20t-20-48q0-28 20-48t48-20q28 0 48 20t20 48q0 28-20 48t-48 20Zm0 172q-100 0-170-70T40-480q0-100 70-170t170-70q72 0 126 34t85 103h356l113 113-167 153-88-64-88 64-75-60h-51q-25 60-78.5 98.5T280-240Zm0-60q58 0 107-38.5t63-98.5h114l54 45 88-63 82 62 85-79-51-51H450q-12-56-60-96.5T280-660q-75 0-127.5 52.5T100-480q0 75 52.5 127.5T280-300Z'

const BRAND_MARKS = {
  GITHUB: siGithub,
  GITLAB: siGitlab,
  GOOGLE: siGoogle,
  KEYCLOAK: siKeycloak,
  AUTHENTIK: siAuthentik,
  OKTA: siOkta,
  AUTH0: siAuth0,
  GITEA: siGitea
}

const PROVIDER_ICONS = ['GENERIC', 'GITHUB', 'GITLAB', 'GOOGLE', 'MICROSOFT', 'KEYCLOAK', 'AUTHENTIK', 'OKTA', 'AUTH0', 'GITEA']

// The simple-icons path of a brand, or null when the provider has no brand mark.
const brandPathOf = (icon) => BRAND_MARKS[icon]?.path ?? null

export { GENERIC_KEY_PATH, PROVIDER_ICONS, brandPathOf }
